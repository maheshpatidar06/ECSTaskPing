package com.example.jgroups.ping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jgroups.PhysicalAddress;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Tuple;
import org.jgroups.util.UUID;

import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ECSOrLocalPing extends FILE_PING {

    private static final Map<UUID, Tuple<PhysicalAddress, byte[]>> memoryStore = new ConcurrentHashMap<>();
    private static final Set<PhysicalAddress> knownHosts = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isEcs = false;

    @Override
    public void init() throws Exception {
        super.init();
        isEcs = detectEcsEnvironment();
        knownHosts.clear();
        if (isEcs) {
            discoverEcsTaskIps();
        } else {
            addLocalIp();
        }
    }

    private boolean detectEcsEnvironment() {
        return System.getenv("ECS_CONTAINER_METADATA_URI_V4") != null;
    }

    private void addLocalIp() {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            knownHosts.add(new IpAddress(localIp, bind_port));
            log.info("Added local IP: " + localIp);
        } catch (Exception e) {
            log.error("Could not add local IP", e);
        }
    }

    private void discoverEcsTaskIps() {
        try {
            String metadataUri = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
            if (metadataUri == null) return;

            URL url = new URL(metadataUri + "/task");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            try (InputStream in = conn.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode containers = root.path("Containers");

                for (JsonNode container : containers) {
                    String networkMode = container.path("Networks").get(0).path("NetworkMode").asText();
                    String ipAddress = container.path("Networks").get(0).path("IPv4Addresses").get(0).asText();
                    log.info("Discovered ECS IP: " + ipAddress + " (mode: " + networkMode + ")");
                    knownHosts.add(new IpAddress(ipAddress, bind_port));
                }
            }
        } catch (Exception e) {
            log.error("Failed to discover ECS IPs", e);
        }
    }

    @Override
    protected Map<UUID, Tuple<PhysicalAddress, byte[]>> read(String clustername) {
        return new HashMap<>(memoryStore);
    }

    @Override
    protected void write(String clustername, UUID logical_addr, PhysicalAddress phys_addr, byte[] data) {
        memoryStore.put(logical_addr, new Tuple<>(phys_addr, data));
    }

    @Override
    protected void remove(String clustername, UUID addr) {
        memoryStore.remove(addr);
    }

    @Override
    protected void clear(String clustername) {
        memoryStore.clear();
    }

    @Override
    protected Collection<PhysicalAddress> getPhysicalAddresses() {
        return new ArrayList<>(knownHosts);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}package com.example.jgroups.ping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jgroups.PhysicalAddress;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Tuple;
import org.jgroups.util.UUID;

import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ECSOrLocalPing extends FILE_PING {

    private static final Map<UUID, Tuple<PhysicalAddress, byte[]>> memoryStore = new ConcurrentHashMap<>();
    private static final Set<PhysicalAddress> knownHosts = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isEcs = false;

    @Override
    public void init() throws Exception {
        super.init();
        isEcs = detectEcsEnvironment();
        knownHosts.clear();
        if (isEcs) {
            discoverEcsTaskIps();
        } else {
            addLocalIp();
        }
    }

    private boolean detectEcsEnvironment() {
        return System.getenv("ECS_CONTAINER_METADATA_URI_V4") != null;
    }

    private void addLocalIp() {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            knownHosts.add(new IpAddress(localIp, bind_port));
            log.info("Added local IP: " + localIp);
        } catch (Exception e) {
            log.error("Could not add local IP", e);
        }
    }

    private void discoverEcsTaskIps() {
        try {
            String metadataUri = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
            if (metadataUri == null) return;

            URL url = new URL(metadataUri + "/task");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            try (InputStream in = conn.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode containers = root.path("Containers");

                for (JsonNode container : containers) {
                    String networkMode = container.path("Networks").get(0).path("NetworkMode").asText();
                    String ipAddress = container.path("Networks").get(0).path("IPv4Addresses").get(0).asText();
                    log.info("Discovered ECS IP: " + ipAddress + " (mode: " + networkMode + ")");
                    knownHosts.add(new IpAddress(ipAddress, bind_port));
                }
            }
        } catch (Exception e) {
            log.error("Failed to discover ECS IPs", e);
        }
    }

    @Override
    protected Map<UUID, Tuple<PhysicalAddress, byte[]>> read(String clustername) {
        return new HashMap<>(memoryStore);
    }

    @Override
    protected void write(String clustername, UUID logical_addr, PhysicalAddress phys_addr, byte[] data) {
        memoryStore.put(logical_addr, new Tuple<>(phys_addr, data));
    }
    @Override
public void readAll(List<Address> members, String clusterName, Responses responses) {
    log.debug("ECSPING: Reading all peers for cluster {}", clusterName);

    try {
        ECSNodeIPResolver resolver = new ECSNodeIPResolver();
        List<String> ips = resolver.getPrivateIpsOfServiceTasks(); // Step 1: Get ECS peer IPs

        for (String ip : ips) {
            // Step 2: Create address
            InetAddress inetAddr = InetAddress.getByName(ip);
            PhysicalAddress physAddr = new IpAddress(inetAddr, bind_port); // bind_port from config

            PingData data = new PingData(null, true, physAddr); // null logical Address for now
            PingHeader hdr = new PingHeader(PingHeader.GET_MBRS_REQ)
                    .clusterName(cluster_name);

            Message msg = new Message(null, null, null)
                    .putHeader(this.id, hdr)
                    .setDest(null)
                    .setSrc(local_addr)
                    .setTransientFlag(Message.TransientFlag.DONT_LOOPBACK)
                    .setFlag(Message.Flag.INTERNAL)
                    .setBuffer(marshal(data));

            msg.setDest(physAddr);
            log.debug("ECSPING: Sending discovery ping to {}", ip);
            down(msg); // Step 3: Send the ping to the peer IP
        }

    } catch (Exception e) {
        log.error("ECSPING readAll() failed", e);
    }
}

    @Override
    protected void remove(String clustername, UUID addr) {
        memoryStore.remove(addr);
    }

    @Override
    protected void clear(String clustername) {
        memoryStore.clear();
    }

    @Override
    protected Collection<PhysicalAddress> getPhysicalAddresses() {
        return new ArrayList<>(knownHosts);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
