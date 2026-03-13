import java.util.*;

public class JsonUtils {
    private static final int MAX_DEPTH = 100;
    private static final ThreadLocal<Integer> currentDepth = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Set<Object>> serializationStack = ThreadLocal.withInitial(HashSet::new);
    
    public static boolean isValidObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        json = json.trim();
        return json.startsWith("{") && json.endsWith("}");
    }
    
    public static boolean isValidArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        json = json.trim();
        return json.startsWith("[") && json.endsWith("]");
    }
    
    public static boolean isValidJson(String json) {
        return isValidObject(json) || isValidArray(json);
    }
    
    public static Map<String, Object> parseObject(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!isValidObject(json)) {
            return result;
        }
        
        json = json.trim();
        json = json.substring(1, json.length() - 1).trim();
        
        if (json.isEmpty()) {
            return result;
        }
        
        List<String> tokens = tokenize(json);
        
        for (int i = 0; i < tokens.size(); i += 2) {
            if (i + 1 >= tokens.size()) break;
            
            String key = unquote(tokens.get(i).trim());
            Object value = parseValue(tokens.get(i + 1).trim());
            result.put(key, value);
        }
        
        return result;
    }
    
    public static Map<String, Object> parseObject(String json, Map<String, Object> defaultValue) {
        try {
            Map<String, Object> result = parseObject(json);
            return result.isEmpty() && !isValidObject(json) ? defaultValue : result;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    public static List<Object> parseArray(String json) {
        List<Object> result = new ArrayList<>();
        if (!isValidArray(json)) {
            return result;
        }
        
        json = json.trim();
        json = json.substring(1, json.length() - 1).trim();
        
        if (json.isEmpty()) {
            return result;
        }
        
        List<String> tokens = splitArray(json);
        
        for (String token : tokens) {
            result.add(parseValue(token.trim()));
        }
        
        return result;
    }
    
    public static List<Object> parseArray(String json, List<Object> defaultValue) {
        try {
            List<Object> result = parseArray(json);
            return result.isEmpty() && !isValidArray(json) ? defaultValue : result;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    public static Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        
        if (value.isEmpty()) {
            return "";
        }
        
        if (value.startsWith("{") && value.endsWith("}")) {
            return parseObject(value);
        } else if (value.startsWith("[") && value.endsWith("]")) {
            return parseArray(value);
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            return unescape(value.substring(1, value.length() - 1));
        } else if (value.equals("true")) {
            return true;
        } else if (value.equals("false")) {
            return false;
        } else if (value.equals("null")) {
            return null;
        } else {
            try {
                if (value.contains(".")) {
                    double d = Double.parseDouble(value);
                    if (Double.isInfinite(d) || Double.isNaN(d)) {
                        return value;
                    }
                    return d;
                } else {
                    long l = Long.parseLong(value);
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        return (int) l;
                    }
                    return l;
                }
            } catch (NumberFormatException e) {
                return value;
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    public static String toJson(Object obj) {
        int depth = currentDepth.get();
        if (depth >= MAX_DEPTH) {
            return "null";
        }
        
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        } else if (obj instanceof Map) {
            return mapToJson((Map<String, Object>) obj);
        } else if (obj instanceof List) {
            return listToJson((List<?>) obj);
        } else if (obj instanceof Collection) {
            return listToJson(new ArrayList<>((Collection<?>) obj));
        } else if (obj instanceof Object[]) {
            return listToJson(Arrays.asList((Object[]) obj));
        } else {
            return "\"" + escape(obj.toString()) + "\"";
        }
    }
    
    public static String mapToJson(Map<String, Object> map) {
        if (map == null) {
            return "{}";
        }
        
        Set<Object> stack = serializationStack.get();
        if (stack.contains(map)) {
            return "null";
        }
        
        int depth = currentDepth.get();
        if (depth >= MAX_DEPTH) {
            return "null";
        }
        
        stack.add(map);
        currentDepth.set(depth + 1);
        
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(entry.getKey())).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            
            sb.append("}");
            return sb.toString();
        } finally {
            stack.remove(map);
            currentDepth.set(depth);
        }
    }
    
    public static String listToJson(List<?> list) {
        if (list == null) {
            return "[]";
        }
        
        Set<Object> stack = serializationStack.get();
        if (stack.contains(list)) {
            return "null";
        }
        
        int depth = currentDepth.get();
        if (depth >= MAX_DEPTH) {
            return "null";
        }
        
        stack.add(list);
        currentDepth.set(depth + 1);
        
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            
            sb.append("]");
            return sb.toString();
        } finally {
            stack.remove(list);
            currentDepth.set(depth);
        }
    }
    
    public static boolean hasKey(Map<String, Object> map, String key) {
        return map != null && map.containsKey(key);
    }
    
    public static Object getNested(Map<String, Object> map, String path) {
        return getNested(map, path, null);
    }
    
    @SuppressWarnings("unchecked")
    public static Object getNested(Map<String, Object> map, String path, Object defaultValue) {
        if (map == null || path == null || path.isEmpty()) {
            return defaultValue;
        }
        
        String[] keys = path.split("\\.");
        Object current = map;
        
        for (String key : keys) {
            if (current == null) {
                return defaultValue;
            }
            
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return defaultValue;
            }
        }
        
        return current != null ? current : defaultValue;
    }
    
    public static String getNestedString(Map<String, Object> map, String path, String defaultValue) {
        Object value = getNested(map, path, defaultValue);
        return value instanceof String ? (String) value : defaultValue;
    }
    
    public static int getNestedInt(Map<String, Object> map, String path, int defaultValue) {
        Object value = getNested(map, path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public static boolean getNestedBoolean(Map<String, Object> map, String path, boolean defaultValue) {
        Object value = getNested(map, path, defaultValue);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }
    
    public static String optString(Map<String, Object> map, String key) {
        return getString(map, key, "");
    }
    
    public static String optString(Map<String, Object> map, String key, String defaultValue) {
        return getString(map, key, defaultValue);
    }
    
    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public static int optInt(Map<String, Object> map, String key) {
        return getInt(map, key, 0);
    }
    
    public static int optInt(Map<String, Object> map, String key, int defaultValue) {
        return getInt(map, key, defaultValue);
    }
    
    public static long getLong(Map<String, Object> map, String key, long defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public static long optLong(Map<String, Object> map, String key) {
        return getLong(map, key, 0L);
    }
    
    public static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public static double optDouble(Map<String, Object> map, String key) {
        return getDouble(map, key, 0.0);
    }
    
    public static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    public static boolean optBoolean(Map<String, Object> map, String key) {
        return getBoolean(map, key, false);
    }
    
    public static boolean optBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        return getBoolean(map, key, defaultValue);
    }
    
    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return new ArrayList<>();
        }
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return new ArrayList<>();
    }
    
    @SuppressWarnings("unchecked")
    public static List<Object> optList(Map<String, Object> map, String key) {
        return getList(map, key);
    }
    
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return new LinkedHashMap<>();
        }
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }
    
    @SuppressWarnings("unchecked")
    public static Map<String, Object> optMap(Map<String, Object> map, String key) {
        return getMap(map, key);
    }
    
    private static List<String> tokenize(String json) {
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
    
    private static List<String> splitArray(String json) {
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
    
    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
    
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '/': sb.append('/'); i++; break;
                    case 'b': sb.append('\b'); i++; break;
                    case 'f': sb.append('\f'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                String hex = s.substring(i + 2, i + 6);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
