import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class GameRulesManager {
    private static final String INDEX_URL = "https://msh.znpwlk.vip/gamerules/index.json";
    private static final String RULES_BASE_URL = "https://msh.znpwlk.vip/gamerules/d/";
    private static final String CACHE_DIR = "msh/gamerules";
    
    private Map<String, Object> indexData;
    private Map<String, String> versionMapping;
    private String defaultVersion;
    
    public GameRulesManager() {
        loadIndex();
    }
    
    private void loadIndex() {
        try {
            String content = downloadContent(INDEX_URL);
            if (content != null) {
                indexData = parseJson(content);
                versionMapping = (Map<String, String>) indexData.get("versionMapping");
                defaultVersion = (String) indexData.get("defaultVersion");
            }
        } catch (Exception e) {
            System.out.println("下载索引失败: " + e.getMessage());
        }
    }
    
    public List<GameRuleItem> getGameRules(String serverVersion) {
        String matchedVersion = findBestMatchVersion(serverVersion);
        if (matchedVersion == null) {
            matchedVersion = defaultVersion;
        }
        
        String content = loadRulesContent(matchedVersion);
        if (content == null) {
            return new ArrayList<>();
        }
        
        try {
            Map<String, Object> data = parseJson(content);
            List<?> rawList = (List<?>) data.get("gameRules");

            List<GameRuleItem> items = new ArrayList<>();
            if (rawList != null) {
                for (Object obj : rawList) {
                    if (obj instanceof Map<?, ?>) {
                        Map<?, ?> ruleMap = (Map<?, ?>) obj;
                        Map<String, Object> typedMap = new HashMap<>();
                        for (Map.Entry<?, ?> entry : ruleMap.entrySet()) {
                            if (entry.getKey() instanceof String) {
                                typedMap.put((String) entry.getKey(), entry.getValue());
                            }
                        }
                        items.add(GameRuleItem.fromMap(typedMap));
                    }
                }
            }
            return items;
        } catch (Exception e) {
            System.out.println("解析游戏规则失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private String findBestMatchVersion(String serverVersion) {
        if (serverVersion == null || serverVersion.isEmpty()) {
            return defaultVersion;
        }
        
        if (versionMapping == null || versionMapping.isEmpty()) {
            return defaultVersion;
        }
        
        if (versionMapping.containsKey(serverVersion)) {
            return versionMapping.get(serverVersion);
        }
        
        double serverVerNum = parseVersionNumber(serverVersion);
        String bestMatch = null;
        double bestDiff = Double.MAX_VALUE;
        
        for (String availableVersion : versionMapping.keySet()) {
            double availVerNum = parseVersionNumber(availableVersion);
            double diff = serverVerNum - availVerNum;
            
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                bestMatch = versionMapping.get(availableVersion);
            }
        }
        
        return bestMatch != null ? bestMatch : defaultVersion;
    }
    
    private double parseVersionNumber(String version) {
        try {
            String[] parts = version.split("\\.");
            double result = 0;
            for (int i = 0; i < parts.length && i < 3; i++) {
                result += Double.parseDouble(parts[i]) * Math.pow(100, 2 - i);
            }
            return result;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private String loadRulesContent(String version) {
        File localFile = new File(CACHE_DIR, version + ".json");
        
        if (localFile.exists()) {
            try {
                return new String(Files.readAllBytes(localFile.toPath()));
            } catch (Exception e) {
                System.out.println("读取本地缓存失败: " + e.getMessage());
            }
        }
        
        String url = RULES_BASE_URL + version + ".json";
        String content = downloadContent(url);
        
        if (content != null) {
            saveToCache(version + ".json", content);
        }
        
        return content;
    }
    
    private String downloadContent(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URI(urlString).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "MSH2/" + Main.VERSION);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    return sb.toString();
                }
            } else {
                System.out.println("下载失败，HTTP状态码: " + responseCode);
            }
        } catch (Exception e) {
            System.out.println("下载内容失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }
    
    private void saveToCache(String filename, String content) {
        try {
            File dir = new File(CACHE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, filename);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
            }
        } catch (Exception e) {
            System.out.println("保存缓存失败: " + e.getMessage());
        }
    }
    
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        json = json.trim();
        
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }
        
        json = json.substring(1, json.length() - 1).trim();
        
        List<String> tokens = tokenize(json);
        
        for (int i = 0; i < tokens.size(); i += 2) {
            if (i + 1 >= tokens.size()) break;
            
            String key = tokens.get(i);
            String value = tokens.get(i + 1);
            
            key = key.trim();
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            
            value = value.trim();
            
            if (value.startsWith("{") && value.endsWith("}")) {
                result.put(key, parseJson(value));
            } else if (value.startsWith("[") && value.endsWith("]")) {
                result.put(key, parseArray(value));
            } else if (value.startsWith("\"") && value.endsWith("\"")) {
                result.put(key, value.substring(1, value.length() - 1));
            } else if (value.equals("true")) {
                result.put(key, true);
            } else if (value.equals("false")) {
                result.put(key, false);
            } else if (value.equals("null")) {
                result.put(key, null);
            } else {
                try {
                    if (value.contains(".")) {
                        result.put(key, Double.parseDouble(value));
                    } else {
                        result.put(key, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    result.put(key, value);
                }
            }
        }
        
        return result;
    }
    
    private List<String> tokenize(String json) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escape = false;
        
        for (char c : json.toCharArray()) {
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            
            if (c == '\\') {
                current.append(c);
                escape = true;
                continue;
            }
            
            if (c == '"' && braceDepth == 0 && bracketDepth == 0) {
                inString = !inString;
                current.append(c);
                continue;
            }
            
            if (!inString) {
                if (c == '{') braceDepth++;
                if (c == '}') braceDepth--;
                if (c == '[') bracketDepth++;
                if (c == ']') bracketDepth--;
                
                if (c == ':' && braceDepth == 0 && bracketDepth == 0) {
                    tokens.add(current.toString().trim());
                    current = new StringBuilder();
                    continue;
                }
                
                if (c == ',' && braceDepth == 0 && bracketDepth == 0) {
                    tokens.add(current.toString().trim());
                    current = new StringBuilder();
                    continue;
                }
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            tokens.add(current.toString().trim());
        }
        
        return tokens;
    }
    
    private List<Object> parseArray(String json) {
        List<Object> result = new ArrayList<>();
        json = json.trim();
        
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return result;
        }
        
        json = json.substring(1, json.length() - 1).trim();
        
        if (json.isEmpty()) {
            return result;
        }
        
        List<String> tokens = splitArray(json);
        
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;
            
            if (token.startsWith("{") && token.endsWith("}")) {
                result.add(parseJson(token));
            } else if (token.startsWith("[") && token.endsWith("]")) {
                result.add(parseArray(token));
            } else if (token.startsWith("\"") && token.endsWith("\"")) {
                result.add(token.substring(1, token.length() - 1));
            } else if (token.equals("true")) {
                result.add(true);
            } else if (token.equals("false")) {
                result.add(false);
            } else if (token.equals("null")) {
                result.add(null);
            } else {
                try {
                    if (token.contains(".")) {
                        result.add(Double.parseDouble(token));
                    } else {
                        result.add(Long.parseLong(token));
                    }
                } catch (NumberFormatException e) {
                    result.add(token);
                }
            }
        }
        
        return result;
    }
    
    private List<String> splitArray(String json) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escape = false;
        
        for (char c : json.toCharArray()) {
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            
            if (c == '\\') {
                current.append(c);
                escape = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            
            if (!inString) {
                if (c == '{') braceDepth++;
                if (c == '}') braceDepth--;
                if (c == '[') bracketDepth++;
                if (c == ']') bracketDepth--;
                
                if (c == ',' && braceDepth == 0 && bracketDepth == 0) {
                    tokens.add(current.toString().trim());
                    current = new StringBuilder();
                    continue;
                }
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            tokens.add(current.toString().trim());
        }
        
        return tokens;
    }
    
    public String getMatchedVersion(String serverVersion) {
        return findBestMatchVersion(serverVersion);
    }
    
    public void refreshIndex() {
        loadIndex();
    }
}
