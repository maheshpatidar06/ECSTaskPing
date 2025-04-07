package com.example.jgroups;

import org.jgroups.*;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.Discovery;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Promise;
import org.jgroups.util.Streamable;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.*;
import java.util.concurrent.*;

public class ECS_PING extends Discovery {

    public static final short ECS_PING_ID = 12345;

    static {
        ClassConfigurator.addProtocol(ECS_PING_ID, ECS_PING.class);
    }

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;

    // Refresh interval in seconds (can be configured)
    private long refreshInterval = 30;

    @Override
    public void init() throws Exception {
        super.init();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        startPeriodicRefresh();
    }

    @Override
    public void stop() {
        super.stop();
        if (refreshTask != null)
            refreshTask.cancel(true);
        if (scheduler != null)
            scheduler.shutdownNow();
    }

    private void startPeriodicRefresh() {
        log.debug("ECS_PING: Starting periodic refresh every " + refreshInterval + "s");
        refreshTask = scheduler.scheduleAtFixedRate(() -> {
            log.debug("ECS_PING: Performing periodic refresh");
            try {
                findMembers(null, false);
            } catch (Exception e) {
                log.error("ECS_PING periodic refresh error", e);
            }
        }, refreshInterval, refreshInterval, TimeUnit.SECONDS);
    }

    @Override
    protected void findMembers(List<Address> members, boolean initial_discovery) {
        log.debug("ECS_PING.findMembers: ECS discovery started");

        // Simulate ECS discovery: you should replace this with actual ECS metadata query or IP listing
        List<PhysicalAddress> discovered = simulateECSDiscovery();

        // Notify discovered members to upper layers
        for (PhysicalAddress addr : discovered) {
            PingData pingData = new PingData(null, true, UUID.randomUUID(), addr);
            PingRsp rsp = new PingRsp(addr, null, true, null);
            up(new Event(Event.FIND_MBRS_OK, Collections.singletonList(rsp)));
        }

        log.debug("ECS_PING.findMembers: discovery finished");
    }

    private List<PhysicalAddress> simulateECSDiscovery() {
        List<PhysicalAddress> addresses = new ArrayList<>();
        // Dummy list, replace with real IP resolution
        addresses.add(new IpAddress("127.0.0.1", 7800));
        // You could pull from ECS or service registry here
        return addresses;
    }

    @Override
    protected void sendGetMembersRequest(String cluster_name, Promise<JoinRsp> promise, boolean use_mcast) {
        log.debug("ECS_PING.sendGetMembersRequest: no-op for ECS_PING");
        // No-op. ECS_PING only uses ECS metadata for discovery
    }

    @Override
    public Object up(Event evt) {
        Message msg = (Message) evt.getArg();
        ECS_PING_Header hdr = msg.getHeader(ECS_PING_ID);
        if (hdr != null) {
            log.debug("ECS_PING: received custom header");
        }
        return up_prot.up(evt);
    }

    @Override
    public String getName() {
        return "ECS_PING";
    }

    public static class ECS_PING_Header extends Header implements Streamable {

        @Override
        public void writeTo(DataOutput out) { }

        @Override
        public void readFrom(DataInput in) { }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public String toString() {
            return "ECS_PING_Header{}";
        }
    }
}
