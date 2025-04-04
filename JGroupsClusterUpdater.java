import org.jgroups.JChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class JGroupsClusterUpdater {
    private final ECSTaskIPFetcher ecsTaskIPFetcher;
    private final JChannel jChannel;

    public JGroupsClusterUpdater(ECSTaskIPFetcher ecsTaskIPFetcher, JChannel jChannel) {
        this.ecsTaskIPFetcher = ecsTaskIPFetcher;
        this.jChannel = jChannel;
    }

    @Scheduled(fixedRate = 30000) // Runs every 30 seconds
    public void updateClusterMembers() {
        List<String> privateIps = ecsTaskIPFetcher.getTaskPrivateIps();
        if (!privateIps.isEmpty()) {
            String initialHosts = String.join(",", privateIps);
            System.setProperty("JGROUPS_INITIAL_HOSTS", initialHosts);
            System.out.println("Updated JGroups Cluster: " + initialHosts);
        }
    }
}
