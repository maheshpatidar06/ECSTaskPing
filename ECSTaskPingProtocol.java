import org.jgroups.*;
import com.amazonaws.services.ecs.*;
import com.amazonaws.services.ecs.model.*;
import java.util.*;

public class ECSTaskPingProtocol extends Protocol {

    private AmazonECS ecsClient;
    private String clusterName;
    private String serviceName; // New field to specify the ECS service
    private Set<String> knownNodes;  // A set of ECS task IPs or hostnames
    private Map<String, Long> pingTimestamps;  // To track ping responses (last successful ping timestamp)
    private long pingTimeoutMillis = 5000;  // Timeout for ping response in milliseconds

    public ECSTaskPingProtocol(String clusterName, String serviceName) {
        this.ecsClient = AmazonECSClient.builder().build();  // Initialize AWS ECS client
        this.clusterName = clusterName;
        this.serviceName = serviceName;  // Set the service name to filter by
        this.knownNodes = new HashSet<>();
        this.pingTimestamps = new HashMap<>();
    }

    @Override
    public void init() throws Exception {
        super.init();
        discoverTasks();  // Discover tasks when protocol is initialized
    }

    @Override
    public void start() throws Exception {
        super.start();
        // Optional: Periodically refresh the task list
    }

    // Discover ECS tasks for a specific service (fetches metadata, e.g., IP addresses)
    private void discoverTasks() {
        try {
            // List the running tasks for the specified ECS service in the ECS cluster
            ListTasksRequest listTasksRequest = new ListTasksRequest()
                .withCluster(clusterName)
                .withServiceName(serviceName)  // Filter tasks by service name
                .withDesiredStatus(TaskDesiredStatus.RUNNING);

            ListTasksResponse tasksResponse = ecsClient.listTasks(listTasksRequest);
            List<String> taskArns = tasksResponse.getTaskArns();

            if (taskArns != null && !taskArns.isEmpty()) {
                describeTasks(taskArns);  // Describe the filtered tasks
            }
        } catch (Exception e) {
            System.err.println("Error discovering ECS tasks for service " + serviceName + ": " + e.getMessage());
        }
    }

    // Get ECS task details (including IP addresses) for the filtered tasks
    private void describeTasks(List<String> taskArns) {
        try {
            DescribeTasksRequest describeTasksRequest = new DescribeTasksRequest()
                .withCluster(clusterName)
                .withTasks(taskArns);

            DescribeTasksResponse describeResponse = ecsClient.describeTasks(describeTasksRequest);
            List<Task> tasks = describeResponse.getTasks();

            // Loop over each task and fetch its IP address
            for (Task task : tasks) {
                String taskArn = task.getTaskArn();
                System.out.println("Task ARN: " + taskArn);

                // Check if the task has containers and extract their network interface IPs
                if (task.getContainers() != null && !task.getContainers().isEmpty()) {
                    String ip = task.getContainers().get(0)
                                   .getNetworkInterfaces().get(0)
                                   .getPrivateIpv4Address();
                    System.out.println("Task IP: " + ip);
                    knownNodes.add(ip);  // Add IP to the known nodes list
                }
            }
        } catch (Exception e) {
            System.err.println("Error describing tasks for service " + serviceName + ": " + e.getMessage());
        }
    }

    // Return the list of known nodes (IP addresses of ECS tasks)
    @Override
    public List<String> getNodes() {
        return new ArrayList<>(knownNodes);
    }

    // Handle sending a ping to another node
    @Override
    public void sendPing() throws Exception {
        for (String node : knownNodes) {
            long currentTime = System.currentTimeMillis();

            // Send a ping to each node
            Ping pingMessage = new Ping(node, currentTime);
            send(pingMessage);

            // Record the last ping timestamp
            pingTimestamps.put(node, currentTime);
            System.out.println("Sent ping to node: " + node);
        }
    }

    // Handle receiving a ping (success)
    @Override
    public void receivePing(Ping pingMessage) {
        String node = pingMessage.getNode();
        long receivedTime = System.currentTimeMillis();
        long sentTime = pingMessage.getTimestamp();

        // If we receive a ping, it means the node is reachable (success)
        long roundTripTime = receivedTime - sentTime;
        System.out.println("Received ping from node: " + node + " with RTT: " + roundTripTime + "ms");

        // Update the ping timestamp for this node to track the success
        pingTimestamps.put(node, receivedTime);

        // Send a response back to acknowledge successful ping
        send(new Ping(node, receivedTime));  // Send a response back
    }

    // Handle ping failure (timeout or no response)
    @Override
    public void handlePingFailure(String node) {
        // If a ping fails (e.g., no response within the timeout), mark the node as unreachable
        System.err.println("Ping to node " + node + " failed or timed out.");
        knownNodes.remove(node);  // Optionally remove the node from the cluster
    }

    // Regularly check for failed pings (no response within the timeout period)
    public void checkForPingTimeouts() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : pingTimestamps.entrySet()) {
            String node = entry.getKey();
            long lastPingTimestamp = entry.getValue();

            // Check if the last ping response was too long ago
            if (currentTime - lastPingTimestamp > pingTimeoutMillis) {
                handlePingFailure(node);  // If ping response took too long, treat it as failure
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        // Optional: Clean up resources if needed when stopping the protocol
    }

    public static class Ping {
        private String node;
        private long timestamp;

        public Ping(String node, long timestamp) {
            this.node = node;
            this.timestamp = timestamp;
        }

        public String getNode() {
            return node;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
