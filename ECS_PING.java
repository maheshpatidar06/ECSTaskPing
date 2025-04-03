import org.jgroups.Address;
import org.jgroups.PhysicalAddress;
import org.jgroups.View;
import org.jgroups.annotations.Property;
import org.jgroups.protocols.PING;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Responses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ECS_PING extends PING {

    private static final Logger log = LoggerFactory.getLogger(ECS_PING.class);

    @Property(description = "AWS ECS Cluster Name")
    private String ecsClusterName;

    @Property(description = "AWS ECS Service Name")
    private String ecsServiceName;

    private final EcsClient ecsClient = EcsClient.create();
    private final Set<PhysicalAddress> discoveredAddresses = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void init() throws Exception {
        super.init();
        startPeriodicTaskDiscovery();
    }

    @Override
    protected void findMembers(List<Address> members, boolean initialDiscovery, Responses responses) {
        log.info("Running ECS_PING findMembers");
        updateClusterNodes();

        for (PhysicalAddress addr : discoveredAddresses) {
            responses.addResponse(null, addr);
        }
    }

    @Override
    public void viewAccepted(View view) {
        super.viewAccepted(view);
        log.info("Updated cluster view: " + view);
    }

    private void startPeriodicTaskDiscovery() {
        scheduler.scheduleAtFixedRate(this::updateClusterNodes, 10, 30, TimeUnit.SECONDS);
    }

    private void updateClusterNodes() {
        try {
            List<String> ipAddresses = getECSTaskIPs();
            Set<PhysicalAddress> newAddresses = ipAddresses.stream()
                .map(this::toPhysicalAddress)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            synchronized (discoveredAddresses) {
                discoveredAddresses.clear();
                discoveredAddresses.addAll(newAddresses);
            }

            log.info("Updated discovered nodes: {}", discoveredAddresses);
        } catch (Exception e) {
            log.error("Failed to fetch ECS task IPs", e);
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
                return Collections.emptyList();
            }

            DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
                    .cluster(ecsClusterName)
                    .tasks(listTasksResponse.taskArns())
                    .build();

            DescribeTasksResponse describeTasksResponse = ecsClient.describeTasks(describeTasksRequest);

            return describeTasksResponse.tasks
