import javafx.application.Platform;
import java.io.*;
import java.util.function.BiConsumer;

public class ServerInstance implements Serializable {
    private String name;
    private String jarPath;
    private String ram;
    private String javaPath;
    private transient Process process;
    private transient Thread outputThread;
    private transient Thread errorThread;
    private transient BiConsumer<String, String> logCallback;
    
    public ServerInstance(String name, String jarPath, String ram) {
        this.name = name;
        this.jarPath = jarPath;
        this.ram = ram;
        this.javaPath = "java";
    }
    
    public ServerInstance(String name, String jarPath, String ram, String javaPath) {
        this.name = name;
        this.jarPath = jarPath;
        this.ram = ram;
        this.javaPath = javaPath != null ? javaPath : "java";
    }
    
    public String getName() { return name; }
    public String getJarPath() { return jarPath; }
    public String getRam() { return ram; }
    public String getJavaPath() { return javaPath; }
    
    public void setName(String name) { this.name = name; }
    public void setRam(String ram) { this.ram = ram; }
    public void setJavaPath(String javaPath) { this.javaPath = javaPath; }
    
    public void setLogCallback(BiConsumer<String, String> callback) {
        this.logCallback = callback;
    }
    
    public boolean isRunning() {
        return process != null && process.isAlive();
    }
    
    public void start() {
        if (isRunning()) return;
        
        try {
            File jarFile = new File(jarPath);
            File workingDir = jarFile.getParentFile();
            
            ProcessBuilder pb = new ProcessBuilder(
                javaPath, "-Xms" + ram, "-Xmx" + ram, "-jar", jarPath, "nogui"
            );
            pb.directory(workingDir);
            pb.redirectErrorStream(false);
            
            process = pb.start();
            
            outputThread = new Thread(() -> readStream(process.getInputStream(), "INFO"));
            errorThread = new Thread(() -> readStream(process.getErrorStream(), "ERROR"));
            outputThread.setDaemon(true);
            errorThread.setDaemon(true);
            outputThread.start();
            errorThread.start();
            
        } catch (Exception e) {
            if (logCallback != null) {
                logCallback.accept("[错误] 启动失败: " + e.getMessage(), "ERROR");
            }
        }
    }
    
    public void stop() {
        if (process != null && process.isAlive()) {
            sendCommand("stop");
            try {
                process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
            }
        }
    }
    
    public void forceStop() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
    
    public void sendCommand(String command) {
        if (process != null && process.isAlive()) {
            try {
                OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
                writer.write(command + "\n");
                writer.flush();
            } catch (Exception e) {
                if (logCallback != null) {
                    logCallback.accept("[错误] 发送命令失败: " + e.getMessage(), "ERROR");
                }
            }
        }
    }
    
    private void readStream(InputStream stream, String level) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logCallback != null) {
                    String finalLine = line;
                    Platform.runLater(() -> logCallback.accept(finalLine, level));
                }
            }
        } catch (Exception e) {
            if (logCallback != null) {
                logCallback.accept("[错误] 读取输出失败: " + e.getMessage(), "ERROR");
            }
        }
    }
}
