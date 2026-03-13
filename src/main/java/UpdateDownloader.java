import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;

public class UpdateDownloader {

    private static final String UPDATE_MARKER_FILE = "msh/update_marker.txt";

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

    public interface DownloadCallback {
        void onProgress(int percentage);
        void onComplete(boolean success, String message);
    }

    public static void downloadUpdate(String downloadUrl, String expectedSha256, String newVersion, DownloadCallback callback) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try {
                String currentJarPath = getCurrentJarPath();
                if (currentJarPath == null) {
                    callback.onComplete(false, "无法获取当前程序路径");
                    return;
                }

                File currentJar = new File(currentJarPath);
                File parentDir = currentJar.getParentFile();

                if (parentDir == null || !parentDir.exists()) {
                    callback.onComplete(false, "无法获取程序所在目录");
                    return;
                }

                String newJarName = "msh-" + newVersion + ".jar";
                File newJar = new File(parentDir, newJarName);

                if (newJar.exists()) {
                    if (!newJar.delete()) {
                        callback.onComplete(false, "无法清理旧下载文件");
                        return;
                    }
                }

                callback.onProgress(5);

                URL url = new URI(downloadUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    callback.onComplete(false, "服务器返回错误: " + responseCode);
                    return;
                }

                int fileSize = conn.getContentLength();
                callback.onProgress(10);

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(newJar)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    int lastProgress = 10;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        if (fileSize > 0) {
                            int progress = 10 + (int) ((totalRead * 80) / fileSize);
                            if (progress > lastProgress) {
                                lastProgress = progress;
                                callback.onProgress(progress);
                            }
                        }
                    }
                }

                callback.onProgress(90);

                if (!newJar.exists() || newJar.length() == 0) {
                    callback.onComplete(false, "下载文件失败，文件为空");
                    return;
                }

                if (!expectedSha256.isEmpty()) {
                    String actualSha256 = calculateSha256(newJar);
                    if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                        newJar.delete();
                        callback.onComplete(false, "文件校验失败，请重新下载");
                        return;
                    }
                }

                callback.onProgress(95);

                if (!saveUpdateMarker(currentJarPath, newJar.getAbsolutePath())) {
                    newJar.delete();
                    callback.onComplete(false, "无法保存更新标记，更新取消");
                    return;
                }

                callback.onProgress(100);
                callback.onComplete(true, "下载完成，即将启动新版本");

                launchNewVersion(newJar.getAbsolutePath());

            } catch (Exception e) {
                e.printStackTrace();
                callback.onComplete(false, "下载失败: " + e.getMessage());
            }
            executor.shutdown();
        });
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

            try (FileWriter writer = new FileWriter(markerFile)) {
                writer.write(oldJarPath + "|" + newJarPath);
            }

            return markerFile.exists();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean deleteMarkerFile(File markerFile) {
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

    private static void launchNewVersion(String newJarPath) {
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

    private static String getCurrentJarPath() {
        try {
            return new File(UpdateDownloader.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
