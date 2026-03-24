import javafx.application.Platform;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public class ServerCore {
    public enum ServerState {
        STOPPED, STARTING, RUNNING, STOPPING
    }
    
    private String id;
    private String name;
    private String jarPath;
    private String minRam;
    private String maxRam;
    private String javaPath;
    private String workingDir;
    private String extraArgs;
    private boolean autoMemory;
    private int logSizeLimit = -1;
    private int logDisplayLines = 500;
    private String detectedVersion;
    private int order;
    private boolean starred;
    private long processPid = -1;

    private transient Process process;
    private transient Thread outputThread;
    private transient Thread errorThread;
    private transient BiConsumer<String, String> logCallback;
    private transient ServerState currentState = ServerState.STOPPED;
    private transient Runnable stateChangeCallback;
    private transient Runnable saveCallback;
    private transient BufferedWriter logFileWriter;
    private transient boolean logFileInitialized = false;
    private transient int logLineCounter = 0;
    private transient ScheduledExecutorService processMonitor;
    private transient boolean isReattached = false;
    
    private transient final Object commandResponseLock = new Object();
    private transient String lastCommandResponse = null;
    private transient boolean waitingForResponse = false;
    private transient String pendingCommand = null;
    private transient String pendingVersion = null;
    
    private static final ExecutorService threadPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    
    public ServerCore(String name, String jarPath, String minRam, String maxRam, String javaPath, String extraArgs, boolean autoMemory) {
        this.id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.name = name;
        this.jarPath = jarPath;
        this.autoMemory = autoMemory;
        if (autoMemory) {
            this.minRam = "";
            this.maxRam = "";
        } else {
            this.minRam = minRam != null && !minRam.isEmpty() ? minRam : "1G";
            this.maxRam = maxRam != null && !maxRam.isEmpty() ? maxRam : "2G";
        }
        this.javaPath = javaPath != null ? javaPath : "java";
        this.extraArgs = extraArgs != null ? extraArgs : "";
        File jar = new File(jarPath);
        this.workingDir = jar.getParent() != null ? jar.getParent() : ".";
    }
    
    public ServerCore(String id, String name, String jarPath, String minRam, String maxRam, String javaPath, String extraArgs, boolean autoMemory) {
        this.id = id;
        this.name = name;
        this.jarPath = jarPath;
        this.minRam = minRam;
        this.maxRam = maxRam;
        this.javaPath = javaPath != null ? javaPath : "java";
        this.extraArgs = extraArgs != null ? extraArgs : "";
        this.autoMemory = autoMemory;
        File jar = new File(jarPath);
        this.workingDir = jar.getParent() != null ? jar.getParent() : ".";
    }
    
    public String getName() { return name; }
    public String getJarPath() { return jarPath; }
    public String getRam() { return maxRam; }
    public String getMinRam() { return minRam; }
    public String getMaxRam() { return maxRam; }
    public String getJavaPath() { return javaPath; }
    public String getWorkingDir() { return workingDir; }
    
    public void setName(String name) { this.name = name; }
    public void setRam(String ram) { this.minRam = ram; this.maxRam = ram; }
    public void setMinRam(String ram) { this.minRam = ram; }
    public void setMaxRam(String ram) { this.maxRam = ram; }
    public void setJavaPath(String path) { this.javaPath = path; }
    
    public String getDetectedVersion() { return detectedVersion; }
    public void setDetectedVersion(String version) { this.detectedVersion = version; }
    
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    
    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
    public long getProcessPid() { return processPid; }
    public void setProcessPid(long pid) { this.processPid = pid; }
    
    public void setLogCallback(BiConsumer<String, String> callback) {
        this.logCallback = callback;
    }
    
    public void setStateChangeCallback(Runnable callback) {
        this.stateChangeCallback = callback;
    }
    
    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }
    
    public ServerState getState() {
        return currentState;
    }
    
    public boolean isRunning() {
        return process != null && process.isAlive();
    }
    
    private void setState(ServerState state) {
        if (currentState != state) {
            currentState = state;
            if (stateChangeCallback != null) {
                Platform.runLater(stateChangeCallback);
            }
        }
    }
    
    public void start() {
        if (isRunning()) {
            log("服务器已经在运行中", "WARN");
            return;
        }
        
        try {
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                log("JAR文件不存在: " + jarPath, "ERROR");
                return;
            }
            
            File workDir = new File(workingDir);
            if (!workDir.exists()) {
                workDir.mkdirs();
            }
            
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(javaPath);
            if (!autoMemory) {
                command.add("-Xms" + minRam);
                command.add("-Xmx" + maxRam);
            }
            command.add("-jar");
            command.add(jarPath);
            
            if (!extraArgs.isEmpty()) {
                for (String arg : extraArgs.split(" ")) {
                    if (!arg.trim().isEmpty()) {
                        command.add(arg.trim());
                    }
                }
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(false);
            
            process = pb.start();
            processPid = process.pid();
            if (saveCallback != null) {
                saveCallback.run();
            }
            setState(ServerState.STARTING);
            
            outputThread = new Thread(() -> readStream(process.getInputStream(), "INFO"));
            errorThread = new Thread(() -> readStream(process.getErrorStream(), "ERROR"));
            outputThread.setDaemon(true);
            errorThread.setDaemon(true);
            outputThread.start();
            errorThread.start();

            threadPool.submit(() -> {
                try {
                    int exitCode = process.waitFor();
                    if (exitCode != 0 && currentState == ServerState.STARTING && !hasEulaIssue) {
                        checkEulaFile();
                    }
                    setState(ServerState.STOPPED);
                } catch (InterruptedException e) {
                    setState(ServerState.STOPPED);
                }
            });
            
            startProcessMonitor();
            
            log("服务器启动中...", "INFO");
            
        } catch (Exception e) {
            log("启动失败: " + e.getMessage(), "ERROR");
            setState(ServerState.STOPPED);
        }
    }
    
    public void stop() {
        if (!isRunning()) {
            return;
        }

        setState(ServerState.STOPPING);
        sendCommand("stop");
        log("正在关闭服务器，等待进程结束...", "INFO");

        threadPool.submit(() -> {
            try {
                boolean terminated = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                if (!terminated && process.isAlive()) {
                    log("正常关闭超时，执行强制终止", "WARN");
                    forceStop();
                } else {
                    cleanupAfterStop();
                    log("服务器已正常关闭", "INFO");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                forceStop();
            }
        });
    }
    
    public void forceStop() {
        setState(ServerState.STOPPING);
        log("正在强制终止服务器...", "WARN");

        long targetPid = -1;

        if (process != null && process.isAlive()) {
            targetPid = process.pid();
            process.destroyForcibly();

            boolean terminated = false;
            try {
                terminated = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!terminated && process.isAlive()) {
                log("进程未响应，尝试强制杀死...", "ERROR");
            } else {
                cleanupAfterStop();
                log("服务器已被强制终止", "WARN");
                return;
            }
        } else if (processPid > 0) {
            targetPid = processPid;
        }

        if (targetPid > 0) {
            try {
                killProcessByPid(targetPid);
                log("[msh] 已通过 PID 强制终止进程，等待确认...", "WARN");

                boolean terminated = waitForProcessTermination(targetPid, 5);
                if (terminated) {
                    log("[msh] 进程已确认终止", "INFO");
                } else {
                    log("[msh] 警告：进程可能仍在运行", "ERROR");
                }
            } catch (Exception e) {
                log("[msh] 强制杀死进程失败: " + e.getMessage(), "ERROR");
            }
        }

        cleanupAfterStop();
    }

    private boolean waitForProcessTermination(long pid, int timeoutSeconds) {
        for (int i = 0; i < timeoutSeconds * 2; i++) {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty() || !handle.get().isAlive()) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void killProcessByPid(long pid) {
        Process killProcess = null;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid));
            } else {
                pb = new ProcessBuilder("kill", "-9", String.valueOf(pid));
            }
            pb.inheritIO();
            killProcess = pb.start();
            killProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log("系统命令终止进程失败: " + e.getMessage(), "ERROR");
        } finally {
            if (killProcess != null && killProcess.isAlive()) {
                killProcess.destroyForcibly();
            }
        }
    }

    private void cleanupAfterStop() {
        stopProcessMonitor();
        
        if (outputThread != null && outputThread.isAlive()) {
            outputThread.interrupt();
        }
        if (errorThread != null && errorThread.isAlive()) {
            errorThread.interrupt();
        }

        closeLogFileWriter();

        process = null;
        outputThread = null;
        errorThread = null;
        
        if (processPid > 0) {
            processPid = -1;
            if (saveCallback != null) {
                saveCallback.run();
            }
        }

        setState(ServerState.STOPPED);
    }
    
    private void startProcessMonitor() {
        stopProcessMonitor();
        processMonitor = Executors.newSingleThreadScheduledExecutor();
        processMonitor.scheduleAtFixedRate(() -> {
            if (process != null && !process.isAlive()) {
                if (currentState == ServerState.RUNNING || currentState == ServerState.STARTING) {
                    log("检测到服务器进程已终止", "WARN");
                    cleanupAfterStop();
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private void stopProcessMonitor() {
        if (processMonitor != null && !processMonitor.isShutdown()) {
            processMonitor.shutdown();
            try {
                if (!processMonitor.awaitTermination(3, TimeUnit.SECONDS)) {
                    processMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                processMonitor.shutdownNow();
            }
            processMonitor = null;
        }
    }
    
    public boolean attachToProcess() {
        if (processPid <= 0) {
            return false;
        }
        
        Optional<ProcessHandle> handle = ProcessHandle.of(processPid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            processPid = -1;
            return false;
        }
        
        log("重新连接到已存在的服务器进程 (PID: " + processPid + ")", "INFO");
        log("注意：无法读取历史日志和发送指令，只能监控进程状态", "WARN");
        
        isReattached = true;
        setState(ServerState.RUNNING);
        
        startProcessMonitor();
        
        threadPool.submit(() -> {
            handle.get().onExit().thenRun(() -> {
                Platform.runLater(() -> {
                    log("服务器进程已结束", "INFO");
                    cleanupAfterStop();
                });
            });
        });
        
        return true;
    }
    
    public boolean isReattached() {
        return isReattached;
    }
    
    public boolean canAttach() {
        if (processPid <= 0) {
            return false;
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(processPid);
        return handle.isPresent() && handle.get().isAlive();
    }

    private void closeLogFileWriter() {
        if (logFileWriter != null) {
            try {
                logFileWriter.flush();
                logFileWriter.close();
            } catch (IOException e) {
                System.out.println("关闭日志文件失败: " + e.getMessage());
            }
            logFileWriter = null;
            logFileInitialized = false;
        }
    }
    
    public void sendCommand(String command) {
        if (isRunning()) {
            try {
                BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream())
                );
                writer.write(command);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                log("发送命令失败: " + e.getMessage(), "ERROR");
            }
        } else {
            log("服务器未运行，无法发送命令", "WARN");
        }
    }
    
    public String queryGameRuleValue(String ruleName) {
        if (!isRunning()) {
            return null;
        }
        
        synchronized (commandResponseLock) {
            waitingForResponse = true;
            pendingCommand = "/gamerule " + ruleName;
            lastCommandResponse = null;
        }
        
        sendCommand("/gamerule " + ruleName);
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long startTime = System.currentTimeMillis();
        long timeout = 3000;
        
        while (System.currentTimeMillis() - startTime < timeout) {
            synchronized (commandResponseLock) {
                if (lastCommandResponse != null) {
                    String response = lastCommandResponse;
                    waitingForResponse = false;
                    pendingCommand = null;
                    lastCommandResponse = null;
                    return parseGameRuleResponse(response, ruleName);
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        synchronized (commandResponseLock) {
            waitingForResponse = false;
            pendingCommand = null;
        }
        
        return null;
    }
    
    private String parseGameRuleResponse(String response, String ruleName) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        
        String lower = response.toLowerCase();
        String ruleLower = ruleName.toLowerCase();
        
        if (lower.contains(ruleLower)) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                ruleName + "\\s*=\\s*(.+)", 
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            if (lower.contains("true")) {
                return "true";
            } else if (lower.contains("false")) {
                return "false";
            }
            
            pattern = java.util.regex.Pattern.compile("\\d+");
            matcher = pattern.matcher(response);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        
        return null;
    }
    
    private void readStream(InputStream stream, String level) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                checkCommandResponse(line);
                checkStateFromLog(line);
                log(line, level);
                String translation = translateLog(line);
                if (translation != null) {
                    log("[msh] " + translation, level);
                }
            }
        } catch (Exception e) {
            log("读取输出流失败: " + e.getMessage(), "ERROR");
        }
    }
    
    private void checkCommandResponse(String line) {
        synchronized (commandResponseLock) {
            if (waitingForResponse && pendingCommand != null) {
                if (line.contains("[Server thread/INFO]") || 
                    line.contains("[Server thread/WARN]") ||
                    (!line.contains("[") && !line.contains("]"))) {
                    
                    if (!line.toLowerCase().contains("gamerule")) {
                        lastCommandResponse = line;
                    }
                }
            }
        }
    }
    
    private String translateLog(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        
        String lower = line.toLowerCase();
        
        if (lower.contains("you need to agree to the eula")) {
            return "您需要同意 EULA 协议才能运行服务器，请编辑 eula.txt 文件";
        }
        if (lower.contains("failed to load eula.txt")) {
            return "加载 EULA 文件失败";
        }
        if (lower.contains("starting minecraft server version")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Starting minecraft server version (\\S+)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                pendingVersion = matcher.group(1);
                return "正在启动 Minecraft 服务器，版本：" + pendingVersion;
            }
            return "正在启动 Minecraft 服务器...";
        }
        if (lower.contains("loading properties")) {
            return "正在加载服务器配置...";
        }
        if (lower.contains("default game type:")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Default game type: (\\S+)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String mode = matcher.group(1);
                String modeCN = mode;
                if (mode.equalsIgnoreCase("SURVIVAL")) modeCN = "生存模式";
                else if (mode.equalsIgnoreCase("CREATIVE")) modeCN = "创造模式";
                else if (mode.equalsIgnoreCase("ADVENTURE")) modeCN = "冒险模式";
                else if (mode.equalsIgnoreCase("SPECTATOR")) modeCN = "旁观模式";
                return "默认游戏模式：" + modeCN;
            }
        }
        if (lower.contains("generating keypair")) {
            return "正在生成密钥对...";
        }
        if (lower.contains("starting minecraft server on")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Starting Minecraft server on \\*:(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "正在启动服务器监听端口：" + matcher.group(1);
            }
            return "正在启动服务器监听...";
        }
        if (lower.contains("loaded") && lower.contains("recipes")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Loaded (\\d+) recipes");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "已加载 " + matcher.group(1) + " 个配方";
            }
        }
        if (lower.contains("loaded") && lower.contains("advancements")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Loaded (\\d+) advancements");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "已加载 " + matcher.group(1) + " 个进度";
            }
        }
        if (lower.contains("prepared spawn area in")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Prepared spawn area in (\\d+) ms");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "出生点区域准备完成，耗时 " + matcher.group(1) + " 毫秒";
            }
        }
        if (lower.contains("done preparing level")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Done preparing level [\"']([^\"']+)[\"'] \\(([^)]+)\\)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "世界 '" + matcher.group(1) + "' 准备完成，耗时 " + matcher.group(2);
            }
            pattern = java.util.regex.Pattern.compile("Done preparing level \"(\\w+)\" \\(([^)]+)\\)");
            matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "世界 '" + matcher.group(1) + "' 准备完成，耗时 " + matcher.group(2);
            }
        }
        if (lower.contains("running delayed init tasks")) {
            return "正在运行延迟初始化任务...";
        }
        if (lower.contains("saving worlds")) {
            return "正在保存世界数据...";
        }
        if (lower.contains("done") && lower.contains("!") && lower.contains("for help")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Done \\(([^)]+)\\)!");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "服务器启动完成！启动耗时 " + matcher.group(1);
            }
            return "服务器启动完成！";
        }
        if (lower.contains("preparing level")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Preparing level ['\"]([^'\"]+)['\"]");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "正在准备世界：" + matcher.group(1);
            }
            return "正在准备世界...";
        }
        if (lower.contains("preparing spawn area")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Preparing spawn area: (\\d+)%");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "正在准备出生点区域：" + matcher.group(1) + "%";
            }
            return "正在准备出生点区域...";
        }
        if (lower.contains("stopping server")) {
            return "正在停止服务器...";
        }
        if (lower.contains("saving chunks")) {
            return "正在保存区块数据...";
        }
        if (lower.contains("saving players")) {
            return "正在保存玩家数据...";
        }
        if (lower.contains("joined the game")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\S+) joined the game");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "玩家 " + matcher.group(1) + " 加入了游戏";
            }
        }
        if (lower.contains("left the game")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\S+) left the game");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "玩家 " + matcher.group(1) + " 离开了游戏";
            }
        }
        if (lower.contains("lost connection")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\S+) lost connection: (.+)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "玩家 " + matcher.group(1) + " 断开连接：" + matcher.group(2);
            }
        }
        if (lower.contains("can't keep up")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Can't keep up! Is the server overloaded\\? Running (\\d+)ms or (\\d+) ticks behind");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return "服务器跟不上！是否过载？落后 " + matcher.group(1) + " 毫秒 / " + matcher.group(2) + " 个刻";
            }
            return "服务器跟不上！可能过载";
        }
        
        return null;
    }
    
    private void checkStateFromLog(String line) {
        String lower = line.toLowerCase();
        
        if (currentState == ServerState.STARTING) {
            if (lower.contains("done") && lower.contains("!")) {
                if (pendingVersion != null) {
                    detectedVersion = pendingVersion;
                    pendingVersion = null;
                    if (saveCallback != null) {
                        saveCallback.run();
                    }
                }
                setState(ServerState.RUNNING);
            }
        } else if (currentState == ServerState.RUNNING) {
            if (lower.contains("stopping server")) {
                setState(ServerState.STOPPING);
            }
        }
        
        if (!hasEulaIssue) {
            if (lower.contains("you need to agree to the eula") ||
                (lower.contains("eula.txt") && lower.contains("false"))) {
                setEulaIssue(true);
            }
        }
    }
    
    private transient boolean hasEulaIssue = false;
    private transient Runnable eulaIssueCallback;
    
    public void setEulaIssueCallback(Runnable callback) {
        this.eulaIssueCallback = callback;
    }
    
    private void setEulaIssue(boolean hasIssue) {
        if (hasIssue && !hasEulaIssue && eulaIssueCallback != null) {
            javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
            delay.setOnFinished(e -> eulaIssueCallback.run());
            delay.play();
        }
        hasEulaIssue = hasIssue;
    }
    
    public boolean hasEulaIssue() {
        return hasEulaIssue;
    }
    
    private void checkEulaFile() {
        try {
            File eulaFile = new File(workingDir, "eula.txt");
            if (eulaFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(eulaFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.toLowerCase().contains("eula=false")) {
                            setEulaIssue(true);
                            return;
                        }
                    }
                }
            } else {
                setEulaIssue(true);
            }
        } catch (IOException e) {
            setEulaIssue(true);
        }
    }
    
    public boolean fixEula() {
        try {
            File eulaFile = new File(workingDir, "eula.txt");
            String content = "eula=true" + System.lineSeparator();
            try (FileWriter writer = new FileWriter(eulaFile)) {
                writer.write(content);
            }
            hasEulaIssue = false;
            return true;
        } catch (IOException e) {
            log("修改 EULA 失败: " + e.getMessage(), "ERROR");
            return false;
        }
    }
    
    private transient final Object logBufferLock = new Object();
    private transient final java.util.List<String> logBuffer = new java.util.ArrayList<>();
    private transient long lastLogFlush = 0;
    private static final long LOG_FLUSH_INTERVAL = 100;

    private void log(String message, String level) {
        if (logCallback != null) {
            synchronized (logBufferLock) {
                logBuffer.add("[" + level + "] " + message);
                long now = System.currentTimeMillis();
                if (now - lastLogFlush >= LOG_FLUSH_INTERVAL || logBuffer.size() >= 10) {
                    flushLogBuffer();
                    lastLogFlush = now;
                }
            }
        }
        writeLogToFile(message, level);
    }

    private void flushLogBuffer() {
        synchronized (logBufferLock) {
            if (logBuffer.isEmpty()) return;
            java.util.List<String> batch = new java.util.ArrayList<>(logBuffer);
            logBuffer.clear();
            Platform.runLater(() -> {
                for (String msg : batch) {
                    int bracketEnd = msg.indexOf("] ");
                    if (bracketEnd > 0) {
                        String level = msg.substring(1, bracketEnd);
                        String message = msg.substring(bracketEnd + 2);
                        logCallback.accept(message, level);
                    }
                }
            });
        }
    }
    
    private void writeLogToFile(String message, String level) {
        try {
            if (!logFileInitialized) {
                initLogFile();
            }
            if (logFileWriter != null) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                logFileWriter.write("[" + timestamp + "] [" + level + "] " + message);
                logFileWriter.newLine();
                logFileWriter.flush();
                logLineCounter++;
                if (logLineCounter >= 50) {
                    logLineCounter = 0;
                    enforceLogLinesLimit();
                }
            }
        } catch (IOException e) {
            System.out.println("写入日志文件失败: " + e.getMessage());
        }
    }
    
    public void enforceLogLinesLimit() {
        if (logDisplayLines <= 0) return;
        try {
            File logDir = new File("msh/logs");
            if (!logDir.exists()) return;
            File logFile = new File(logDir, id + ".log");
            if (!logFile.exists()) return;
            
            java.util.List<String> lines = new java.util.ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            
            if (lines.size() > logDisplayLines) {
                java.util.List<String> keepLines = lines.subList(lines.size() - logDisplayLines, lines.size());
                closeLogFileWriter();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, false))) {
                    for (String l : keepLines) {
                        writer.write(l);
                        writer.newLine();
                    }
                }
                initLogFile();
            }
        } catch (IOException e) {
            System.out.println("清理日志行数失败: " + e.getMessage());
        }
    }
    
    private void initLogFile() {
        try {
            File logDir = new File("msh/logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, id + ".log");
            logFileWriter = new BufferedWriter(new FileWriter(logFile, true));
            logFileInitialized = true;
        } catch (IOException e) {
            System.out.println("初始化日志文件失败: " + e.getMessage());
        }
    }
    
    public java.util.List<String> getLogHistory(int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            File logDir = new File("msh/logs");
            if (!logDir.exists()) {
                return lines;
            }
            File logFile = new File(logDir, id + ".log");
            if (!logFile.exists()) {
                return lines;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            if (lines.size() > maxLines) {
                return lines.subList(lines.size() - maxLines, lines.size());
            }
        } catch (IOException e) {
            System.out.println("读取日志历史失败: " + e.getMessage());
        }
        return lines;
    }

    public static java.util.List<File> getLogFiles(String serverId) {
        java.util.List<File> files = new java.util.ArrayList<>();
        File logDir = new File("msh/logs");
        if (!logDir.exists()) {
            return files;
        }
        File logFile = new File(logDir, serverId + ".log");
        if (logFile.exists()) {
            files.add(logFile);
        }
        return files;
    }

    public static boolean clearOldLogs(String serverId, int keepDays) {
        try {
            java.util.List<File> files = getLogFiles(serverId);
            long now = System.currentTimeMillis();
            long keepMillis = keepDays * 24 * 60 * 60 * 1000L;
            for (File file : files) {
                if (now - file.lastModified() > keepMillis) {
                    file.delete();
                }
            }
            return true;
        } catch (Exception e) {
            System.out.println("清理旧日志失败: " + e.getMessage());
            return false;
        }
    }

    public void setLogSizeLimit(int limitMB) {
        this.logSizeLimit = limitMB;
        if (limitMB > 0) {
            enforceLogSizeLimit(limitMB);
        }
    }

    public int getLogSizeLimit() {
        return logSizeLimit;
    }

    public void setLogDisplayLines(int lines) {
        this.logDisplayLines = lines;
    }

    public int getLogDisplayLines() {
        return logDisplayLines;
    }

    public void enforceLogSizeLimit(int limitMB) {
        try {
            File logDir = new File("msh/logs");
            if (!logDir.exists()) {
                return;
            }
            File logFile = new File(logDir, id + ".log");
            if (!logFile.exists()) {
                return;
            }

            long limitBytes = limitMB * 1024L * 1024L;

            if (logFile.length() <= limitBytes) {
                return;
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, false))) {
                writer.write("");
            }
        } catch (Exception e) {
            System.out.println("强制执行日志大小限制失败: " + e.getMessage());
        }
    }

    public void clearLogCache() {
        try {
            File logDir = new File("msh/logs");
            if (!logDir.exists()) {
                return;
            }
            File logFile = new File(logDir, id + ".log");
            if (logFile.exists()) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, false))) {
                    writer.write("");
                }
            }
        } catch (IOException e) {
            System.out.println("清空日志缓存失败: " + e.getMessage());
        }
    }

    public String getId() { return id; }
    public String getExtraArgs() { return extraArgs; }
    public boolean isAutoMemory() { return autoMemory; }
}
