import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class ECSNodeIPResolver {

    private static final String METADATA_URI = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
    private static final ObjectMapper mapper = new ObjectMapper();

    private final EcsClient ecsClient = EcsClient.create();
    private final Ec2Client ec2Client = Ec2Client.create();

    public List<String> getPrivateIpsOfServiceTasks() {
        try {
            // Step 1: Get cluster name and task ARN
            JsonNode metadata = fetchMetadata();
            String clusterArn = metadata.path("Cluster").asText();
            String taskArn = metadata.path("TaskARN").asText();

            // Step 2: Get service name via task tags (must enable ECS managed tags)
            String serviceName = getServiceNameFromTags(clusterArn, taskArn);
            if (serviceName == null) {
                throw new RuntimeException("Could not determine service name from task tags");
            }

            // Step 3: List running tasks for the service
            ListTasksResponse listTasks = ecsClient.listTasks(ListTasksRequest.builder()
                    .cluster(clusterArn)
                    .serviceName(serviceName)
                    .desiredStatus(DesiredStatus.RUNNING)
                    .build());

            List<String> taskArns = listTasks.taskArns();
            if (taskArns.isEmpty()) return Collections.emptyList();

            // Step 4: Describe those tasks
            DescribeTasksResponse tasksResponse = ecsClient.describeTasks(DescribeTasksRequest.builder()
                    .cluster(clusterArn)
                    .tasks(taskArns)
                    .build());

            List<String> eniIds = tasksResponse.tasks().stream()
                    .flatMap(task -> task.attachments().stream())
                    .flatMap(att -> att.details().stream())
                    .filter(d -> d.name().equals("networkInterfaceId"))
                    .map(KeyValuePair::value)
                    .collect(Collectors.toList());

            // Step 5: Fetch private IPs from EC2
            DescribeNetworkInterfacesResponse eniResp = ec2Client.describeNetworkInterfaces(r -> r
                    .networkInterfaceIds(eniIds));

            return eniResp.networkInterfaces().stream()
                    .map(NetworkInterface::privateIpAddress)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to get IPs of tasks in service", e);
        }
    }

    private JsonNode fetchMetadata() throws Exception {
        URL url = new URL(METADATA_URI + "/task");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream()) {
            return mapper.readTree(in);
        }
    }

    private String getServiceNameFromTags(String clusterArn, String taskArn) {
        try {
            ListTagsForResourceResponse tagsResp = ecsClient.listTagsForResource(r -> r.resourceArn(taskArn));
            for (Tag tag : tagsResp.tags()) {
                if (tag.key().equals("ecs:serviceName")) {
                    return tag.value();
                }
            }
        } catch (Exception e) {
            System.err.println("Unable to fetch serviceName from tags: " + e.getMessage());
        }
        return null;
    }
}
