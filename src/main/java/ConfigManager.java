import java.io.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {
    
    public static Map<String, String> loadConfig(File file) {
        Map<String, String> config = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    config.put(key, value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return config;
    }
    
    public static void saveConfig(File file, Map<String, String> config) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("# Minecraft server properties\n");
            writer.write("# " + new Date() + "\n");
            for (Map.Entry<String, String> entry : config.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static Map<String, String> getDefaultServerProperties() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("spawn-protection", "16");
        defaults.put("max-tick-time", "60000");
        defaults.put("query.port", "25565");
        defaults.put("generator-settings", "");
        defaults.put("sync-chunk-writes", "true");
        defaults.put("force-gamemode", "false");
        defaults.put("allow-nether", "true");
        defaults.put("enforce-whitelist", "false");
        defaults.put("gamemode", "survival");
        defaults.put("broadcast-console-to-ops", "true");
        defaults.put("enable-query", "false");
        defaults.put("player-idle-timeout", "0");
        defaults.put("text-filtering-config", "");
        defaults.put("difficulty", "easy");
        defaults.put("spawn-monsters", "true");
        defaults.put("broadcast-rcon-to-ops", "true");
        defaults.put("op-permission-level", "4");
        defaults.put("pvp", "true");
        defaults.put("entity-broadcast-range-percentage", "100");
        defaults.put("snooper-enabled", "true");
        defaults.put("level-type", "default");
        defaults.put("enable-status", "true");
        defaults.put("resource-pack-prompt", "");
        defaults.put("hardcore", "false");
        defaults.put("enable-command-block", "false");
        defaults.put("network-compression-threshold", "256");
        defaults.put("max-players", "20");
        defaults.put("max-world-size", "29999984");
        defaults.put("resource-pack", "");
        defaults.put("spawn-npcs", "true");
        defaults.put("allow-flight", "false");
        defaults.put("level-name", "world");
        defaults.put("view-distance", "10");
        defaults.put("resource-pack-sha1", "");
        defaults.put("spawn-animals", "true");
        defaults.put("white-list", "false");
        defaults.put("rcon.password", "");
        defaults.put("generate-structures", "true");
        defaults.put("online-mode", "true");
        defaults.put("level-seed", "");
        defaults.put("prevent-proxy-connections", "false");
        defaults.put("use-native-transport", "true");
        defaults.put("enable-jmx-monitoring", "false");
        defaults.put("motd", "A Minecraft Server");
        defaults.put("rate-limit", "0");
        defaults.put("enable-rcon", "false");
        return defaults;
    }
}
