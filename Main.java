import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.Node;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;

public class Main extends Application {
    public static final String VERSION = "2.3";
    
    private Stage primaryStage;
    private StackPane rootContainer;
    private StackPane mainLayout;
    private Sidebar sidebar;
    private ContentPanel contentPanel;
    private ServerManager serverManager;
    private TrayIcon trayIcon;
    private ImageView bgView;
    private String backgroundImagePath = "";
    private String animationMode = "slide";
    private boolean autoCheckUpdate = true;
    private int notificationDuration = 5;

    private double xOffset = 0;
    private double yOffset = 0;
    
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.serverManager = new ServerManager();

        loadSavedServers();
        checkAndAttachRunningServers();

        if (autoCheckUpdate) {
            checkUpdateOnStartup(true);
        } else {
            checkUpdateOnStartup(false);
        }

        rootContainer = new StackPane();
        
        setupBackground();
        setupMainLayout();
        setupTitleBar();
        
        Scene scene = new Scene(rootContainer, 900, 600);
        scene.setFill(Color.TRANSPARENT);
        
        String modernScrollCss = """
            .scroll-pane {
                -fx-background-color: transparent;
                -fx-background: transparent;
            }
            .scroll-pane > .viewport {
                -fx-background-color: transparent;
            }
            .scroll-pane .scroll-bar:vertical {
                -fx-background-color: transparent;
                -fx-pref-width: 8;
                -fx-padding: 0 1 0 0;
            }
            .scroll-pane .scroll-bar:horizontal {
                -fx-background-color: transparent;
                -fx-pref-height: 8;
                -fx-padding: 0 0 1 0;
            }
            .scroll-pane .scroll-bar .track {
                -fx-background-color: rgba(255,255,255,0.08);
                -fx-background-radius: 3;
                -fx-border-radius: 3;
            }
            .scroll-pane .scroll-bar .thumb {
                -fx-background-color: rgba(255,255,255,0.35);
                -fx-background-radius: 3;
                -fx-border-radius: 3;
            }
            .scroll-pane .scroll-bar .thumb:hover {
                -fx-background-color: rgba(255,255,255,0.55);
            }
            .scroll-pane .scroll-bar .thumb:pressed {
                -fx-background-color: rgba(255,255,255,0.7);
            }
            .scroll-pane .scroll-bar .increment-button,
            .scroll-pane .scroll-bar .decrement-button {
                -fx-background-color: transparent;
                -fx-padding: 0;
                -fx-pref-height: 0;
                -fx-min-height: 0;
            }
            .scroll-pane .scroll-bar .increment-arrow,
            .scroll-pane .scroll-bar .decrement-arrow {
                -fx-background-color: transparent;
                -fx-shape: "";
                -fx-padding: 0;
            }
            .scroll-pane .corner {
                -fx-background-color: transparent;
            }
            """;
        scene.getStylesheets().add("data:text/css," + modernScrollCss.replace("\n", "").replace("  ", ""));
        
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setTitle("MSH2 - Minecraft Server Hub 2");
        stage.setResizable(false);
        
        stage.setOnCloseRequest(e -> {
            e.consume();
            handleWindowClose();
        });
        
        stage.show();
        
