import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class UpdateDownloader {

    private static final String UPDATE_MARKER_FILE = "msh/update_marker.txt";
    private static final String DOWNLOAD_PROGRESS_FILE = "msh/download_progress.txt";
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public enum DownloadError {
        NETWORK_ERROR,
        DISK_ERROR,
        CHECKSUM_ERROR,
        SERVER_ERROR,
        CANCELLED,
        UNKNOWN
    }

    public static class DeleteResult {
        public boolean success;
        public String message;
        public boolean hasMarkerFile;

        public DeleteResult(boolean success, String message, boolean hasMarkerFile) {
            this.success = success;
            this.message = message;
            this.hasMarkerFile = hasMarkerFile;
        }
    }

    public static class DownloadResult {
        public boolean success;
        public String message;
        public String newJarPath;
        public DownloadError error;

        public DownloadResult(boolean success, String message, String newJarPath, DownloadError error) {
            this.success = success;
            this.message = message;
            this.newJarPath = newJarPath;
            this.error = error;
        }

        public DownloadResult(boolean success, String message, String newJarPath) {
            this(success, message, newJarPath, success ? null : DownloadError.UNKNOWN);
        }
    }

    public interface DownloadCallback {
        void onProgress(int percentage, String speed);
        void onComplete(boolean success, String message);
        void onComplete(boolean success, String message, String newJarPath);
        default void onComplete(DownloadResult result) {
            onComplete(result.success, result.message, result.newJarPath);
        }
    }

    private static class DownloadProgress {
        String downloadUrl;
        String version;
        long downloadedBytes;
        long totalBytes;
        String tempFilePath;
        long lastModified;

        DownloadProgress(String downloadUrl, String version, long downloadedBytes, long totalBytes, String tempFilePath) {
            this.downloadUrl = downloadUrl;
            this.version = version;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.tempFilePath = tempFilePath;
            this.lastModified = System.currentTimeMillis();
        }

        boolean isValid(String currentUrl, String currentVersion) {
            return downloadUrl.equals(currentUrl) && version.equals(currentVersion);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastModified > 7 * 24 * 60 * 60 * 1000;
        }
    }

    private static class SpeedCalculator {
        private final List<Double> speedSamples = new ArrayList<>();
        private static final int MAX_SAMPLES = 5;

        void addSample(double speed) {
            speedSamples.add(speed);
            if (speedSamples.size() > MAX_SAMPLES) {
                speedSamples.remove(0);
            }
        }

        double getSmoothedSpeed() {
            if (speedSamples.isEmpty()) {
                return 0;
            }
            double sum = 0;
            for (double speed : speedSamples) {
                sum += speed;
            }
            return sum / speedSamples.size();
        }
    }

    public static void downloadUpdate(String downloadUrl, String expectedSha256, String newVersion, DownloadCallback callback) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            DownloadResult result = doDownload(downloadUrl, expectedSha256, newVersion, callback);
            callback.onComplete(result);
            executor.shutdown();
        });
    }

    private static DownloadResult doDownload(String downloadUrl, String expectedSha256, String newVersion, DownloadCallback callback) {
        String currentJarPath = getCurrentJarPath();
        if (currentJarPath == null) {
            return new DownloadResult(false, "无法获取当前程序路径", null, DownloadError.UNKNOWN);
        }

        File currentJar = new File(currentJarPath);
        File parentDir = currentJar.getParentFile();

        if (parentDir == null || !parentDir.exists()) {
            return new DownloadResult(false, "无法获取程序所在目录", null, DownloadError.DISK_ERROR);
        }

        String newJarName = "msh-" + newVersion + ".jar";
        File newJar = new File(parentDir, newJarName);
        File defaultTempJar = new File(parentDir, newJarName + ".tmp");

        if (newJar.exists()) {
            if (!newJar.delete()) {
                return new DownloadResult(false, "无法清理旧下载文件", null, DownloadError.DISK_ERROR);
            }
        }

        DownloadProgress progress = loadDownloadProgress(downloadUrl, newVersion);
        long existingSize = 0;
        File tempJar = defaultTempJar;

        if (progress != null && progress.isValid(downloadUrl, newVersion) && !progress.isExpired()) {
            File existingTemp = new File(progress.tempFilePath);
            if (existingTemp.exists() && existingTemp.length() == progress.downloadedBytes) {
                existingSize = progress.downloadedBytes;
                if (!defaultTempJar.equals(existingTemp)) {
                    tempJar = existingTemp;
                    if (defaultTempJar.exists() && !defaultTempJar.delete()) {
                        return new DownloadResult(false, "无法清理默认临时文件", null, DownloadError.DISK_ERROR);
                    }
                }
            } else {
                clearDownloadProgress();
                progress = null;
            }
        } else {
            clearDownloadProgress();
            progress = null;
        }

        if (progress == null && defaultTempJar.exists()) {
            if (!defaultTempJar.delete()) {
                return new DownloadResult(false, "无法清理临时文件", null, DownloadError.DISK_ERROR);
            }
        }

        callback.onProgress(5, "");

        HttpURLConnection conn = null;
        int retryCount = 0;
        long totalBytes = progress != null ? progress.totalBytes : -1;
        long downloadedBytes = existingSize;
        SpeedCalculator speedCalc = new SpeedCalculator();

        long initialDownloadedBytes = downloadedBytes;
        long lastSaveTime = System.currentTimeMillis();

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                URL url = new URI(downloadUrl).toURL();
                conn = createConnection(url);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);

                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

                if (downloadedBytes > 0) {
                    conn.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");
                }

                int responseCode = conn.getResponseCode();
                boolean isPartial = responseCode == 206;

                if (responseCode != 200 && responseCode != 206) {
                    if (progress != null && downloadedBytes > 0) {
                        progress.downloadedBytes = downloadedBytes;
                        progress.lastModified = System.currentTimeMillis();
                        saveDownloadProgress(progress);
                    }
                    return new DownloadResult(false, "服务器返回错误: " + responseCode, null, DownloadError.SERVER_ERROR);
                }

                if (downloadedBytes > 0 && !isPartial) {
                    if (tempJar.exists() && !tempJar.delete()) {
                        return new DownloadResult(false, "无法清理临时文件以重新开始下载", null, DownloadError.DISK_ERROR);
                    }
                    downloadedBytes = 0;
                    initialDownloadedBytes = 0;
                    speedCalc = new SpeedCalculator();
                    clearDownloadProgress();
                    retryCount++;
                    continue;
                }

                String contentType = conn.getContentType();
                if (contentType != null && !contentType.contains("application") && !contentType.contains("octet-stream") && !contentType.contains("jar")) {
                    if (progress != null && downloadedBytes > 0) {
                        progress.downloadedBytes = downloadedBytes;
                        progress.lastModified = System.currentTimeMillis();
                        saveDownloadProgress(progress);
                    }
                    return new DownloadResult(false, "服务器返回的文件类型不正确: " + contentType, null, DownloadError.SERVER_ERROR);
                }

                long contentLength = conn.getContentLengthLong();
                if (contentLength > 0) {
                    if (totalBytes <= 0) {
                        totalBytes = isPartial ? downloadedBytes + contentLength : contentLength;
                    }

                    long freeSpace = parentDir.getFreeSpace();
                    long requiredSpace = isPartial ? contentLength : totalBytes;
                    if (freeSpace < requiredSpace) {
                        if (progress != null && downloadedBytes > 0) {
                            progress.downloadedBytes = downloadedBytes;
                            progress.lastModified = System.currentTimeMillis();
                            saveDownloadProgress(progress);
                        }
                        return new DownloadResult(false, "磁盘空间不足，需要 " + formatBytes(requiredSpace) + "，可用 " + formatBytes(freeSpace), null, DownloadError.DISK_ERROR);
                    }
                }

                if (progress == null) {
                    progress = new DownloadProgress(downloadUrl, newVersion, downloadedBytes, totalBytes, tempJar.getAbsolutePath());
                } else if (totalBytes > 0 && progress.totalBytes <= 0) {
                    progress.totalBytes = totalBytes;
                }

                int startProgress = 10;
                if (totalBytes > 0 && downloadedBytes > 0) {
                    startProgress = 10 + (int) ((downloadedBytes * 80) / totalBytes);
                }
                callback.onProgress(startProgress, "");

                boolean append = isPartial && downloadedBytes > 0;
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempJar, append)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long lastTime = System.currentTimeMillis();
                    long lastRead = downloadedBytes;
                    int lastProgress = startProgress;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        long currentTime = System.currentTimeMillis();
                        long timeDiff = currentTime - lastTime;

                        if (timeDiff >= 500) {
                            long bytesDiff = downloadedBytes - lastRead;
                            double instantSpeed = (bytesDiff * 1000.0) / timeDiff;
                            speedCalc.addSample(instantSpeed);
                            double smoothedSpeed = speedCalc.getSmoothedSpeed();
                            String speedStr = formatSpeed(smoothedSpeed);

                            if (totalBytes > 0) {
                                int progressPercent = 10 + (int) ((downloadedBytes * 80) / totalBytes);
                                if (progressPercent > lastProgress) {
                                    lastProgress = progressPercent;
                                    callback.onProgress(progressPercent, speedStr);
                                }
                            }

                            if (currentTime - lastSaveTime >= 2000 && progress != null) {
                                progress.downloadedBytes = downloadedBytes;
                                progress.lastModified = System.currentTimeMillis();
                                saveDownloadProgress(progress);
                                lastSaveTime = currentTime;
                            }

                            lastTime = currentTime;
                            lastRead = downloadedBytes;
                        }
                    }

                    out.flush();
                }

                if (progress != null && downloadedBytes > initialDownloadedBytes) {
                    progress.downloadedBytes = downloadedBytes;
                    progress.lastModified = System.currentTimeMillis();
                    saveDownloadProgress(progress);
                }

                break;

            } catch (java.net.SocketTimeoutException e) {
                if (progress != null && downloadedBytes > initialDownloadedBytes) {
                    progress.downloadedBytes = downloadedBytes;
                    progress.lastModified = System.currentTimeMillis();
                    saveDownloadProgress(progress);
                }
                retryCount++;
                if (retryCount >= MAX_RETRY_COUNT) {
                    return new DownloadResult(false, "连接超时，已重试 " + MAX_RETRY_COUNT + " 次", null, DownloadError.NETWORK_ERROR);
                }
                lastSaveTime = System.currentTimeMillis();
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new DownloadResult(false, "下载被取消", null, DownloadError.CANCELLED);
                }
            } catch (java.net.UnknownHostException | java.net.ConnectException e) {
                if (progress != null && downloadedBytes > initialDownloadedBytes) {
                    progress.downloadedBytes = downloadedBytes;
                    progress.lastModified = System.currentTimeMillis();
                    saveDownloadProgress(progress);
                }
                return new DownloadResult(false, "网络连接失败: " + e.getMessage(), null, DownloadError.NETWORK_ERROR);
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("space")) {
                    if (progress != null && downloadedBytes > initialDownloadedBytes) {
                        progress.downloadedBytes = downloadedBytes;
                        progress.lastModified = System.currentTimeMillis();
                        saveDownloadProgress(progress);
                    }
                    return new DownloadResult(false, "磁盘空间不足", null, DownloadError.DISK_ERROR);
                }
                if (progress != null && downloadedBytes > initialDownloadedBytes) {
                    progress.downloadedBytes = downloadedBytes;
                    progress.lastModified = System.currentTimeMillis();
                    saveDownloadProgress(progress);
                }
                retryCount++;
                if (retryCount >= MAX_RETRY_COUNT) {
                    return new DownloadResult(false, "下载失败: " + e.getMessage(), null, DownloadError.NETWORK_ERROR);
                }
                lastSaveTime = System.currentTimeMillis();
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new DownloadResult(false, "下载被取消", null, DownloadError.CANCELLED);
                }
            } catch (Exception e) {
                if (progress != null && downloadedBytes > initialDownloadedBytes) {
                    progress.downloadedBytes = downloadedBytes;
                    progress.lastModified = System.currentTimeMillis();
                    saveDownloadProgress(progress);
                }
                return new DownloadResult(false, "下载失败: " + e.getMessage(), null, DownloadError.UNKNOWN);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        if (retryCount >= MAX_RETRY_COUNT) {
            return new DownloadResult(false, "下载失败，已重试 " + MAX_RETRY_COUNT + " 次", null, DownloadError.NETWORK_ERROR);
        }

        callback.onProgress(90, "");

        if (!tempJar.exists() || tempJar.length() == 0) {
            if (tempJar.exists()) {
                tempJar.delete();
            }
            clearDownloadProgress();
            return new DownloadResult(false, "下载文件失败，文件为空", null, DownloadError.NETWORK_ERROR);
        }

        long actualFileSize = tempJar.length();
        if (totalBytes > 0 && actualFileSize != totalBytes) {
            if (tempJar.exists()) {
                tempJar.delete();
            }
            clearDownloadProgress();
            return new DownloadResult(false, "文件大小不匹配，期望 " + formatBytes(totalBytes) + "，实际 " + formatBytes(actualFileSize), null, DownloadError.CHECKSUM_ERROR);
        }

        if (!expectedSha256.isEmpty()) {
            try {
                String actualSha256 = calculateSha256(tempJar);
                if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                    if (tempJar.exists()) {
                        tempJar.delete();
                    }
                    clearDownloadProgress();
                    return new DownloadResult(false, "文件校验失败，请重新下载", null, DownloadError.CHECKSUM_ERROR);
                }
            } catch (Exception e) {
                if (tempJar.exists()) {
                    tempJar.delete();
                }
                clearDownloadProgress();
                return new DownloadResult(false, "校验文件时出错: " + e.getMessage(), null, DownloadError.CHECKSUM_ERROR);
            }
        }

        if (!tempJar.renameTo(newJar)) {
            if (tempJar.exists()) {
                tempJar.delete();
            }
            clearDownloadProgress();
            return new DownloadResult(false, "无法完成文件写入", null, DownloadError.DISK_ERROR);
        }

        if (!newJar.canRead()) {
            if (newJar.exists()) {
                newJar.delete();
            }
            clearDownloadProgress();
            return new DownloadResult(false, "无法读取新文件", null, DownloadError.DISK_ERROR);
        }

        callback.onProgress(95, "");

        if (!saveUpdateMarker(currentJarPath, newJar.getAbsolutePath())) {
            if (newJar.exists()) {
                newJar.delete();
            }
            clearDownloadProgress();
            return new DownloadResult(false, "无法保存更新标记，更新取消", null, DownloadError.DISK_ERROR);
        }

        clearDownloadProgress();
        callback.onProgress(100, "");
        return new DownloadResult(true, "下载完成", newJar.getAbsolutePath());
    }

    private static void saveDownloadProgress(DownloadProgress progress) {
        try {
            File mshDir = new File("msh");
            if (!mshDir.exists()) {
                mshDir.mkdirs();
            }

            File tempFile = new File(DOWNLOAD_PROGRESS_FILE + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(progress.downloadUrl + "\n");
                writer.write(progress.version + "\n");
                writer.write(progress.downloadedBytes + "\n");
                writer.write(progress.totalBytes + "\n");
                writer.write(progress.tempFilePath + "\n");
                writer.write(progress.lastModified + "\n");
            }

            File targetFile = new File(DOWNLOAD_PROGRESS_FILE);
            if (targetFile.exists()) {
                targetFile.delete();
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete();
            }

        } catch (Exception e) {
            File tempFile = new File(DOWNLOAD_PROGRESS_FILE + ".tmp");
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private static DownloadProgress loadDownloadProgress(String url, String version) {
        File file = new File(DOWNLOAD_PROGRESS_FILE);
        if (!file.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String savedUrl = reader.readLine();
            String savedVersion = reader.readLine();
            String downloadedStr = reader.readLine();
            String totalStr = reader.readLine();
            String tempPath = reader.readLine();
            String modifiedStr = reader.readLine();

            if (savedUrl == null || savedVersion == null || downloadedStr == null ||
                totalStr == null || tempPath == null || modifiedStr == null ||
                savedUrl.isEmpty() || savedVersion.isEmpty() || tempPath.isEmpty()) {
                return null;
            }

            long downloadedBytes;
            long totalBytes;
            long lastModified;
            try {
                downloadedBytes = Long.parseLong(downloadedStr);
                totalBytes = Long.parseLong(totalStr);
                lastModified = Long.parseLong(modifiedStr);
            } catch (NumberFormatException e) {
                return null;
            }

            if (downloadedBytes < 0 || totalBytes < 0 || lastModified < 0) {
                return null;
            }

            DownloadProgress progress = new DownloadProgress(
                savedUrl,
                savedVersion,
                downloadedBytes,
                totalBytes,
                tempPath
            );
            progress.lastModified = lastModified;
            return progress;

        } catch (Exception e) {
            return null;
        }
    }

    private static void clearDownloadProgress() {
        File file = new File(DOWNLOAD_PROGRESS_FILE);
        File tempFile = new File(DOWNLOAD_PROGRESS_FILE + ".tmp");
        if (file.exists()) {
            file.delete();
        }
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    public static DeleteResult checkAndDeleteOldVersion() {
        File markerFile = new File(UPDATE_MARKER_FILE);

        if (!markerFile.exists()) {
            return new DeleteResult(true, "没有更新标记文件", false);
        }

        String content;
        try {
            content = Files.readString(markerFile.toPath()).trim();
        } catch (Exception e) {
            return new DeleteResult(false, "读取标记文件失败: " + e.getMessage(), true);
        }

        if (content.isEmpty()) {
            deleteMarkerFile(markerFile);
            return new DeleteResult(true, "标记文件为空，已清理", true);
        }

        String[] parts = content.split("\\|");
        if (parts.length < 2) {
            deleteMarkerFile(markerFile);
            return new DeleteResult(true, "标记文件格式错误，已清理", true);
        }

        String oldJarPath = parts[0].trim();
        String newJarPath = parts[1].trim();

        if (oldJarPath.isEmpty() || newJarPath.isEmpty()) {
            deleteMarkerFile(markerFile);
            return new DeleteResult(true, "标记文件路径为空，已清理", true);
        }

        String currentJarPath = getCurrentJarPath();
        if (currentJarPath == null) {
            return new DeleteResult(false, "无法获取当前程序路径", true);
        }

        if (oldJarPath.equals(currentJarPath)) {
            deleteMarkerFile(markerFile);
            return new DeleteResult(true, "旧版本路径与当前程序相同，无需删除", true);
        }

        if (!newJarPath.equals(currentJarPath)) {
            deleteMarkerFile(markerFile);
            return new DeleteResult(true, "当前程序不是新版本，标记已清理", true);
        }

        File oldJar = new File(oldJarPath);

        if (!oldJar.exists()) {
            deleteMarkerFile(markerFile);
            return new DeleteResult(true, "旧版本文件已不存在", true);
        }

        if (!oldJar.isFile()) {
            deleteMarkerFile(markerFile);
            return new DeleteResult(true, "旧版本路径不是文件", true);
        }

        if (!oldJar.canWrite()) {
            return new DeleteResult(false, "旧版本文件无法删除（无权限）: " + oldJarPath, true);
        }

        int retries = 0;
        boolean deleted = false;
        Exception lastException = null;

        while (!deleted && retries < 5) {
            try {
                System.gc();
                Thread.sleep(300);
                deleted = oldJar.delete();
                if (!deleted) {
                    retries++;
                }
            } catch (Exception e) {
                lastException = e;
                retries++;
            }
        }

        if (!deleted) {
            String errorMsg = "无法删除旧版本文件: " + oldJarPath;
            if (lastException != null) {
                errorMsg += " (" + lastException.getMessage() + ")";
            }
            return new DeleteResult(false, errorMsg, true);
        }

        boolean markerDeleted = deleteMarkerFile(markerFile);
        if (!markerDeleted) {
            return new DeleteResult(true, "旧版本已删除，但标记文件清理失败", true);
        }

        return new DeleteResult(true, "旧版本已删除，更新完成", true);
    }

    private static boolean saveUpdateMarker(String oldJarPath, String newJarPath) {
        try {
            File mshDir = new File("msh");
            if (!mshDir.exists()) {
                mshDir.mkdirs();
            }

            File markerFile = new File(UPDATE_MARKER_FILE);

            if (markerFile.exists() && !markerFile.canWrite()) {
                return false;
            }

            File tempFile = new File(UPDATE_MARKER_FILE + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(oldJarPath + "|" + newJarPath);
            }

            if (markerFile.exists() && !markerFile.delete()) {
                tempFile.delete();
                return false;
            }
            if (!tempFile.renameTo(markerFile)) {
                tempFile.delete();
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            File tempFile = new File(UPDATE_MARKER_FILE + ".tmp");
            if (tempFile.exists()) {
                tempFile.delete();
            }
            return false;
        }
    }

    private static boolean deleteMarkerFile(File markerFile) {
        File tempFile = new File(UPDATE_MARKER_FILE + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }

        if (!markerFile.exists()) {
            return true;
        }

        int retries = 0;
        while (markerFile.exists() && retries < 3) {
            try {
                return markerFile.delete();
            } catch (Exception e) {
                retries++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        }
        return !markerFile.exists();
    }

    public static void launchNewVersion(String newJarPath) {
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

            File newJar = new File(newJarPath);
            if (!newJar.exists()) {
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-jar",
                newJarPath
            );
            pb.inheritIO();
            pb.directory(newJar.getParentFile());
            pb.start();

            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String calculateSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static HttpURLConnection createConnection(URL url) throws IOException {
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

    private static String getCurrentJarPath() {
        try {
            return new File(UpdateDownloader.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return String.format("%.1f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024);
        } else {
            return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
        }
    }
}
