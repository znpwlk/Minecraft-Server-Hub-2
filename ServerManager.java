import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ServerManager {
    private List<ServerCore> servers;
    private ServerCore selectedServer;
    
    public ServerManager() {
        this.servers = new ArrayList<>();
    }
    
    public void addServer(ServerCore server) {
        int maxOrder = servers.stream().mapToInt(ServerCore::getOrder).max().orElse(0);
        server.setOrder(maxOrder + 1);
        servers.add(server);
    }
    
    public void removeServer(ServerCore server) {
        server.stop();
        servers.remove(server);
        if (selectedServer == server) {
            selectedServer = null;
        }
    }
    
    public void removeServer(String name) {
        ServerCore server = getByName(name);
        if (server != null) {
            removeServer(server);
        }
    }
    
    public List<ServerCore> getServers() {
        return new ArrayList<>(servers);
    }
    
    public ServerCore getByName(String name) {
        for (ServerCore s : servers) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }
    
    public boolean exists(String name) {
        return getByName(name) != null;
    }
    
    public ServerCore getSelected() {
        return selectedServer;
    }
    
    public void setSelected(ServerCore server) {
        this.selectedServer = server;
    }
    
    public void setSelected(String name) {
        this.selectedServer = getByName(name);
    }
    
    public void stopAll() {
        for (ServerCore s : servers) {
            s.stop();
        }
    }
    
    public void forceStopAll() {
        for (ServerCore s : servers) {
            s.forceStop();
        }
    }
    
    public int getRunningCount() {
        int count = 0;
        for (ServerCore s : servers) {
            if (s.isRunning()) count++;
        }
        return count;
    }
    
    public int getTotalCount() {
        return servers.size();
    }
    
    public List<ServerCore> getSortedServers() {
        return servers.stream()
            .sorted(Comparator.comparingInt(ServerCore::getOrder))
            .collect(Collectors.toList());
    }
    
    public List<ServerCore> getStarredServers() {
        return servers.stream()
            .filter(ServerCore::isStarred)
            .sorted(Comparator.comparingInt(ServerCore::getOrder))
            .collect(Collectors.toList());
    }
    
    public void moveServerUp(ServerCore server) {
        int currentOrder = server.getOrder();
        if (currentOrder <= 0) return;
        
        ServerCore prevServer = servers.stream()
            .filter(s -> s.getOrder() == currentOrder - 1)
            .findFirst()
            .orElse(null);
        
        if (prevServer != null) {
            prevServer.setOrder(currentOrder);
            server.setOrder(currentOrder - 1);
        }
    }
    
    public void moveServerDown(ServerCore server) {
        int currentOrder = server.getOrder();
        int maxOrder = servers.stream().mapToInt(ServerCore::getOrder).max().orElse(0);
        if (currentOrder >= maxOrder) return;
        
        ServerCore nextServer = servers.stream()
            .filter(s -> s.getOrder() == currentOrder + 1)
            .findFirst()
            .orElse(null);
        
        if (nextServer != null) {
            nextServer.setOrder(currentOrder);
            server.setOrder(currentOrder + 1);
        }
    }
    
    public void toggleStar(ServerCore server) {
        server.setStarred(!server.isStarred());
    }
}
