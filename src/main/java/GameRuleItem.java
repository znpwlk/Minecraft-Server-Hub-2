import java.util.Map;

public class GameRuleItem {
    private String name;
    private String displayName;
    private String description;
    private String type;
    private Object defaultValue;
    private Integer minValue;
    private Integer maxValue;
    private Integer introducedVersion;
    private String commandTemplate;
    
    private Object currentValue;
    
    public GameRuleItem() {}
    
    public GameRuleItem(String name, String displayName, String description, String type, 
                        Object defaultValue, Integer minValue, Integer maxValue, 
                        Integer introducedVersion, String commandTemplate) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.introducedVersion = introducedVersion;
        this.commandTemplate = commandTemplate;
        this.currentValue = defaultValue;
    }
    
    public static GameRuleItem fromMap(Map<String, Object> map) {
        GameRuleItem item = new GameRuleItem();
        item.name = (String) map.get("name");
        item.displayName = (String) map.get("displayName");
        item.description = (String) map.get("description");
        item.type = (String) map.get("type");
        item.defaultValue = map.get("defaultValue");
        item.minValue = map.containsKey("minValue") ? ((Number) map.get("minValue")).intValue() : null;
        item.maxValue = map.containsKey("maxValue") ? ((Number) map.get("maxValue")).intValue() : null;
        item.introducedVersion = map.containsKey("introducedVersion") ? ((Number) map.get("introducedVersion")).intValue() : null;
        item.commandTemplate = (String) map.get("commandTemplate");
        item.currentValue = item.defaultValue;
        return item;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
    
    public Integer getMinValue() { return minValue; }
    public void setMinValue(Integer minValue) { this.minValue = minValue; }
    
    public Integer getMaxValue() { return maxValue; }
    public void setMaxValue(Integer maxValue) { this.maxValue = maxValue; }
    
    public Integer getIntroducedVersion() { return introducedVersion; }
    public void setIntroducedVersion(Integer introducedVersion) { this.introducedVersion = introducedVersion; }
    
    public String getCommandTemplate() { return commandTemplate; }
    public void setCommandTemplate(String commandTemplate) { this.commandTemplate = commandTemplate; }
    
    public Object getCurrentValue() { return currentValue; }
    public void setCurrentValue(Object currentValue) { this.currentValue = currentValue; }
    
    public boolean isBoolean() { return "boolean".equals(type); }
    public boolean isInteger() { return "integer".equals(type); }
    
    public boolean getBooleanValue() {
        if (currentValue instanceof Boolean) {
            return (Boolean) currentValue;
        }
        return Boolean.parseBoolean(String.valueOf(currentValue));
    }
    
    public int getIntegerValue() {
        if (currentValue instanceof Number) {
            return ((Number) currentValue).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(currentValue));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public String getCommand() {
        if (commandTemplate == null) return null;
        String valueStr = String.valueOf(currentValue).toLowerCase();
        return commandTemplate.replaceAll("true|false|\\d+$", valueStr);
    }
}
