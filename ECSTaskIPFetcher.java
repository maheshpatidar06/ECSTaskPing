import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.List;
import java.util.stream.Collectors;

public class ECSTaskIPFetcher {
    private final EcsClient ecsClient;
    private final Ec2Client ec2Client;
    private final String clusterName;
    private final String serviceName;

    public ECSTaskIPFetcher(String clusterName, String serviceName) {
        this.ecsClient = EcsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.ec2Client = Ec2Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.clusterName = clusterName;
        this.serviceName = serviceName;
    }

    public List<String> getTaskPrivateIps() {
        // Fetch running task ARNs
        List<String> taskArns = ecsClient.listTasks(ListTasksRequest.builder()
                .cluster(clusterName)
                .serviceName(serviceName)
                .desiredStatus("RUNNING")
                .build()).taskArns();

        if (taskArns.isEmpty()) return List.of();

        // Describe task details
        List<Task> tasks = ecsClient.describeTasks(DescribeTasksRequest.builder()
                .cluster(clusterName)
                .tasks(taskArns)
                .build()).tasks();

        // Extract network interface IDs
        List<String> eniIds = tasks.stream()
                .flatMap(task -> task.attachments().stream())
                .flatMap(attachment -> attachment.details().stream())
                .filter(detail -> detail.name().equals("networkInterfaceId"))
                .map(KeyValuePair::value)
                .collect(Collectors.toList());

        // Get private IPs from ENIs
        return ec2Client.describeNetworkInterfaces(DescribeNetworkInterfacesRequest.builder()
                        .networkInterfaceIds(eniIds)
                        .build())
                .networkInterfaces()
                .stream()
                .map(NetworkInterface::privateIpAddress)
                .collect(Collectors.toList());
    }
}
