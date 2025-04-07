package com.example.jgroups.protocols;

import com.example.jgroups.ecs.ECSDiscovery;
import org.jgroups.*;
import org.jgroups.protocols.Discovery;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Responses;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom discovery protocol for ECS tasks
 */
public class ECS_PING extends Discovery {

    protected String clusterName = "your-ecs-cluster";
    protected String serviceName = "your-service-name";
    protected long refreshInterval = 10000; // 10 sec default
    protected int discoveryPort = 7800;

    private ECSDiscovery ecsDiscovery;
    private volatile List<IpAddress> currentIPs = new ArrayList<>();
    private volatile Address localAddress;

    @Override
    public void init() throws Exception {
        super.init();
        this.ecsDiscovery = new ECSDiscovery(clusterName, serviceName);
        startPeriodicRefresh();
    }

    private void startPeriodicRefresh() {
        Thread refresher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    updateMembers();
                    Thread.sleep(refreshInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Failed to update ECS IPs", e);
                }
            }
        }, "ECS_PING-Refresher");
        refresher.setDaemon(true);
        refresher.start();
    }

    private void updateMembers() {
        List<String> ips = ecsDiscovery.getRunningTaskIPs();
        List<IpAddress> updated = new ArrayList<>();
        for (String ip : ips) {
            try {
                InetAddress inet = InetAddress.getByName(ip);
                updated.add(new IpAddress(inet, discoveryPort));
            } catch (Exception e) {
                log.error("Invalid IP from ECS: " + ip, e);
            }
        }
        currentIPs = updated;
        log.debug("Refreshed ECS member IPs: " + currentIPs);
    }
    private boolean sendPing(PhysicalAddress dest) {
        try {
            // Build header
            ECS_PING_Header hdr = new ECS_PING_Header(ECS_PING_Header.PING_REQ);
            Message msg = new Message(dest).putHeader(this.id, hdr);
            msg.setFlag(Message.Flag.INTERNAL, Message.Flag.OOB);
    
            // Send and wait for response (simplified)
            down(new Event(Event.MSG, msg));
    
            // Optional: implement some waiting mechanism or response collection here
            return true;
        } catch (Exception e) {
            log.warn("Ping to " + dest + " failed", e);
            return false;
        }
    }

    @Override
    protected void findMembers(List<Address> members, boolean initialDiscovery, Responses responses) {
         PhysicalAddress myPhysical = (PhysicalAddress) down(new Event(Event.GET_PHYSICAL_ADDRESS, localAddress));
        
        // Add self
        PingData selfPing = new PingData(localAddress, true, myPhysical);
        responses.addResponse(selfPing);
        
        // Add others
        for (IpAddress addr : currentIPs) {
            if (addr.equals(myPhysical)) continue;
            boolean success = sendPing(addr); // Custom ping method
        
            if (success) {
                System.out.println("[ECS_PING] Ping SUCCESS to: " + addr);
                responses.addResponse(new PingData(null, false, addr));
            } else {
                System.out.println("[ECS_PING] Ping FAILED to: " + addr);
            }
        }
        
        responses.done(); // Signal completion
    }

    @Override
    public Object up(Event evt) {
        switch (evt.getType()) {
            case Event.SET_LOCAL_ADDRESS:
                localAddress = (Address) evt.getArg();
                break;
        }
        return super.up(evt);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