        fadeIn(mainLayout, 500);
    }
    
    private void setupBackground() {
        bgView = new ImageView();
        bgView.setFitWidth(900);
        bgView.setFitHeight(600);
        bgView.setPreserveRatio(false);

        loadBackgroundImage();
        loadDefaultJavaPath();

        rootContainer.getChildren().add(bgView);

        Pane overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(20, 20, 40, 0.6);");
        overlay.setPrefSize(900, 600);
        rootContainer.getChildren().add(overlay);
    }

    private void loadBackgroundImage() {
        try {
            File configFile = new File("msh/config.json");
            if (configFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                if (content.contains("\"backgroundImage\"")) {
                    int start = content.indexOf("\"backgroundImage\"") + 19;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        backgroundImagePath = content.substring(start, end);
                    }
                }
                if (content.contains("\"animationMode\"")) {
                    int start = content.indexOf("\"animationMode\"") + 17;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        animationMode = content.substring(start, end);
                    }
                }
                if (content.contains("\"autoCheckUpdate\"")) {
                    int start = content.indexOf("\"autoCheckUpdate\"") + 19;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        autoCheckUpdate = Boolean.parseBoolean(content.substring(start, end));
                    }
                }
                if (content.contains("\"notificationDuration\"")) {
                    int start = content.indexOf("\"notificationDuration\"") + 24;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        try {
                            notificationDuration = Integer.parseInt(content.substring(start, end));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("读取背景配置失败: " + e.getMessage());
        }

        try {
            if (!backgroundImagePath.isEmpty()) {
                File f = new File(backgroundImagePath);
                if (f.exists()) {
                    Image bgImage = new Image(f.toURI().toString());
                    bgView.setImage(bgImage);
                    return;
                }
            }

            File[] possibleFiles = {
                new File("image.png"),
                new File("background.png"),
                new File("bg.png")
            };
            for (File f : possibleFiles) {
                if (f.exists()) {
                    Image bgImage = new Image(f.toURI().toString());
                    bgView.setImage(bgImage);
                    return;
                }
            }

            InputStream defaultImageStream = getClass().getResourceAsStream("/image.png");
            if (defaultImageStream != null) {
                Image bgImage = new Image(defaultImageStream);
                bgView.setImage(bgImage);
            }
        } catch (Exception e) {
            System.out.println("背景图加载失败: " + e.getMessage());
        }
    }

    public String getBackgroundImagePath() {
        return backgroundImagePath;
    }

    public void setBackgroundImage(String path) {
        String oldPath = backgroundImagePath;

        if (path != null && !path.isEmpty()) {
            try {
                File sourceFile = new File(path);
                if (sourceFile.exists()) {
                    File bgDir = new File("msh/backgrounds");
                    bgDir.mkdirs();

                    String ext = "";
                    String name = sourceFile.getName();
                    int dotIndex = name.lastIndexOf('.');
                    if (dotIndex > 0) {
                        ext = name.substring(dotIndex);
                    }

                    String newFileName = System.currentTimeMillis() + ext;
                    File destFile = new File(bgDir, newFileName);

                    java.nio.file.Files.copy(
                        sourceFile.toPath(),
                        destFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );

                    backgroundImagePath = destFile.getAbsolutePath();

                    if (!oldPath.isEmpty() && !oldPath.equals(backgroundImagePath)) {
                        File oldFile = new File(oldPath);
                        if (oldFile.exists() && oldFile.getParentFile().getName().equals("backgrounds")) {
                            oldFile.delete();
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("复制背景图片失败: " + e.getMessage());
                return;
            }
        } else {
            backgroundImagePath = "";

            if (!oldPath.isEmpty()) {
                File oldFile = new File(oldPath);
                if (oldFile.exists() && oldFile.getParentFile().getName().equals("backgrounds")) {
                    oldFile.delete();
                }
            }
        }

        try {
            File configFile = new File("msh/config.json");
            String content = "{}";
            if (configFile.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            } else {
                configFile.getParentFile().mkdirs();
            }

            if (content.contains("\"backgroundImage\"")) {
                int start = content.indexOf("\"backgroundImage\"") + 19;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + backgroundImagePath + content.substring(end);
                } else {
                    content = content.replace("}", ",\"backgroundImage\":\"" + backgroundImagePath + "\"}");
                }
            } else {
                content = content.replace("}", ",\"backgroundImage\":\"" + backgroundImagePath + "\"}");
            }

            if (content.contains("\"animationMode\"")) {
                int start = content.indexOf("\"animationMode\"") + 17;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + animationMode + content.substring(end);
                } else {
                    content = content.replace("}", ",\"animationMode\":\"" + animationMode + "\"}");
                }
            } else {
                content = content.replace("}", ",\"animationMode\":\"" + animationMode + "\"}");
            }

            if (content.contains("\"autoCheckUpdate\"")) {
                int start = content.indexOf("\"autoCheckUpdate\"") + 19;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + autoCheckUpdate + content.substring(end);
                } else {
                    content = content.replace("}", ",\"autoCheckUpdate\":\"" + autoCheckUpdate + "\"}");
                }
            } else {
                content = content.replace("}", ",\"autoCheckUpdate\":\"" + autoCheckUpdate + "\"}");
            }

            if (content.contains("\"notificationDuration\"")) {
                int start = content.indexOf("\"notificationDuration\"") + 24;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + notificationDuration + content.substring(end);
                } else {
                    content = content.replace("}", ",\"notificationDuration\":\"" + notificationDuration + "\"}");
                }
            } else {
                content = content.replace("}", ",\"notificationDuration\":\"" + notificationDuration + "\"}");
            }

            java.nio.file.Files.write(configFile.toPath(), content.getBytes());
        } catch (Exception e) {
            System.out.println("保存背景配置失败: " + e.getMessage());
        }

        try {
            if (!backgroundImagePath.isEmpty()) {
                File f = new File(backgroundImagePath);
                if (f.exists()) {
                    Image bgImage = new Image(f.toURI().toString());
                    bgView.setImage(bgImage);
                }
            } else {
                bgView.setImage(null);
                File[] possibleFiles = {
                    new File("image.png"),
                    new File("background.png"),
                    new File("bg.png")
                };
                for (File f : possibleFiles) {
                    if (f.exists()) {
                        Image bgImage = new Image(f.toURI().toString());
                        bgView.setImage(bgImage);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("背景图加载失败: " + e.getMessage());
        }
    }

    public String getBackgroundImageName() {
        if (backgroundImagePath.isEmpty()) {
            return "";
        }
        File f = new File(backgroundImagePath);
        return f.getName();
    }

    public String getAnimationMode() {
        return animationMode;
    }

    public void setAnimationMode(String mode) {
        animationMode = mode != null ? mode : "slide";
        saveAnimationMode();
    }

    public boolean isAutoCheckUpdate() {
        return autoCheckUpdate;
    }

    public void setAutoCheckUpdate(boolean autoCheck) {
        autoCheckUpdate = autoCheck;
        saveAutoCheckUpdate();
    }

    public int getNotificationDuration() {
        return notificationDuration;
    }

    public void setNotificationDuration(int duration) {
        notificationDuration = Math.max(1, Math.min(30, duration));
        saveNotificationDuration();
    }

    private void saveNotificationDuration() {
        try {
            File configFile = new File("msh/config.json");
            String content = "{}";
            if (configFile.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            } else {
                configFile.getParentFile().mkdirs();
            }

            if (content.contains("\"notificationDuration\"")) {
                int start = content.indexOf("\"notificationDuration\"") + 24;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + notificationDuration + content.substring(end);
                } else {
                    content = content.replace("}", ",\"notificationDuration\":\"" + notificationDuration + "\"}");
                }
            } else {
                content = content.replace("}", ",\"notificationDuration\":\"" + notificationDuration + "\"}");
            }

            java.nio.file.Files.write(configFile.toPath(), content.getBytes());
        } catch (Exception e) {
            System.out.println("保存通知时长设置失败: " + e.getMessage());
        }
    }

    private void saveAutoCheckUpdate() {
        try {
            File configFile = new File("msh/config.json");
            String content = "{}";
            if (configFile.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            } else {
                configFile.getParentFile().mkdirs();
            }

            if (content.contains("\"autoCheckUpdate\"")) {
                int start = content.indexOf("\"autoCheckUpdate\"") + 19;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + autoCheckUpdate + content.substring(end);
                } else {
                    content = content.replace("}", ",\"autoCheckUpdate\":\"" + autoCheckUpdate + "\"}");
                }
            } else {
                content = content.replace("}", ",\"autoCheckUpdate\":\"" + autoCheckUpdate + "\"}");
            }

            java.nio.file.Files.write(configFile.toPath(), content.getBytes());
        } catch (Exception e) {
            System.out.println("保存自动检查更新配置失败: " + e.getMessage());
        }
    }
    
    private void saveAnimationMode() {
        try {
            File configFile = new File("msh/config.json");
            String content = "{}";
            if (configFile.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            } else {
                configFile.getParentFile().mkdirs();
            }

            if (content.contains("\"animationMode\"")) {
                int start = content.indexOf("\"animationMode\"") + 17;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + animationMode + content.substring(end);
                } else {
                    content = content.replace("}", ",\"animationMode\":\"" + animationMode + "\"}");
                }
            } else {
                content = content.replace("}", ",\"animationMode\":\"" + animationMode + "\"}");
            }

            java.nio.file.Files.write(configFile.toPath(), content.getBytes());
        } catch (Exception e) {
            System.out.println("保存动画配置失败: " + e.getMessage());
        }
    }

    private String defaultJavaPath = "";

    public String getDefaultJavaPath() {
        return defaultJavaPath;
    }

    public void setDefaultJavaPath(String path) {
        defaultJavaPath = path != null ? path : "";
        saveDefaultJavaPath();
    }

    private void loadDefaultJavaPath() {
        try {
            File configFile = new File("msh/config.json");
            if (configFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                if (content.contains("\"defaultJavaPath\"")) {
                    int start = content.indexOf("\"defaultJavaPath\"") + 19;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        defaultJavaPath = content.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("加载 Java 配置失败: " + e.getMessage());
        }
    }

    private void saveDefaultJavaPath() {
        try {
            File configFile = new File("msh/config.json");
            String content = "{}";
            if (configFile.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            } else {
                configFile.getParentFile().mkdirs();
            }

            if (content.contains("\"defaultJavaPath\"")) {
                int start = content.indexOf("\"defaultJavaPath\"") + 19;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + defaultJavaPath + content.substring(end);
                }
            } else {
                content = content.replace("}", ",\"defaultJavaPath\":\"" + defaultJavaPath + "\"}");
            }

            java.nio.file.Files.write(configFile.toPath(), content.getBytes());
        } catch (Exception e) {
            System.out.println("保存 Java 配置失败: " + e.getMessage());
        }
    }

    private void setupTitleBar() {
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_RIGHT);
        titleBar.setPadding(new Insets(0));
        titleBar.setPrefSize(900, 32);
        titleBar.setMaxSize(900, 32);
        titleBar.setStyle("-fx-background-color: rgba(30, 30, 50, 0.4);");
        
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        
        titleBar.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button minBtn = createWindowButton("—", "transparent", "rgba(255,255,255,0.15)");
        minBtn.setPrefSize(46, 32);
        minBtn.setOnAction(e -> minimizeToTray());

        Button closeBtn = createWindowButton("×", "transparent", "rgba(231,76,60,0.9)");
        closeBtn.setPrefSize(46, 32);
        closeBtn.setOnAction(e -> handleWindowClose());

        titleBar.getChildren().addAll(spacer, minBtn, closeBtn);

        rootContainer.getChildren().add(titleBar);
        StackPane.setAlignment(titleBar, Pos.TOP_CENTER);

        setupTrayIcon();
    }

    private void setupTrayIcon() {
        if (isLinux()) {
            return;
        }
        try {
            if (!SystemTray.isSupported()) {
                return;
            }

            Platform.setImplicitExit(false);

            SystemTray tray = SystemTray.getSystemTray();

            java.awt.PopupMenu popup = new java.awt.PopupMenu();

            java.awt.MenuItem showItem = new java.awt.MenuItem("Show");
            showItem.addActionListener(e -> Platform.runLater(() -> {
                primaryStage.show();
                primaryStage.toFront();
            }));

            java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");
            exitItem.addActionListener(e -> Platform.runLater(() -> {
                if (primaryStage.isShowing()) {
                    handleWindowClose();
                } else {
                    handleTrayExit();
                }
            }));

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            BufferedImage trayImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = trayImage.createGraphics();
            g2d.setColor(java.awt.Color.GREEN);
            g2d.fillRect(0, 0, 16, 16);
            g2d.dispose();

            trayIcon = new TrayIcon(trayImage, "MSH2", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        Platform.runLater(() -> {
                            primaryStage.show();
                            primaryStage.toFront();
                        });
                    }
                }
            });

            tray.add(trayIcon);
        } catch (Exception e) {
            System.out.println("系统托盘初始化失败: " + e.getMessage());
        }
    }

    private boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }

    private void handleTrayExit() {
        java.util.List<ServerCore> activeServers = new java.util.ArrayList<>();
        for (ServerCore server : serverManager.getServers()) {
            if (server.getState() != ServerCore.ServerState.STOPPED) {
                activeServers.add(server);
            }
        }

        if (activeServers.isEmpty()) {
            cleanupAndExit();
            return;
        }

        Platform.runLater(() -> {
            primaryStage.show();
            primaryStage.toFront();
            handleWindowClose();
        });
    }

    private void minimizeToTray() {
        primaryStage.setIconified(true);
    }
    
    private Button createWindowButton(String text, String normalColor, String hoverColor) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        btn.setTextFill(Color.WHITE);
        btn.setStyle(
            "-fx-background-color: " + normalColor + ";" +
            "-fx-background-radius: 0;" +
            "-fx-cursor: hand;"
        );

        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + hoverColor + ";" +
            "-fx-background-radius: 0;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + normalColor + ";" +
            "-fx-background-radius: 0;" +
            "-fx-cursor: hand;"
        ));

        return btn;
    }
    
    private void setupMainLayout() {
        mainLayout = new StackPane();
        mainLayout.setPrefSize(900, 600);
        mainLayout.setPadding(new Insets(36, 0, 0, 0));

        HBox layoutBox = new HBox();
        layoutBox.setPrefSize(900, 568);

        sidebar = new Sidebar(this);
        sidebar.setPrefWidth(180);
        sidebar.setMinWidth(180);
        sidebar.setMaxWidth(180);
        sidebar.setPrefHeight(568);

        contentPanel = new ContentPanel(this, serverManager);
        contentPanel.setPrefWidth(720);
        contentPanel.setPrefHeight(568);
        contentPanel.setRootContainer(rootContainer);
        contentPanel.setSidebar(sidebar);
        sidebar.setContentPanel(contentPanel);

        HBox.setHgrow(contentPanel, Priority.ALWAYS);
        layoutBox.getChildren().addAll(sidebar, contentPanel);

        mainLayout.getChildren().add(layoutBox);
        StackPane.setAlignment(layoutBox, Pos.CENTER);

        rootContainer.getChildren().add(mainLayout);

        setupNotificationLayer();
    }

    private VBox notificationContainer;

    private void setupNotificationLayer() {
        notificationContainer = new VBox(8);
        notificationContainer.setPadding(new Insets(40, 10, 0, 0));
        notificationContainer.setAlignment(Pos.TOP_RIGHT);
        notificationContainer.setMouseTransparent(false);
        notificationContainer.setPickOnBounds(false);

        rootContainer.getChildren().add(notificationContainer);
        StackPane.setAlignment(notificationContainer, Pos.TOP_RIGHT);
    }

    private void openUrl(String url) {
        try {
            if (isLinux()) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            } else {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception ex) {
            showNotification("无法打开链接: " + ex.getMessage(), "error");
        }
    }

    public void showNotification(String message, String type) {
        Platform.runLater(() -> {
            HBox notification = createNotification(message, type);
            notificationContainer.getChildren().add(0, notification);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), notification);
            slideIn.setFromY(-50);
            slideIn.setToY(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), notification);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            ParallelTransition enter = new ParallelTransition(slideIn, fadeIn);
            enter.play();

            PauseTransition delay = new PauseTransition(Duration.seconds(notificationDuration));
            delay.setOnFinished(e -> hideNotification(notification));
            delay.play();
        });
    }

    private HBox createNotification(String message, String type) {
        String bgColor = switch (type) {
            case "success" -> "rgba(76, 175, 80, 0.95)";
            case "error" -> "rgba(244, 67, 54, 0.95)";
            case "warning" -> "rgba(255, 152, 0, 0.95)";
            default -> "rgba(33, 150, 243, 0.95)";
        };

        HBox notification = new HBox(10);
        notification.setPadding(new Insets(12, 16, 12, 16));
        notification.setAlignment(Pos.CENTER_LEFT);
        notification.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 4);"
        );
        notification.setMaxWidth(300);
        notification.setOpacity(0);

        Label iconLabel = new Label(switch (type) {
            case "success" -> "✓";
            case "error" -> "✕";
            case "warning" -> "⚠";
            default -> "ℹ";
        });
        iconLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        iconLabel.setTextFill(Color.WHITE);

        Label msgLabel = new Label(message);
        msgLabel.setFont(Font.font("Microsoft YaHei", 13));
        msgLabel.setTextFill(Color.WHITE);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(240);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        closeBtn.setTextFill(Color.WHITE);
        closeBtn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.2);" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-min-width: 24;" +
            "-fx-min-height: 24;" +
            "-fx-padding: 0;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.4);" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-min-width: 24;" +
            "-fx-min-height: 24;" +
            "-fx-padding: 0;"
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.2);" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-min-width: 24;" +
            "-fx-min-height: 24;" +
            "-fx-padding: 0;"
        ));
        closeBtn.setOnAction(e -> hideNotification(notification));

        notification.getChildren().addAll(iconLabel, msgLabel, spacer, closeBtn);

        notification.setOnMouseEntered(e -> {
            notification.setStyle(
                "-fx-background-color: " + bgColor.replace("0.95", "1.0") + ";" +
                "-fx-background-radius: 4;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 6);"
            );
        });

        notification.setOnMouseExited(e -> {
            notification.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                "-fx-background-radius: 4;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 4);"
            );
        });

        setupNotificationDrag(notification, bgColor);

        return notification;
    }

    private void setupNotificationDrag(HBox notification, String bgColor) {
        final double[] dragStartX = new double[1];
        final double[] notificationStartX = new double[1];
        final boolean[] isDragging = new boolean[1];

        notification.setOnMousePressed(e -> {
            dragStartX[0] = e.getSceneX();
            notificationStartX[0] = notification.getTranslateX();
            isDragging[0] = false;
            e.consume();
        });

        notification.setOnMouseDragged(e -> {
            double offsetX = e.getSceneX() - dragStartX[0];

            if (Math.abs(offsetX) > 5) {
                isDragging[0] = true;
            }

            double newX = notificationStartX[0] + offsetX;
            newX = Math.max(-100, Math.min(newX, 200));
            notification.setTranslateX(newX);
            e.consume();
        });

        notification.setOnMouseReleased(e -> {
            if (!isDragging[0]) return;

            double currentX = notification.getTranslateX();

            if (currentX > 80) {
                hideNotificationToRight(notification);
            } else if (currentX < -30) {
                animateNotificationReset(notification, bgColor);
            } else {
                animateNotificationReset(notification, bgColor);
            }
        });
    }

    private void animateNotificationReset(HBox notification, String bgColor) {
        TranslateTransition reset = new TranslateTransition(Duration.millis(200), notification);
        reset.setToX(0);
        reset.setInterpolator(Interpolator.EASE_OUT);
        reset.play();
    }

    private void hideNotificationToRight(HBox notification) {
        if (!notificationContainer.getChildren().contains(notification)) {
            return;
        }

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(200), notification);
        slideOut.setToX(400);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), notification);
        fadeOut.setToValue(0);

        ParallelTransition exit = new ParallelTransition(slideOut, fadeOut);
        exit.setOnFinished(e -> notificationContainer.getChildren().remove(notification));
        exit.play();
    }

    private void hideNotification(HBox notification) {
        if (!notificationContainer.getChildren().contains(notification)) {
            return;
        }

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(250), notification);
        slideOut.setToY(-50);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), notification);
        fadeOut.setToValue(0);

        ParallelTransition exit = new ParallelTransition(slideOut, fadeOut);
        exit.setOnFinished(e -> notificationContainer.getChildren().remove(notification));
        exit.play();
    }
    
    private void fadeIn(Node node, double millis) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(millis), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void handleWindowClose() {
        java.util.List<ServerCore> activeServers = new java.util.ArrayList<>();
        for (ServerCore server : serverManager.getServers()) {
            if (server.getState() != ServerCore.ServerState.STOPPED) {
                activeServers.add(server);
            }
        }

        if (activeServers.isEmpty()) {
            cleanupAndExit();
            return;
        }

        showCloseConfirmDialog(activeServers);
    }

    private void showCloseConfirmDialog(java.util.List<ServerCore> activeServers) {
        ScrollPane serverListScroll = new ScrollPane();
        serverListScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        serverListScroll.setFitToWidth(true);
        serverListScroll.setMaxHeight(150);
        serverListScroll.setPrefWidth(350);

        VBox serverListBox = new VBox(8);
        serverListBox.setPadding(new Insets(10));
        serverListBox.setAlignment(Pos.CENTER_LEFT);

        boolean hasExternalServer = false;
        for (ServerCore server : activeServers) {
            String stateText = switch (server.getState()) {
                case STARTING -> "启动中";
                case RUNNING -> "运行中";
                case STOPPING -> "停止中";
                default -> "";
            };

            if (server.isReattached()) {
                stateText = "外部启动，只能强制关闭";
                hasExternalServer = true;
            }

            HBox serverRow = new HBox(8);
            serverRow.setAlignment(Pos.CENTER_LEFT);

            Label dotLabel = new Label("•");
            dotLabel.setFont(Font.font("Microsoft YaHei", 14));
            dotLabel.setTextFill(Color.web("#666666"));

            Label nameLabel = new Label(server.getName());
            nameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
            nameLabel.setTextFill(Color.web("#2196F3"));
            nameLabel.setCursor(javafx.scene.Cursor.HAND);
            nameLabel.setMaxWidth(200);
            nameLabel.setEllipsisString("...");

            Label stateLabel = new Label("(" + stateText + ")");
            stateLabel.setFont(Font.font("Microsoft YaHei", 12));
            stateLabel.setTextFill(server.isReattached() ? Color.web("#FF9800") : Color.web("#999999"));

            ServerCore targetServer = server;
            nameLabel.setOnMouseEntered(e -> nameLabel.setTextFill(Color.web("#64B5F6")));
            nameLabel.setOnMouseExited(e -> nameLabel.setTextFill(Color.web("#2196F3")));
            nameLabel.setOnMouseClicked(e -> {
                serverManager.setSelected(targetServer);
                contentPanel.showConsole();
            });

            serverRow.getChildren().addAll(dotLabel, nameLabel, stateLabel);
            serverListBox.getChildren().add(serverRow);
        }

        serverListScroll.setContent(serverListBox);

        String closeButtonText = hasExternalServer ? "强制关闭并退出" : "关闭服务器并退出";
        java.util.List<DialogButton> buttons;
        if (isLinux()) {
            buttons = java.util.Arrays.asList(
                new DialogButton(closeButtonText, "#f44336", "#d32f2f", "#FFFFFF", () -> {
                    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    });
                    executor.submit(() -> {
                        for (ServerCore server : activeServers) {
                            if (server.isReattached()) {
                                server.forceStop();
                            } else {
                                server.stop();
                            }
                        }
                        int waitCount = 0;
                        while (serverManager.getRunningCount() > 0 && waitCount < 30) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ignored) {}
                            waitCount++;
                        }
                        if (serverManager.getRunningCount() > 0) {
                            serverManager.forceStopAll();
                        }
                        Platform.runLater(this::cleanupAndExit);
                        executor.shutdown();
                    });
                }),
                new DialogButton("取消", "transparent", "#f0f0f0", "#666666", null)
            );
        } else {
            buttons = java.util.Arrays.asList(
                new DialogButton(closeButtonText, "#f44336", "#d32f2f", "#FFFFFF", () -> {
                    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    });
                    executor.submit(() -> {
                        for (ServerCore server : activeServers) {
                            if (server.isReattached()) {
                                server.forceStop();
                            } else {
                                server.stop();
                            }
                        }
                        int waitCount = 0;
                        while (serverManager.getRunningCount() > 0 && waitCount < 30) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ignored) {}
                            waitCount++;
                        }
                        if (serverManager.getRunningCount() > 0) {
                            serverManager.forceStopAll();
                        }
                        Platform.runLater(this::cleanupAndExit);
                        executor.shutdown();
                    });
                }),
                new DialogButton("最小化到托盘", "#e3f2fd", "#bbdefb", "#333333", this::minimizeToTray),
                new DialogButton("后台运行", "#f5f5f5", "#e0e0e0", "#333333", () -> primaryStage.hide()),
                new DialogButton("取消", "transparent", "#f0f0f0", "#666666", null)
            );
        }

        showGenericDialog("确认关闭", "以下服务器正在运行：", buttons, serverListScroll);
    }

    private void cleanupAndExit() {
        if (!isLinux() && trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception ignored) {}
        }
        Platform.exit();
        System.exit(0);
    }

    private void loadSavedServers() {
        java.util.List<ServerCore> servers = ServerDataStore.loadServers();
        for (ServerCore server : servers) {
            serverManager.addServer(server);
        }
    }
    
    public void saveServers() {
        ServerDataStore.saveServers(serverManager.getServers());
    }
    
    private void checkAndAttachRunningServers() {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            for (ServerCore server : serverManager.getServers()) {
                if (server.getProcessPid() <= 0) continue;

                if (!server.canAttach()) {
                    server.setProcessPid(-1);
                }
            }
            executor.shutdown();
        });
    }

    private void checkUpdateOnStartup(boolean showAllUpdates) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try {
                UpdateChecker checker = new UpdateChecker(VERSION);
                UpdateChecker.UpdateResult result = checker.checkUpdate();

                if (result.status == UpdateChecker.UpdateStatus.NO_UPDATE) {
                    executor.shutdown();
                    return;
                }

                if (!showAllUpdates && result.status != UpdateChecker.UpdateStatus.FORCE_UPDATE) {
                    executor.shutdown();
                    return;
                }

                Platform.runLater(() -> {
                    if (result.status == UpdateChecker.UpdateStatus.FORCE_UPDATE) {
                        showForceUpdateDialog(result);
                    } else {
                        showNormalUpdateDialog(result);
                    }
                });

            } catch (Exception e) {
            }
            executor.shutdown();
        });
    }

    public void showForceUpdateDialog(UpdateChecker.UpdateResult result) {
        final String[] newJarPath = new String[1];
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);");
        overlay.setPrefSize(900, 600);
        overlay.setAlignment(Pos.CENTER);

        VBox dialog = new VBox(12);
        dialog.setAlignment(Pos.CENTER);
        dialog.setPadding(new Insets(20, 30, 20, 30));
        dialog.setMaxWidth(420);
        dialog.setStyle(
            "-fx-background-color: #ffebee;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #f44336;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(244,67,54,0.4), 20, 0, 0, 3);"
        );

        javafx.scene.shape.SVGPath warningIcon = new javafx.scene.shape.SVGPath();
        warningIcon.setContent("M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z");
        warningIcon.setFill(Color.web("#d32f2f"));
        warningIcon.setScaleX(2);
        warningIcon.setScaleY(2);

        Label titleLabel = new Label("⚠ 紧急更新通知");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#c62828"));

        Label urgentLabel = new Label("您的版本存在严重问题，必须立即更新！");
        urgentLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        urgentLabel.setTextFill(Color.web("#d32f2f"));
        urgentLabel.setWrapText(true);
        urgentLabel.setAlignment(Pos.CENTER);

        VBox infoBox = new VBox(8);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setPadding(new Insets(10, 0, 10, 0));

        Label currentVerLabel = new Label("当前版本: " + VERSION);
        currentVerLabel.setFont(Font.font("Microsoft YaHei", 13));
        currentVerLabel.setTextFill(Color.web("#555"));

        Label newVerLabel = new Label("最新版本: " + result.latestVersion);
        newVerLabel.setFont(Font.font("Microsoft YaHei", 13));
        newVerLabel.setTextFill(Color.web("#d32f2f"));
        newVerLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        Label dateLabel = new Label("发布日期: " + result.updateDate);
        dateLabel.setFont(Font.font("Microsoft YaHei", 12));
        dateLabel.setTextFill(Color.web("#666"));

        infoBox.getChildren().addAll(currentVerLabel, newVerLabel, dateLabel);

        VBox changelogBox = new VBox(5);
        changelogBox.setAlignment(Pos.CENTER_LEFT);
        changelogBox.setPadding(new Insets(5, 0, 5, 0));
        changelogBox.setMaxHeight(100);
        changelogBox.setPrefHeight(100);

        Label changelogTitle = new Label("更新内容:");
        changelogTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        changelogTitle.setTextFill(Color.web("#666"));

        javafx.scene.text.Text changelogText = new javafx.scene.text.Text();
        changelogText.setFont(Font.font("Microsoft YaHei", 11));
        changelogText.setFill(Color.web("#888"));
        changelogText.setWrappingWidth(320);

        new Thread(() -> {
            try {
                UpdateChecker checker = new UpdateChecker(VERSION);
                String changelog = checker.fetchChangelog();
                Platform.runLater(() -> changelogText.setText(changelog));
            } catch (Exception e) {
                Platform.runLater(() -> changelogText.setText("获取更新日志失败"));
            }
        }).start();

        ScrollPane changelogScroll = new ScrollPane(changelogText);
        changelogScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        changelogScroll.setFitToWidth(true);
        changelogScroll.setPrefHeight(80);
        changelogScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        changelogScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        changelogBox.getChildren().addAll(changelogTitle, changelogScroll);

        Label linkTitle = new Label("下载链接:");
        linkTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        linkTitle.setTextFill(Color.web("#333"));

        javafx.scene.control.Hyperlink downloadLink = new javafx.scene.control.Hyperlink(result.downloadUrl);
        downloadLink.setFont(Font.font("Microsoft YaHei", 12));
        downloadLink.setTextFill(Color.web("#1565c0"));
        downloadLink.setWrapText(true);
        downloadLink.setOnAction(e -> {
            openUrl(result.downloadUrl);
        });

        VBox linkBox = new VBox(5);
        linkBox.setAlignment(Pos.CENTER_LEFT);
        linkBox.getChildren().addAll(linkTitle, downloadLink);

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
        progressBar.setPrefWidth(320);
        progressBar.setVisible(false);
        progressBar.setStyle("-fx-accent: #d32f2f;");

        Label progressLabel = new Label("");
        progressLabel.setFont(Font.font("Microsoft YaHei", 12));
        progressLabel.setTextFill(Color.web("#555"));
        progressLabel.setVisible(false);

        VBox progressBox = new VBox(8);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.getChildren().addAll(progressBar, progressLabel);
        progressBox.setVisible(false);

        Button updateBtn = new Button("立即更新");
        updateBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        updateBtn.setTextFill(Color.WHITE);
        updateBtn.setPrefWidth(200);
        updateBtn.setPrefHeight(45);
        updateBtn.setStyle(
            "-fx-background-color: #d32f2f;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        );
        updateBtn.setOnMouseEntered(e -> updateBtn.setStyle(
            "-fx-background-color: #b71c1c;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        ));
        updateBtn.setOnMouseExited(e -> updateBtn.setStyle(
            "-fx-background-color: #d32f2f;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        ));
        updateBtn.setOnAction(e -> {
            updateBtn.setVisible(false);
            linkBox.setVisible(false);
            progressBox.setVisible(true);
            progressBar.setVisible(true);
            progressLabel.setVisible(true);
            progressLabel.setText("正在下载更新...");

            UpdateDownloader.downloadUpdate(
                result.downloadUrl,
                result.sha256,
                result.latestVersion,
                new UpdateDownloader.DownloadCallback() {
                    @Override
                    public void onProgress(int percentage, String speed) {
                        Platform.runLater(() -> {
                            progressBar.setProgress(percentage / 100.0);
                            if (speed != null && !speed.isEmpty()) {
                                progressLabel.setText("下载进度: " + percentage + "% (" + speed + ")");
                            } else {
                                progressLabel.setText("下载进度: " + percentage + "%");
                            }
                        });
                    }

                    @Override
                    public void onComplete(boolean success, String message) {
                    }

                    @Override
                    public void onComplete(boolean success, String message, String newJarPath) {
                        Platform.runLater(() -> {
                            if (success) {
                                progressLabel.setTextFill(Color.web("#2e7d32"));
                                progressLabel.setText(message);
                                showForceUpdateRestartConfirm(newJarPath);
                            } else {
                                progressLabel.setTextFill(Color.web("#d32f2f"));
                                progressLabel.setText(message);
                                updateBtn.setVisible(true);
                            }
                        });
                    }
                }
            );
        });

        VBox btnBox = new VBox(12);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(15, 0, 0, 0));
        btnBox.getChildren().addAll(updateBtn);

        dialog.getChildren().addAll(warningIcon, titleLabel, urgentLabel, infoBox, changelogBox, linkBox, progressBox, btnBox);
        overlay.getChildren().add(dialog);
        rootContainer.getChildren().add(overlay);

        fadeIn(overlay, 300);

        dialog.setOpacity(0);
        dialog.setScaleX(0.8);
        dialog.setScaleY(0.8);

        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), dialog);
        fade.setFromValue(0);
        fade.setToValue(1);

        javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(300), dialog);
        scale.setFromX(0.8);
        scale.setFromY(0.8);
        scale.setToX(1);
        scale.setToY(1);

        javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(fade, scale);
        pt.play();
    }

    private void showForceUpdateRestartConfirm(String newJarPath) {
        int runningCount = serverManager.getRunningCount();

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.9);");
        overlay.setPrefSize(900, 600);
        overlay.setAlignment(Pos.CENTER);

        VBox dialog = new VBox(15);
        dialog.setAlignment(Pos.CENTER);
        dialog.setPadding(new Insets(30, 40, 30, 40));
        dialog.setMaxWidth(450);
        dialog.setStyle(
            "-fx-background-color: #ffebee;" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: #f44336;" +
            "-fx-border-width: 3;" +
            "-fx-border-radius: 16;" +
            "-fx-effect: dropshadow(gaussian, rgba(244,67,54,0.5), 30, 0, 0, 0);"
        );

        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z");
        icon.setFill(Color.web("#d32f2f"));
        icon.setScaleX(1.5);
        icon.setScaleY(1.5);

        Label titleLabel = new Label("更新下载完成");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#c62828"));

        Label messageLabel;
        if (runningCount > 0) {
            messageLabel = new Label("有 " + runningCount + " 个服务器正在运行，是否关闭服务器并重启程序？\n\n注意：强制更新必须重启才能生效！");
        } else {
            messageLabel = new Label("是否立即重启程序以完成更新？\n\n注意：强制更新必须重启才能生效！");
        }
        messageLabel.setFont(Font.font("Microsoft YaHei", 13));
        messageLabel.setTextFill(Color.web("#555"));
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.CENTER);

        Button restartBtn = new Button("立即重启");
        restartBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        restartBtn.setTextFill(Color.WHITE);
        restartBtn.setPrefWidth(200);
        restartBtn.setPrefHeight(45);
        restartBtn.setStyle(
            "-fx-background-color: #d32f2f;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        );
        restartBtn.setOnMouseEntered(e -> restartBtn.setStyle(
            "-fx-background-color: #b71c1c;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        ));
        restartBtn.setOnMouseExited(e -> restartBtn.setStyle(
            "-fx-background-color: #d32f2f;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        ));
        restartBtn.setOnAction(e -> {
            serverManager.stopAll();
            UpdateDownloader.launchNewVersion(newJarPath);
        });

        dialog.getChildren().addAll(icon, titleLabel, messageLabel, restartBtn);
        overlay.getChildren().add(dialog);
        rootContainer.getChildren().add(overlay);

        fadeIn(overlay, 300);

        dialog.setOpacity(0);
        dialog.setScaleX(0.8);
        dialog.setScaleY(0.8);

        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), dialog);
        fade.setFromValue(0);
        fade.setToValue(1);

        javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(300), dialog);
        scale.setFromX(0.8);
        scale.setFromY(0.8);
        scale.setToX(1);
        scale.setToY(1);

        javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(fade, scale);
        pt.play();
    }

    public void showNormalUpdateDialog(UpdateChecker.UpdateResult result) {
        final String[] newJarPath = new String[1];
        Platform.runLater(() -> {
            StackPane overlay = new StackPane();
            overlay.setStyle("-fx-background-color: rgba(0,0,0,0.5);");
            overlay.setPrefSize(900, 600);
            overlay.setAlignment(Pos.CENTER);

            VBox dialog = new VBox(12);
            dialog.setAlignment(Pos.CENTER);
            dialog.setPadding(new Insets(20, 30, 20, 30));
            dialog.setMaxWidth(420);
            dialog.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 8;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 3);"
            );

            javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
            icon.setContent("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z");
            icon.setFill(Color.web("#4CAF50"));
            icon.setScaleX(1.5);
            icon.setScaleY(1.5);

            Label titleLabel = new Label("发现新版本");
            titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
            titleLabel.setTextFill(Color.web("#333"));

            VBox infoBox = new VBox(8);
            infoBox.setAlignment(Pos.CENTER_LEFT);
            infoBox.setPadding(new Insets(5, 0, 5, 0));

            Label currentVerLabel = new Label("当前版本: " + VERSION);
            currentVerLabel.setFont(Font.font("Microsoft YaHei", 13));
            currentVerLabel.setTextFill(Color.web("#666"));

            Label newVerLabel = new Label("最新版本: " + result.latestVersion);
            newVerLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            newVerLabel.setTextFill(Color.web("#4CAF50"));

            Label dateLabel = new Label("发布日期: " + result.updateDate);
            dateLabel.setFont(Font.font("Microsoft YaHei", 12));
            dateLabel.setTextFill(Color.web("#888"));

            infoBox.getChildren().addAll(currentVerLabel, newVerLabel, dateLabel);

            VBox changelogBox = new VBox(5);
            changelogBox.setAlignment(Pos.CENTER_LEFT);
            changelogBox.setPadding(new Insets(5, 0, 5, 0));
            changelogBox.setMaxHeight(100);
            changelogBox.setPrefHeight(100);

            Label changelogTitle = new Label("更新内容:");
            changelogTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
            changelogTitle.setTextFill(Color.web("#666"));

            javafx.scene.text.Text changelogText = new javafx.scene.text.Text();
            changelogText.setFont(Font.font("Microsoft YaHei", 11));
            changelogText.setFill(Color.web("#888"));
            changelogText.setWrappingWidth(320);

            new Thread(() -> {
                try {
                    UpdateChecker checker = new UpdateChecker(VERSION);
                    String changelog = checker.fetchChangelog();
                    Platform.runLater(() -> changelogText.setText(changelog));
                } catch (Exception e) {
                    Platform.runLater(() -> changelogText.setText("获取更新日志失败"));
                }
            }).start();

            ScrollPane changelogScroll = new ScrollPane(changelogText);
            changelogScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            changelogScroll.setFitToWidth(true);
            changelogScroll.setPrefHeight(80);
            changelogScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            changelogScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

            changelogBox.getChildren().addAll(changelogTitle, changelogScroll);

            javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
            progressBar.setPrefWidth(320);
            progressBar.setVisible(false);
            progressBar.setStyle("-fx-accent: #4CAF50;");

            Label progressLabel = new Label("");
            progressLabel.setFont(Font.font("Microsoft YaHei", 12));
            progressLabel.setTextFill(Color.web("#666"));
            progressLabel.setVisible(false);

            VBox progressBox = new VBox(8);
            progressBox.setAlignment(Pos.CENTER);
            progressBox.getChildren().addAll(progressBar, progressLabel);
            progressBox.setVisible(false);

            HBox btnBox = new HBox(15);
            btnBox.setAlignment(Pos.CENTER);
            btnBox.setPadding(new Insets(10, 0, 0, 0));

            Button updateBtn = new Button("立即更新");
            updateBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
            updateBtn.setTextFill(Color.WHITE);
            updateBtn.setPrefWidth(120);
            updateBtn.setPrefHeight(38);
            updateBtn.setStyle(
                "-fx-background-color: #4CAF50;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            );
            updateBtn.setOnMouseEntered(e -> updateBtn.setStyle(
                "-fx-background-color: #45a049;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            ));
            updateBtn.setOnMouseExited(e -> updateBtn.setStyle(
                "-fx-background-color: #4CAF50;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            ));

            Button laterBtn = new Button("稍后提醒");
            laterBtn.setFont(Font.font("Microsoft YaHei", 14));
            laterBtn.setTextFill(Color.WHITE);
            laterBtn.setPrefWidth(120);
            laterBtn.setPrefHeight(38);
            laterBtn.setStyle(
                "-fx-background-color: #757575;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            );
            laterBtn.setOnMouseEntered(e -> laterBtn.setStyle(
                "-fx-background-color: #616161;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            ));
            laterBtn.setOnMouseExited(e -> laterBtn.setStyle(
                "-fx-background-color: #757575;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            ));
            laterBtn.setOnAction(e -> rootContainer.getChildren().remove(overlay));

            updateBtn.setOnAction(e -> {
                updateBtn.setVisible(false);
                laterBtn.setVisible(false);
                progressBox.setVisible(true);
                progressBar.setVisible(true);
                progressLabel.setVisible(true);
                progressLabel.setText("正在下载更新...");

                UpdateDownloader.downloadUpdate(
                    result.downloadUrl,
                    result.sha256,
                    result.latestVersion,
                    new UpdateDownloader.DownloadCallback() {
                        @Override
                        public void onProgress(int percentage, String speed) {
                            Platform.runLater(() -> {
                                progressBar.setProgress(percentage / 100.0);
                                if (speed != null && !speed.isEmpty()) {
                                    progressLabel.setText("下载进度: " + percentage + "% (" + speed + ")");
                                } else {
                                    progressLabel.setText("下载进度: " + percentage + "%");
                                }
                            });
                        }

                        @Override
                        public void onComplete(boolean success, String message) {
                        }

                        @Override
                        public void onComplete(boolean success, String message, String newJar) {
                            Platform.runLater(() -> {
                                if (success) {
                                    progressLabel.setTextFill(Color.web("#2e7d32"));
                                    progressLabel.setText(message);
                                    newJarPath[0] = newJar;
                                    showNormalUpdateRestartConfirm(overlay, newJarPath[0]);
                                } else {
                                    progressLabel.setTextFill(Color.web("#d32f2f"));
                                    progressLabel.setText(message);
                                    updateBtn.setVisible(true);
                                    laterBtn.setVisible(true);
                                }
                            });
                        }
                    }
                );
            });

            btnBox.getChildren().addAll(updateBtn, laterBtn);

            dialog.getChildren().addAll(icon, titleLabel, infoBox, changelogBox, progressBox, btnBox);
            overlay.getChildren().add(dialog);
            rootContainer.getChildren().add(overlay);

            fadeIn(overlay, 200);

            dialog.setOpacity(0);
            dialog.setScaleX(0.9);
            dialog.setScaleY(0.9);

            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), dialog);
            fade.setFromValue(0);
            fade.setToValue(1);

            javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(200), dialog);
            scale.setFromX(0.9);
            scale.setFromY(0.9);
            scale.setToX(1);
            scale.setToY(1);

            javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(fade, scale);
            pt.play();
        });
    }
    
    public Stage getPrimaryStage() { return primaryStage; }
    public ServerManager getServerManager() { return serverManager; }
    
    public void setCurrentPage(String page) {
        sidebar.setCurrentPage(page);
    }

    private void showNormalUpdateRestartConfirm(StackPane parentOverlay, String newJarPath) {
        int runningCount = serverManager.getRunningCount();

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        overlay.setPrefSize(900, 600);
        overlay.setAlignment(Pos.CENTER);

        VBox dialog = new VBox(15);
        dialog.setAlignment(Pos.CENTER);
        dialog.setPadding(new Insets(25, 35, 25, 35));
        dialog.setMaxWidth(420);
        dialog.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
        );

        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z");
        icon.setFill(Color.web("#4CAF50"));
        icon.setScaleX(1.5);
        icon.setScaleY(1.5);

        Label titleLabel = new Label("更新下载完成");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#333"));

        Label messageLabel;
        if (runningCount > 0) {
            messageLabel = new Label("有 " + runningCount + " 个服务器正在运行，是否关闭服务器并重启程序？");
        } else {
            messageLabel = new Label("是否立即重启程序以完成更新？");
        }
        messageLabel.setFont(Font.font("Microsoft YaHei", 13));
        messageLabel.setTextFill(Color.web("#666"));
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.CENTER);

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER);

        Button restartBtn = new Button("立即重启");
        restartBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        restartBtn.setTextFill(Color.WHITE);
        restartBtn.setPrefWidth(120);
        restartBtn.setPrefHeight(38);
        restartBtn.setStyle(
            "-fx-background-color: #4CAF50;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        );
        restartBtn.setOnMouseEntered(e -> restartBtn.setStyle(
            "-fx-background-color: #45a049;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        restartBtn.setOnMouseExited(e -> restartBtn.setStyle(
            "-fx-background-color: #4CAF50;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        restartBtn.setOnAction(e -> {
            serverManager.stopAll();
            UpdateDownloader.launchNewVersion(newJarPath);
        });

        Button laterBtn = new Button("稍后手动重启");
        laterBtn.setFont(Font.font("Microsoft YaHei", 14));
        laterBtn.setTextFill(Color.WHITE);
        laterBtn.setPrefWidth(140);
        laterBtn.setPrefHeight(38);
        laterBtn.setStyle(
            "-fx-background-color: #757575;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        );
        laterBtn.setOnMouseEntered(e -> laterBtn.setStyle(
            "-fx-background-color: #616161;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        laterBtn.setOnMouseExited(e -> laterBtn.setStyle(
            "-fx-background-color: #757575;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        laterBtn.setOnAction(e -> {
            rootContainer.getChildren().remove(parentOverlay);
            rootContainer.getChildren().remove(overlay);
        });

        btnBox.getChildren().addAll(restartBtn, laterBtn);

        dialog.getChildren().addAll(icon, titleLabel, messageLabel, btnBox);
        overlay.getChildren().add(dialog);
        rootContainer.getChildren().add(overlay);

        fadeIn(overlay, 200);

        dialog.setOpacity(0);
        dialog.setScaleX(0.9);
        dialog.setScaleY(0.9);

        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), dialog);
        fade.setFromValue(0);
        fade.setToValue(1);

        javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(200), dialog);
        scale.setFromX(0.9);
        scale.setFromY(0.9);
        scale.setToX(1);
        scale.setToY(1);

        javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(fade, scale);
        pt.play();
    }

    public static void main(String[] args) {
        UpdateDownloader.DeleteResult result = UpdateDownloader.checkAndDeleteOldVersion();
        if (!result.success && result.hasMarkerFile) {
            System.err.println("更新清理失败: " + result.message);
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "更新清理失败: " + result.message + "\n请手动删除旧版本文件",
                "更新错误",
                javax.swing.JOptionPane.ERROR_MESSAGE
            );
        }
        launch(args);
    }

    public static class DialogButton {
        public final String text;
        public final String bgColor;
        public final String hoverColor;
        public final String textColor;
        public final Runnable action;

        public DialogButton(String text, String bgColor, String hoverColor, String textColor, Runnable action) {
            this.text = text;
            this.bgColor = bgColor;
            this.hoverColor = hoverColor;
            this.textColor = textColor;
            this.action = action;
        }
    }

    public void showGenericDialog(String title, String message, java.util.List<DialogButton> buttons, Node customContent) {
        Platform.runLater(() -> {
            StackPane overlay = new StackPane();
            overlay.setStyle("-fx-background-color: rgba(0,0,0,0.5);");
            overlay.setPrefSize(900, 600);
            overlay.setAlignment(Pos.CENTER);

            VBox dialog = new VBox(12);
            dialog.setAlignment(Pos.CENTER);
            dialog.setPadding(new Insets(20, 25, 20, 25));
            dialog.setMaxWidth(400);
            dialog.setMaxHeight(500);
            dialog.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
            );

            javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
            icon.setContent("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z");
            icon.setFill(Color.web("#FF9800"));
            icon.setScaleX(1.5);
            icon.setScaleY(1.5);

            Label titleLabel = new Label(title);
            titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
            titleLabel.setTextFill(Color.web("#333333"));

            Label msgLabel = new Label(message);
            msgLabel.setFont(Font.font("Microsoft YaHei", 13));
            msgLabel.setTextFill(Color.web("#666666"));
            msgLabel.setWrapText(true);
            msgLabel.setAlignment(Pos.CENTER);

            VBox contentBox = new VBox(12);
            contentBox.setAlignment(Pos.CENTER);
            contentBox.getChildren().addAll(icon, titleLabel, msgLabel);

            if (customContent != null) {
                contentBox.getChildren().add(customContent);
            }

            VBox btnBox = new VBox(10);
            btnBox.setAlignment(Pos.CENTER);
            btnBox.setPadding(new Insets(10, 0, 0, 0));

            for (DialogButton btnDef : buttons) {
                Button btn = new Button(btnDef.text);
                btn.setFont(Font.font("Microsoft YaHei", 13));
                btn.setTextFill(Color.web(btnDef.textColor));
                btn.setPrefWidth(200);
                btn.setStyle(
                    "-fx-background-color: " + btnDef.bgColor + ";" +
                    "-fx-background-radius: 6;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 10 20;"
                );
                btn.setOnMouseEntered(e -> btn.setStyle(
                    "-fx-background-color: " + btnDef.hoverColor + ";" +
                    "-fx-background-radius: 6;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 10 20;"
                ));
                btn.setOnMouseExited(e -> btn.setStyle(
                    "-fx-background-color: " + btnDef.bgColor + ";" +
                    "-fx-background-radius: 6;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 10 20;"
                ));
                btn.setOnAction(e -> {
                    if (btnDef.action != null) {
                        btnDef.action.run();
                    }
                    rootContainer.getChildren().remove(overlay);
                });
                btnBox.getChildren().add(btn);
            }

            contentBox.getChildren().add(btnBox);
            dialog.getChildren().add(contentBox);

            overlay.getChildren().add(dialog);
            rootContainer.getChildren().add(overlay);

            fadeIn(overlay, 200);

            dialog.setOpacity(0);
            dialog.setScaleX(0.9);
            dialog.setScaleY(0.9);

            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), dialog);
            fade.setFromValue(0);
            fade.setToValue(1);

            javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(200), dialog);
            scale.setFromX(0.9);
            scale.setFromY(0.9);
            scale.setToX(1);
            scale.setToY(1);

            javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(fade, scale);
            pt.play();
        });
    }
}
