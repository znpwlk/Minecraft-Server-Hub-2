import java.io.*;
import java.util.*;

public class GameRules {
    
    public static Map<String, String> loadProperties(File file) {
        Map<String, String> props = new LinkedHashMap<>();
        if (!file.exists()) {
            return props;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    props.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return props;
    }
    
    public static void saveProperties(File file, Map<String, String> props) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("# Minecraft server properties\n");
            writer.write("# " + new Date() + "\n\n");
            for (Map.Entry<String, String> entry : props.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static Map<String, String> getDefaults() {
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
    
    public static File getConfigFile(String workingDir) {
        return new File(workingDir, "server.properties");
    }
    
    public static boolean createDefaultConfig(String workingDir) {
        File configFile = getConfigFile(workingDir);
        if (!configFile.exists()) {
            saveProperties(configFile, getDefaults());
            return true;
        }
        return false;
    }
    
    public static String getTitle(String key) {
        return switch (key) {
            case "accepts-transfers" -> "接受玩家转移";
            case "allow-flight" -> "允许飞行";
            case "broadcast-console-to-ops" -> "广播控制台到OP";
            case "broadcast-rcon-to-ops" -> "广播RCON到OP";
            case "bug-report-link" -> "错误报告链接";
            case "debug" -> "调试模式";
            case "difficulty" -> "游戏难度";
            case "enable-code-of-conduct" -> "启用行为准则";
            case "enable-jmx-monitoring" -> "启用JMX监控";
            case "enable-query" -> "启用Query";
            case "enable-rcon" -> "启用RCON";
            case "enable-status" -> "启用状态响应";
            case "enforce-secure-profile" -> "强制安全档案";
            case "enforce-whitelist" -> "强制白名单";
            case "entity-broadcast-range-percentage" -> "实体广播范围";
            case "force-gamemode" -> "强制游戏模式";
            case "function-permission-level" -> "函数权限等级";
            case "gamemode" -> "默认游戏模式";
            case "generate-structures" -> "生成结构";
            case "generator-settings" -> "生成器设置";
            case "hardcore" -> "极限模式";
            case "hide-online-players" -> "隐藏在线玩家";
            case "initial-disabled-packs" -> "初始禁用数据包";
            case "initial-enabled-packs" -> "初始启用数据包";
            case "level-name" -> "世界名称";
            case "level-seed" -> "世界种子";
            case "level-type" -> "世界类型";
            case "log-ips" -> "记录IP";
            case "max-chained-neighbor-updates" -> "最大连锁更新";
            case "max-players" -> "最大玩家数";
            case "max-tick-time" -> "最大Tick时间";
            case "max-world-size" -> "最大世界大小";
            case "motd" -> "服务器介绍";
            case "network-compression-threshold" -> "网络压缩阈值";
            case "online-mode" -> "正版验证";
            case "op-permission-level" -> "OP权限等级";
            case "pause-when-empty-seconds" -> "空服暂停秒数";
            case "player-idle-timeout" -> "玩家空闲超时";
            case "prevent-proxy-connections" -> "阻止代理连接";
            case "query.port" -> "Query端口";
            case "rate-limit" -> "速率限制";
            case "rcon.password" -> "RCON密码";
            case "rcon.port" -> "RCON端口";
            case "region-file-compression" -> "区域文件压缩";
            case "require-resource-pack" -> "强制资源包";
            case "resource-pack" -> "资源包地址";
            case "resource-pack-id" -> "资源包ID";
            case "resource-pack-prompt" -> "资源包提示";
            case "resource-pack-sha1" -> "资源包SHA1";
            case "server-ip" -> "服务器IP";
            case "server-port" -> "服务器端口";
            case "simulation-distance" -> "模拟距离";
            case "spawn-protection" -> "出生点保护";
            case "status-heartbeat-interval" -> "状态心跳间隔";
            case "sync-chunk-writes" -> "同步区块写入";
            case "text-filtering-config" -> "文本过滤配置";
            case "text-filtering-version" -> "文本过滤版本";
            case "use-native-transport" -> "使用原生传输";
            case "view-distance" -> "视距";
            case "white-list" -> "白名单";
            default -> key;
        };
    }

    public static String getDescription(String key) {
        return switch (key) {
            case "accepts-transfers" -> "允许从其他服务器转移玩家，需要配合BungeeCord等代理使用";
            case "allow-flight" -> "允许玩家使用飞行模组，建议开启避免误判正常玩家";
            case "broadcast-console-to-ops" -> "将控制台输出实时广播给所有OP玩家";
            case "broadcast-rcon-to-ops" -> "将RCON命令输出广播给OP玩家";
            case "bug-report-link" -> "崩溃时显示给玩家的错误报告页面链接";
            case "debug" -> "显示详细调试信息，仅排查问题时开启";
            case "difficulty" -> "peaceful=无怪物 easy=简单 normal=普通 hard=困难";
            case "enable-code-of-conduct" -> "显示服务器行为准则提示";
            case "enable-jmx-monitoring" -> "允许通过JMX监控服务器性能";
            case "enable-query" -> "允许外部工具查询服务器信息";
            case "enable-rcon" -> "开启远程控制台功能，建议设置强密码";
            case "enable-status" -> "关闭后服务器不会显示在服务器列表中";
            case "enforce-secure-profile" -> "要求玩家必须有安全的微软账户档案";
            case "enforce-whitelist" -> "开启后只有白名单内的玩家可以加入";
            case "entity-broadcast-range-percentage" -> "值越小性能越好，推荐：50-100";
            case "force-gamemode" -> "玩家加入时强制切换到默认游戏模式";
            case "function-permission-level" -> "1=普通玩家 2=OP 3=高级OP 4=控制台";
            case "gamemode" -> "survival=生存 creative=创造 adventure=冒险 spectator=旁观";
            case "generate-structures" -> "关闭可提高性能但会减少游戏内容";
            case "generator-settings" -> "用于超平坦世界等高级配置";
            case "hardcore" -> "死亡后无法复活，世界会被锁定";
            case "hide-online-players" -> "不在服务器列表显示在线玩家数量";
            case "initial-disabled-packs" -> "服务器启动时禁用的数据包";
            case "initial-enabled-packs" -> "服务器启动时启用的数据包";
            case "level-name" -> "世界文件夹的名称";
            case "level-seed" -> "相同的种子生成相同的世界，留空为随机";
            case "level-type" -> "normal=普通 flat=超平坦 large_biomes=巨型 amplified=放大化";
            case "log-ips" -> "开启有助于排查问题但会占用更多空间";
            case "max-chained-neighbor-updates" -> "防止红石机器导致崩溃，推荐：1000000";
            case "max-players" -> "根据服务器配置调整，推荐：10-100";
            case "max-tick-time" -> "超时会被认为是崩溃，推荐：60000";
            case "max-world-size" -> "默认29999984约等于无限大";
            case "motd" -> "支持颜色代码（&a, &b等）";
            case "network-compression-threshold" -> "256是推荐值，-1禁用压缩";
            case "online-mode" -> "关闭后盗版玩家可进入，但会降低安全性";
            case "op-permission-level" -> "1=最低 4=最高，推荐：4";
            case "pause-when-empty-seconds" -> "-1=不暂停 0=立即暂停，推荐：-1";
            case "player-idle-timeout" -> "0=不踢，推荐：30或0";
            case "prevent-proxy-connections" -> "可提高安全性但可能误杀正常玩家";
            case "query.port" -> "通常与服务器端口相同";
            case "rate-limit" -> "可防止攻击，推荐：0或2";
            case "rcon.password" -> "建议设置强密码并保密";
            case "rcon.port" -> "默认25575";
            case "region-file-compression" -> "deflate=标准压缩 lz4=快速压缩 none=不压缩";
            case "require-resource-pack" -> "拒绝则无法进入服务器";
            case "resource-pack" -> "玩家加入时会自动下载";
            case "resource-pack-id" -> "用于版本控制";
            case "resource-pack-prompt" -> "提示玩家下载资源包的文本";
            case "resource-pack-sha1" -> "用于验证资源包完整性";
            case "server-ip" -> "留空表示监听所有网络接口";
            case "server-port" -> "默认25565，修改后需用IP:端口连接";
            case "simulation-distance" -> "越小性能越好，推荐：6-10";
            case "spawn-protection" -> "0=关闭保护";
            case "status-heartbeat-interval" -> "0=不发送，用于服务器监控";
            case "sync-chunk-writes" -> "开启更安全但较慢";
            case "text-filtering-config" -> "用于过滤聊天内容";
            case "text-filtering-version" -> "文本过滤API版本";
            case "use-native-transport" -> "Linux上更快更稳定";
            case "view-distance" -> "3-32，越小性能越好，推荐：8-12";
            case "white-list" -> "开启后只有白名单玩家可以加入";
            default -> "";
        };
    }

    public static boolean isBooleanOption(String key) {
        return switch (key) {
            case "accepts-transfers", "allow-flight", "broadcast-console-to-ops",
                 "broadcast-rcon-to-ops", "debug", "enable-code-of-conduct",
                 "enable-jmx-monitoring", "enable-query", "enable-rcon",
                 "enable-status", "enforce-secure-profile", "enforce-whitelist",
                 "force-gamemode", "generate-structures", "hardcore",
                 "hide-online-players", "log-ips", "online-mode",
                 "prevent-proxy-connections", "require-resource-pack",
                 "sync-chunk-writes", "use-native-transport", "white-list" -> true;
            default -> false;
        };
    }

    public static List<String> getEnumOptions(String key) {
        return switch (key) {
            case "difficulty" -> List.of("peaceful (和平-无怪物)", "easy (简单)", "normal (普通)", "hard (困难)");
            case "gamemode" -> List.of("survival (生存模式)", "creative (创造模式)", "adventure (冒险模式)", "spectator (旁观模式)");
            case "level-type" -> List.of("minecraft:normal (普通世界)", "minecraft:flat (超平坦)", "minecraft:large_biomes (巨型生物群系)", "minecraft:amplified (放大化)");
            case "region-file-compression" -> List.of("deflate (标准压缩-推荐)", "lz4 (快速压缩)", "none (不压缩)");
            case "op-permission-level" -> List.of("1 (基础OP)", "2 (普通OP)", "3 (高级OP)", "4 (完全权限-推荐)");
            case "max-players" -> List.of("10 (小型服)", "20 (中型服-推荐)", "50 (大型服)", "100 (超大型服)", "200 (巨型服)");
            case "view-distance" -> List.of("6 (低配置)", "8 (中配置)", "10 (推荐)", "12 (高配置)", "16 (超高配置)", "32 (极限)");
            case "simulation-distance" -> List.of("4 (低配置)", "6 (中配置)", "8 (推荐)", "10 (高配置)", "12 (超高配置)");
            case "network-compression-threshold" -> List.of("-1 (禁用压缩)", "0 (全部压缩)", "64 (低)", "128 (中)", "256 (推荐)", "512 (高)");
            case "player-idle-timeout" -> List.of("0 (不踢)", "10 (10分钟)", "30 (30分钟-推荐)", "60 (1小时)");
            case "spawn-protection" -> List.of("0 (关闭保护)", "10 (小范围)", "16 (默认)", "32 (大范围)", "64 (超大范围)");
            case "pause-when-empty-seconds" -> List.of("-1 (不暂停-推荐)", "0 (立即暂停)", "60 (1分钟)", "300 (5分钟)", "600 (10分钟)");
            default -> null;
        };
    }

    public static boolean supportsCustomValue(String key) {
        return switch (key) {
            case "max-players", "view-distance", "simulation-distance",
                 "network-compression-threshold", "player-idle-timeout",
                 "spawn-protection", "pause-when-empty-seconds",
                 "op-permission-level" -> true;
            default -> false;
        };
    }

    public static String getValueFromDisplay(String key, String display) {
        if (display == null) return "";
        int paren = display.indexOf('(');
        if (paren > 0) {
            return display.substring(0, paren).trim();
        }
        return display;
    }

    public static boolean shouldShowOption(String key, String value) {
        return true;
    }

    public static int getPriority(String key) {
        return switch (key) {
            case "server-port", "server-ip" -> 1;
            case "online-mode" -> 2;
            case "max-players" -> 3;
            case "motd" -> 4;
            case "gamemode", "difficulty" -> 5;
            case "white-list", "enforce-whitelist" -> 6;
            case "view-distance", "simulation-distance" -> 7;
            case "allow-flight" -> 8;
            case "spawn-protection" -> 9;
            case "pvp" -> 10;
            case "hardcore" -> 11;
            case "generate-structures" -> 12;
            case "level-name", "level-seed", "level-type" -> 13;
            case "enable-rcon", "rcon.port", "rcon.password" -> 14;
            case "enable-query", "query.port" -> 15;
            case "enable-status" -> 16;
            case "resource-pack", "require-resource-pack" -> 17;
            case "player-idle-timeout" -> 18;
            case "op-permission-level" -> 19;
            case "max-world-size" -> 20;
            default -> 100;
        };
    }
}
