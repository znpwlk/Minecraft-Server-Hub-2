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
                indexData = JsonUtils.parseObject(content);
                @SuppressWarnings("unchecked")
                Map<String, String> mapping = (Map<String, String>) indexData.get("versionMapping");
                versionMapping = mapping;
                defaultVersion = JsonUtils.getString(indexData, "defaultVersion", null);
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
            Map<String, Object> data = JsonUtils.parseObject(content);
            List<Object> rawList = JsonUtils.getList(data, "gameRules");

            List<GameRuleItem> items = new ArrayList<>();
            for (Object obj : rawList) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedMap = (Map<String, Object>) obj;
                    items.add(GameRuleItem.fromMap(typedMap));
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
    
    public String getMatchedVersion(String serverVersion) {
        return findBestMatchVersion(serverVersion);
    }
    
    public void refreshIndex() {
        loadIndex();
    }
}
