public class ECSJGroupApp {

    public static void main(String[] args) throws Exception {
        String clusterName = "your-cluster-name";
        String serviceName = "your-service-name";  // Specify your ECS service name here

        // Create the JGroup configuration and pass the service name for filtering
        JChannel channel = new JChannel("jgroup-config.xml");

        // Start the channel with the custom ping protocol that filters tasks by service name
        ECSTaskPingProtocol pingProtocol = new ECSTaskPingProtocol(clusterName, serviceName);
        channel.getProtocolStack().addProtocol(pingProtocol);

        channel.connect(clusterName);

        // Send and receive messages in the JGroup cluster
        channel.send(new Message(null, "Hello, ECS Cluster!"));

        // Wait for the application to finish (or handle messages)
        Thread.sleep(10000);

        // Close the channel when done
        channel.close();
    }
}
