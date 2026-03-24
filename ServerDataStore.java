import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ServerDataStore {
    private static final String DATA_DIR = "msh";
    private static final String SERVERS_FILE = "servers.json";
    
    public static void saveServers(List<ServerCore> servers) {
        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            List<Map<String, Object>> serverList = new ArrayList<>();
            for (ServerCore s : servers) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", s.getId());
                map.put("name", s.getName());
                map.put("jarPath", s.getJarPath());
                map.put("minRam", s.getMinRam());
                map.put("maxRam", s.getMaxRam());
                map.put("javaPath", s.getJavaPath());
                map.put("workingDir", s.getWorkingDir());
                map.put("extraArgs", s.getExtraArgs());
                map.put("autoMemory", s.isAutoMemory());
                map.put("logSizeLimit", s.getLogSizeLimit());
                map.put("logDisplayLines", s.getLogDisplayLines());
                map.put("order", s.getOrder());
                map.put("starred", s.isStarred());
                map.put("processPid", s.getProcessPid());
                serverList.add(map);
            }
            
            String json = JsonUtils.listToJson(serverList);
            
            File file = new File(dir, SERVERS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json);
            }
        } catch (Exception e) {
            System.out.println("保存失败: " + e.getMessage());
        }
    }
    
    public static List<ServerCore> loadServers() {
        List<ServerCore> servers = new ArrayList<>();
        
        try {
            File file = new File(DATA_DIR, SERVERS_FILE);
            if (!file.exists()) {
                return servers;
            }
            
            String content = new String(Files.readAllBytes(file.toPath()));
            content = content.trim();
            
            if (content.isEmpty() || !content.startsWith("[")) {
                return servers;
            }
            
            List<Object> serverList = JsonUtils.parseArray(content);
            
            for (Object obj : serverList) {
                if (!(obj instanceof Map)) continue;
                
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) obj;
                
                String id = JsonUtils.getString(data, "id", null);
                String name = JsonUtils.getString(data, "name", null);
                String jarPath = JsonUtils.getString(data, "jarPath", null);
                
                if (name == null || jarPath == null) continue;
                
                String minRam = JsonUtils.getString(data, "minRam", "1G");
                String maxRam = JsonUtils.getString(data, "maxRam", "2G");
                String javaPath = JsonUtils.getString(data, "javaPath", "java");
                String extraArgs = JsonUtils.getString(data, "extraArgs", "");
                boolean autoMemory = JsonUtils.getBoolean(data, "autoMemory", false);
                int logSizeLimit = JsonUtils.getInt(data, "logSizeLimit", -1);
                int logDisplayLines = JsonUtils.getInt(data, "logDisplayLines", 500);
                int order = JsonUtils.getInt(data, "order", 0);
                boolean starred = JsonUtils.getBoolean(data, "starred", false);
                long processPid = JsonUtils.getLong(data, "processPid", -1);

                ServerCore server;
                if (id != null && !id.isEmpty()) {
                    server = new ServerCore(id, name, jarPath, minRam, maxRam, javaPath, extraArgs, autoMemory);
                } else {
                    server = new ServerCore(name, jarPath, minRam, maxRam, javaPath, extraArgs, autoMemory);
                }
                server.setLogSizeLimit(logSizeLimit);
                server.setLogDisplayLines(logDisplayLines);
                server.setOrder(order);
                server.setStarred(starred);
                server.setProcessPid(processPid);
                servers.add(server);
            }
        } catch (Exception e) {
            System.out.println("加载失败: " + e.getMessage());
        }
        
        return servers;
    }
}
