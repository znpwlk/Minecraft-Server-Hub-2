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
            
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            
            for (int i = 0; i < servers.size(); i++) {
                ServerCore s = servers.get(i);
                json.append("  {\n");
                json.append("    \"id\": \"").append(escapeJson(s.getId())).append("\",\n");
                json.append("    \"name\": \"").append(escapeJson(s.getName())).append("\",\n");
                json.append("    \"jarPath\": \"").append(escapeJson(s.getJarPath())).append("\",\n");
                json.append("    \"minRam\": \"").append(escapeJson(s.getMinRam())).append("\",\n");
                json.append("    \"maxRam\": \"").append(escapeJson(s.getMaxRam())).append("\",\n");
                json.append("    \"javaPath\": \"").append(escapeJson(s.getJavaPath())).append("\",\n");
                json.append("    \"workingDir\": \"").append(escapeJson(s.getWorkingDir())).append("\",\n");
                json.append("    \"extraArgs\": \"").append(escapeJson(s.getExtraArgs())).append("\",\n");
                json.append("    \"autoMemory\": ").append(s.isAutoMemory()).append(",\n");
                json.append("    \"logSizeLimit\": ").append(s.getLogSizeLimit()).append(",\n");
                json.append("    \"logDisplayLines\": ").append(s.getLogDisplayLines()).append(",\n");
                json.append("    \"order\": ").append(s.getOrder()).append(",\n");
                json.append("    \"starred\": ").append(s.isStarred()).append(",\n");
                json.append("    \"processPid\": ").append(s.getProcessPid()).append("\n");
                json.append("  }");
                if (i < servers.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            
            json.append("]");
            
            File file = new File(dir, SERVERS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString());
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
            
            List<Map<String, String>> serverList = parseJsonArray(content);
            
            for (Map<String, String> data : serverList) {
                String id = data.get("id");
                String name = data.get("name");
                String jarPath = data.get("jarPath");
                String minRam = data.getOrDefault("minRam", "1G");
                String maxRam = data.getOrDefault("maxRam", "2G");
                String javaPath = data.getOrDefault("javaPath", "java");
                String extraArgs = data.getOrDefault("extraArgs", "");
                boolean autoMemory = "true".equalsIgnoreCase(data.getOrDefault("autoMemory", "false"));
                int logSizeLimit = -1;
                try {
                    logSizeLimit = Integer.parseInt(data.getOrDefault("logSizeLimit", "-1"));
                } catch (NumberFormatException e) {
                    logSizeLimit = -1;
                }
                
                int logDisplayLines = 500;
                try {
                    logDisplayLines = Integer.parseInt(data.getOrDefault("logDisplayLines", "500"));
                } catch (NumberFormatException e) {
                    logDisplayLines = 500;
                }
                
                int order = 0;
                try {
                    order = Integer.parseInt(data.getOrDefault("order", "0"));
                } catch (NumberFormatException e) {
                    order = 0;
                }
                
                boolean starred = "true".equalsIgnoreCase(data.getOrDefault("starred", "false"));
                
                long processPid = -1;
                try {
                    processPid = Long.parseLong(data.getOrDefault("processPid", "-1"));
                } catch (NumberFormatException e) {
                    processPid = -1;
                }

                if (name != null && jarPath != null) {
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
            }
        } catch (Exception e) {
            System.out.println("加载失败: " + e.getMessage());
        }
        
        return servers;
    }
    
    private static List<Map<String, String>> parseJsonArray(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return result;
        }
        
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return result;
        }
        
        List<String> objects = splitObjects(json);
        
        for (String obj : objects) {
            Map<String, String> map = parseJsonObject(obj);
            if (!map.isEmpty()) {
                result.add(map);
            }
        }
        
        return result;
    }
    
    private static List<String> splitObjects(String json) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        
        for (char c : json.toCharArray()) {
            if (c == '"' && (current.length() == 0 || current.charAt(current.length() - 1) != '\\')) {
                inString = !inString;
            }
            
            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    String obj = current.toString().trim();
                    if (!obj.isEmpty()) {
                        result.add(obj);
                    }
                    current = new StringBuilder();
                    continue;
                }
            }
            
            current.append(c);
        }
        
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        
        return result;
    }
    
    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> result = new HashMap<>();
        
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }
        
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return result;
        }
        
        List<String> pairs = splitPairs(json);
        
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex > 0) {
                String key = pair.substring(0, colonIndex).trim();
                String value = pair.substring(colonIndex + 1).trim();
                
                key = unescapeJson(key);
                value = unescapeJson(value);
                
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    private static List<String> splitPairs(String json) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        
        for (char c : json.toCharArray()) {
            if (c == '"' && (current.length() == 0 || current.charAt(current.length() - 1) != '\\')) {
                inString = !inString;
            }
            
            if (!inString) {
                if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    String pair = current.toString().trim();
                    if (!pair.isEmpty()) {
                        result.add(pair);
                    }
                    current = new StringBuilder();
                    continue;
                }
            }
            
            current.append(c);
        }
        
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        
        return result;
    }
    
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    private static String unescapeJson(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}