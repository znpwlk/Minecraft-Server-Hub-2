import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final String UPDATE_URL = "https://znpwlk.github.io/Minecraft-Server-Hub-2-API/update.json";
    private static final String CHANGELOG_URL = "https://znpwlk.github.io/Minecraft-Server-Hub-2-API/changelog.json";

    private String currentVersion;

    public UpdateChecker(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public enum UpdateStatus {
        NO_UPDATE,
        OPTIONAL_UPDATE,
        FORCE_UPDATE
    }

    public static class UpdateResult {
        public UpdateStatus status;
        public String latestVersion;
        public String downloadUrl;
        public String sha256;
        public String updateDate;
        public String changelog;

        public UpdateResult(UpdateStatus status) {
            this.status = status;
        }
    }

    public UpdateResult checkUpdate() {
        try {
            String jsonResponse = fetchFromServer(UPDATE_URL);

            String latestVersion = extractJsonValue(jsonResponse, "version");
            String forceUpdateVersion = extractJsonValue(jsonResponse, "forceUpdateVersion");
            String downloadUrl = extractJsonValue(jsonResponse, "downloadUrl");
            String sha256 = extractJsonValue(jsonResponse, "sha256");
            String updateDate = extractJsonValue(jsonResponse, "updateDate");

            int currentCompareLatest = compareVersion(currentVersion, latestVersion);
            int currentCompareForce = compareVersion(currentVersion, forceUpdateVersion);

            UpdateResult result;

            if (currentCompareForce < 0) {
                result = new UpdateResult(UpdateStatus.FORCE_UPDATE);
            } else if (currentCompareLatest != 0) {
                result = new UpdateResult(UpdateStatus.OPTIONAL_UPDATE);
            } else {
                result = new UpdateResult(UpdateStatus.NO_UPDATE);
            }

            result.latestVersion = latestVersion;
            result.downloadUrl = downloadUrl;
            result.sha256 = sha256;
            result.updateDate = updateDate;

            return result;

        } catch (Exception e) {
            return new UpdateResult(UpdateStatus.NO_UPDATE);
        }
    }

    public String fetchChangelog() {
        try {
            String jsonResponse = fetchFromServer(CHANGELOG_URL);
            return parseChangelogJson(jsonResponse);
        } catch (Exception e) {
            return "获取更新日志失败";
        }
    }

    private String parseChangelogJson(String json) {
        StringBuilder result = new StringBuilder();

        String itemPattern = "\\{([^}]+)\\}";
        Pattern ip = Pattern.compile(itemPattern, Pattern.DOTALL);
        Matcher im = ip.matcher(json);

        boolean first = true;
        while (im.find()) {
            String item = im.group(1);

            String version = extractJsonValue(item, "version");
            String date = extractJsonValue(item, "date");
            String changesStr = extractJsonArray(item, "changes");

            if (!version.isEmpty() && !date.isEmpty()) {
                if (!first) {
                    result.append("\n\n");
                }
                first = false;

                result.append("v").append(version).append(" - ").append(date).append("\n\n");

                String changePattern = "\"([^\"]+)\"";
                Pattern cp = Pattern.compile(changePattern);
                Matcher cm = cp.matcher(changesStr);

                while (cm.find()) {
                    result.append("• ").append(cm.group(1)).append("\n");
                }
            }
        }

        return result.length() > 0 ? result.toString() : "暂无更新日志";
    }

    private String extractJsonArray(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\\[(.*?)\\]";
        Pattern r = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private String fetchFromServer(String urlString) throws Exception {
        URL url = new URI(urlString).toURL();

        HttpURLConnection conn = createConnection(url);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private HttpURLConnection createConnection(URL url) throws IOException {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(url.toURI());
            for (Proxy proxy : proxies) {
                try {
                    return (HttpURLConnection) url.openConnection(proxy);
                } catch (IOException e) {
                }
            }
        } catch (Exception e) {
        }
        return (HttpURLConnection) url.openConnection();
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    public static int compareVersion(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (num1 < num2) {
                return -1;
            } else if (num1 > num2) {
                return 1;
            }
        }

        return 0;
    }

    public static void main(String[] args) {
        System.out.println("测试版本比较:");
        System.out.println("compareVersion(\"2.12\", \"2.13\") = " + compareVersion("2.12", "2.13"));
        System.out.println("compareVersion(\"2.12\", \"2.11\") = " + compareVersion("2.12", "2.11"));
        System.out.println("compareVersion(\"2.12\", \"3.12\") = " + compareVersion("2.12", "3.12"));
        System.out.println("compareVersion(\"2.12\", \"1.21\") = " + compareVersion("2.12", "1.21"));
        System.out.println("compareVersion(\"2.0.0\", \"2.0.0\") = " + compareVersion("2.0.0", "2.0.0"));
    }
}
