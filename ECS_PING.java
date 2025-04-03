import org.jgroups.Address;
import org.jgroups.PhysicalAddress;
import org.jgroups.stack.IpAddress;
import org.jgroups.protocols.PING;
import org.jgroups.annotations.Property;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

public class ECS_PING extends PING {

    @Property(description = "AWS ECS Cluster Name")
    private String ecsClusterName;

    @Property(description = "AWS ECS Service Name")
    private String ecsServiceName;

    private final EcsClient ecsClient = EcsClient.create();

    @Override
    protected void findMembers(List<Address> members, boolean initialDiscovery, Responses responses) {
        List<String> ipAddresses = getECSTaskIPs();

        for (String ip : ipAddresses) {
            try {
                InetAddress inetAddress = InetAddress.getByName(ip);
                PhysicalAddress physicalAddr = new IpAddress(inetAddress, bind_port);
                responses.addResponse(null, physicalAddr);
            } catch (Exception e) {
                log.error("Failed to resolve IP address for ECS task", e);
            }
        }
    }

    private List<String> getECSTaskIPs() {
        try {
            ListTasksRequest listTasksRequest = ListTasksRequest.builder()
                    .cluster(ecsClusterName)
                    .serviceName(ecsServiceName)
                    .build();

            ListTasksResponse listTasksResponse = ecsClient.listTasks(listTasksRequest);
            if (listTasksResponse.taskArns().isEmpty()) {
                return List.of();
            }

            DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
                    .cluster(ecsClusterName)
                    .tasks(listTasksResponse.taskArns())
                    .build();

            DescribeTasksResponse describeTasksResponse = ecsClient.describeTasks(describeTasksRequest);

            return describeTasksResponse.tasks().stream()
                    .flatMap(task -> task.containers().stream())
                    .map(container -> container.networkInterfaces().get(0).privateIpv4Address())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to fetch ECS task IPs", e);
            return List.of();
        }
    }
}
