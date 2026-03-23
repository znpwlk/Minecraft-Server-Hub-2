import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.animation.*;
import javafx.util.Duration;

import java.io.*;
import java.util.*;

public class ContentPanel extends StackPane {
    private Main mainApp;
    private ServerManager serverManager;
    private Sidebar sidebar;
    private Region currentView;
    private TextArea logArea;
    private Label statusLabel;
    private javafx.scene.shape.Circle statusIndicator;
    private javafx.animation.Animation statusAnimation;
    private Button startStopBtn;
    private Button stopBtn;
    private Button forceStopBtn;
    private TextField commandField;
    private Button sendBtn;
    private ServerCore currentServer;
    private String currentPage = "";
    private boolean isAnimating = false;
    private java.util.concurrent.ExecutorService verifyExecutor = null;
    private Animation currentAnimation = null;
    private long lastSwitchTime = 0;
    private static final long MIN_SWITCH_INTERVAL = 50;
    private String lastRandomEffect = null;
    
    public ContentPanel(Main mainApp, ServerManager manager) {
        this.mainApp = mainApp;
        this.serverManager = manager;

        setPrefWidth(720);
        setMaxWidth(720);
        setPrefHeight(568);
        setMaxHeight(568);
        setPadding(new Insets(20));

        setStyle(
            "-fx-background-color: transparent;"
        );

        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(720, 568);
        setClip(clip);

        loadDialogAnimation();

        showHome();
    }
    
    public void showHome() {
        VBox home = new VBox(20);
        home.setAlignment(Pos.TOP_CENTER);
        home.setPadding(new Insets(20, 30, 20, 30));
        
        Label title = new Label("MSH2");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 42));
        title.setTextFill(Color.WHITE);
        title.setEffect(new DropShadow(12, Color.rgb(100, 180, 255, 0.7)));
        
        HBox stats = new HBox(30);
        stats.setAlignment(Pos.CENTER);
        
        VBox totalBox = createStatBox("服务器总数", String.valueOf(serverManager.getTotalCount()));
        VBox runningBox = createStatBox("运行中", String.valueOf(serverManager.getRunningCount()));
        
        stats.getChildren().addAll(totalBox, runningBox);
        
        VBox serverListSection = createHomeServerList();
        VBox.setVgrow(serverListSection, Priority.ALWAYS);
        
        home.getChildren().addAll(title, stats, serverListSection);

        String transitionType = determineTransition("home");
        switchView(home, transitionType);
        currentPage = "home";

        if (sidebar != null) {
            sidebar.hideSubPageNav();
        }
    }
    
    private VBox createHomeServerList() {
        VBox container = new VBox(10);
        container.setAlignment(Pos.TOP_CENTER);
        
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label title = new Label("服务器列表");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button addBtn = createSmallIconBtn("添加", "#4CAF50", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z");
        addBtn.setOnAction(e -> {
            mainApp.setCurrentPage("add");
            showAddServer();
        });
        
        header.getChildren().addAll(title, spacer, addBtn);
        
        ScrollPane scroll = new ScrollPane();
        scroll.setStyle(
            "-fx-background: transparent;" +
            "-fx-background-color: transparent;"
        );
        scroll.setFitToWidth(true);
        scroll.setMaxWidth(640);
        scroll.setPrefHeight(320);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        VBox serverBox = new VBox(8);
        serverBox.setPadding(new Insets(5));
        
        List<ServerCore> starredServers = serverManager.getStarredServers();
        List<ServerCore> normalServers = serverManager.getSortedServers().stream()
            .filter(s -> !s.isStarred())
            .collect(java.util.stream.Collectors.toList());
        
        if (starredServers.isEmpty() && normalServers.isEmpty()) {
            Label empty = new Label("暂无服务器，点击上方按钮添加");
            empty.setFont(Font.font("Microsoft YaHei", 13));
            empty.setTextFill(Color.GRAY);
            empty.setPadding(new Insets(30, 0, 30, 0));
            serverBox.getChildren().add(empty);
        } else {
            if (!starredServers.isEmpty()) {
                Label starredLabel = new Label("⭐ 星标服务器");
                starredLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
                starredLabel.setTextFill(Color.web("#FFD700"));
                starredLabel.setPadding(new Insets(5, 0, 5, 5));
                serverBox.getChildren().add(starredLabel);
                
                for (ServerCore s : starredServers) {
                    serverBox.getChildren().add(createHomeServerCard(s, serverBox));
                }
            }
            
            if (!normalServers.isEmpty()) {
                if (!starredServers.isEmpty()) {
                    Label normalLabel = new Label("服务器");
                    normalLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
                    normalLabel.setTextFill(Color.rgb(180, 180, 180));
                    normalLabel.setPadding(new Insets(15, 0, 5, 5));
                    serverBox.getChildren().add(normalLabel);
                }
                
                for (ServerCore s : normalServers) {
                    serverBox.getChildren().add(createHomeServerCard(s, serverBox));
                }
            }
        }
        
        scroll.setContent(serverBox);
        container.getChildren().addAll(header, scroll);
        
        return container;
    }
    
    private HBox createHomeServerCard(ServerCore server, VBox parentContainer) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: rgba(255,255,255,0.1);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        );
        
        Button starBtn = createStarBtn(server);
        
        VBox info = new VBox(4);
        info.setMaxWidth(380);
        HBox.setHgrow(info, Priority.ALWAYS);
        
        Label name = new Label(server.getName());
        name.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        name.setTextFill(Color.WHITE);
        name.setMaxWidth(380);
        name.setEllipsisString("...");
        name.setCursor(Cursor.HAND);
        name.setOnMouseEntered(e -> name.setTextFill(Color.web("#64B5F6")));
        name.setOnMouseExited(e -> name.setTextFill(Color.WHITE));
        name.setOnMouseClicked(e -> {
            currentServer = server;
            serverManager.setSelected(server);
            mainApp.setCurrentPage("console");
            showConsole();
        });
        
        Label status = new Label(server.isRunning() ? "● 运行中" : "● 已停止");
        status.setFont(Font.font("Microsoft YaHei", 11));
        status.setTextFill(server.isRunning() ? Color.LIGHTGREEN : Color.ORANGE);
        
        info.getChildren().addAll(name, status);
        
        HBox controls = new HBox(5);
        controls.setAlignment(Pos.CENTER_RIGHT);
        
        Button upBtn = createSmallIconBtn("", "#78909C", "M7 14l5-5 5 5z");
        upBtn.setPrefSize(28, 28);
        upBtn.setOnAction(e -> {
            serverManager.moveServerUp(server);
            mainApp.saveServers();
            refreshHomeServerList(parentContainer);
        });
        
        Button downBtn = createSmallIconBtn("", "#78909C", "M7 10l5 5 5-5z");
        downBtn.setPrefSize(28, 28);
        downBtn.setOnAction(e -> {
            serverManager.moveServerDown(server);
            mainApp.saveServers();
            refreshHomeServerList(parentContainer);
        });
        
        Button manageBtn = createSmallIconBtn("管理", "#2196F3", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z");
        manageBtn.setOnAction(e -> {
            currentServer = server;
            serverManager.setSelected(server);
            mainApp.setCurrentPage("console");
            showConsole();
        });
        
        controls.getChildren().addAll(upBtn, downBtn, manageBtn);
        
        card.getChildren().addAll(starBtn, info, controls);
        
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: rgba(255,255,255,0.2);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: rgba(255,255,255,0.1);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        ));
        
        return card;
    }
    
    private Button createStarBtn(ServerCore server) {
        String starOutline = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
        String starFilled = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
        
        Button btn = new Button();
        btn.setPrefSize(32, 32);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-padding: 4;"
        );
        
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setContent(server.isStarred() ? starFilled : starOutline);
        svg.setFill(server.isStarred() ? Color.web("#FFD700") : Color.web("#78909C"));
        svg.setStroke(server.isStarred() ? Color.web("#FFD700") : Color.web("#78909C"));
        svg.setStrokeWidth(1);
        
        btn.setGraphic(svg);
        btn.setCursor(Cursor.HAND);
        
        btn.setOnAction(e -> {
            serverManager.toggleStar(server);
            mainApp.saveServers();
            svg.setFill(server.isStarred() ? Color.web("#FFD700") : Color.web("#78909C"));
            svg.setStroke(server.isStarred() ? Color.web("#FFD700") : Color.web("#78909C"));
        });
        
        return btn;
    }
    
    private void refreshHomeServerList(VBox container) {
        container.getChildren().clear();
        List<ServerCore> starredServers = serverManager.getStarredServers();
        List<ServerCore> normalServers = serverManager.getSortedServers().stream()
            .filter(s -> !s.isStarred())
            .collect(java.util.stream.Collectors.toList());
        
        if (starredServers.isEmpty() && normalServers.isEmpty()) {
            Label empty = new Label("暂无服务器，点击上方按钮添加");
            empty.setFont(Font.font("Microsoft YaHei", 13));
            empty.setTextFill(Color.GRAY);
            empty.setPadding(new Insets(30, 0, 30, 0));
            container.getChildren().add(empty);
        } else {
            if (!starredServers.isEmpty()) {
                Label starredLabel = new Label("⭐ 星标服务器");
                starredLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
                starredLabel.setTextFill(Color.web("#FFD700"));
                starredLabel.setPadding(new Insets(5, 0, 5, 5));
                container.getChildren().add(starredLabel);
                
                for (ServerCore s : starredServers) {
                    container.getChildren().add(createHomeServerCard(s, container));
                }
            }
            
            if (!normalServers.isEmpty()) {
                if (!starredServers.isEmpty()) {
                    Label normalLabel = new Label("服务器");
                    normalLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
                    normalLabel.setTextFill(Color.rgb(180, 180, 180));
                    normalLabel.setPadding(new Insets(15, 0, 5, 5));
                    container.getChildren().add(normalLabel);
                }
                
                for (ServerCore s : normalServers) {
                    container.getChildren().add(createHomeServerCard(s, container));
                }
            }
        }
    }

    private VBox createStatBox(String label, String value) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 30, 20, 30));
        box.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-background-radius: 12;"
        );
        
        Label valLabel = new Label(value);
        valLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 36));
        valLabel.setTextFill(Color.WHITE);
        
        Label nameLabel = new Label(label);
        nameLabel.setFont(Font.font("Microsoft YaHei", 13));
        nameLabel.setTextFill(Color.rgb(180, 180, 180));
        
        box.getChildren().addAll(valLabel, nameLabel);
        return box;
    }
    
    public void showServerList() {
        VBox listView = new VBox(15);
        listView.setPadding(new Insets(10));
        
        Label title = new Label("服务器列表");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);
        
        ScrollPane scroll = new ScrollPane();
        scroll.setStyle(
            "-fx-background: transparent;" +
            "-fx-background-color: transparent;"
        );
        scroll.setFitToWidth(true);
        scroll.setMaxWidth(680);
        scroll.setPrefHeight(420);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinHeight(100);
        
        VBox serverBox = new VBox(10);
        serverBox.setPadding(new Insets(10));
        
        List<ServerCore> servers = serverManager.getSortedServers();
        if (servers.isEmpty()) {
            Label empty = new Label("暂无服务器，点击添加");
            empty.setFont(Font.font("Microsoft YaHei", 14));
            empty.setTextFill(Color.GRAY);
            serverBox.getChildren().add(empty);
        } else {
            for (ServerCore s : servers) {
                serverBox.getChildren().add(createServerCard(s, serverBox));
            }
        }
        
        scroll.setContent(serverBox);
        listView.getChildren().addAll(title, scroll);

        String transitionType = determineTransition("list");
        switchView(listView, transitionType);
        currentPage = "list";

        if (sidebar != null) {
            sidebar.hideSubPageNav();
        }
    }

    private HBox createServerCard(ServerCore server, VBox parentContainer) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: rgba(255,255,255,0.15);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Button starBtn = createStarBtnForList(server, parentContainer);
        
        VBox info = new VBox(5);
        info.setMaxWidth(420);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label name = new Label(server.getName());
        name.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        name.setTextFill(Color.WHITE);
        name.setMaxWidth(420);
        name.setEllipsisString("...");

        String pathText = server.getJarPath();
        if (pathText.length() > 50) {
            pathText = pathText.substring(0, 25) + "..." + pathText.substring(pathText.length() - 22);
        }
        Label path = new Label(pathText);
        path.setFont(Font.font("Microsoft YaHei", 11));
        path.setTextFill(Color.rgb(150, 150, 150));
        path.setMaxWidth(420);

        Label status = new Label(server.isRunning() ? "● 运行中" : "● 已停止");
        status.setFont(Font.font("Microsoft YaHei", 12));
        status.setTextFill(server.isRunning() ? Color.LIGHTGREEN : Color.ORANGE);

        info.getChildren().addAll(name, path, status);

        name.setCursor(Cursor.HAND);
        name.setOnMouseEntered(e -> name.setTextFill(Color.web("#64B5F6")));
        name.setOnMouseExited(e -> name.setTextFill(Color.WHITE));
        name.setOnMouseClicked(e -> {
            currentServer = server;
            serverManager.setSelected(server);
            mainApp.setCurrentPage("console");
            showConsole();
        });

        Button manageBtn = createSmallIconBtn("管理", "#2196F3", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z");
        manageBtn.setOnAction(e -> {
            currentServer = server;
            serverManager.setSelected(server);
            mainApp.setCurrentPage("console");
            showConsole();
        });
        
        Button delBtn = createSmallIconBtn("删除", "#f44336", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
        delBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认删除");
            alert.setHeaderText("删除服务器 '" + server.getName() + "'?");
            alert.setContentText("此操作不可恢复");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                serverManager.removeServer(server);
                mainApp.saveServers();
                showServerList();
            }
        });
        
        card.getChildren().addAll(starBtn, info, manageBtn, delBtn);
        
        card.setOnMouseEntered(ev -> card.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-border-color: rgba(255,255,255,0.25);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 0 0 1 0;"
        ));
        card.setOnMouseExited(ev -> card.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: rgba(255,255,255,0.15);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 0 0 1 0;"
        ));
        
        return card;
    }
    
    private Button createStarBtnForList(ServerCore server, VBox parentContainer) {
        Button btn = new Button();
        btn.setPrefSize(32, 32);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-padding: 4;"
        );
        
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setContent("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z");
        svg.setFill(server.isStarred() ? Color.web("#FFD700") : Color.web("#78909C"));
        svg.setStroke(server.isStarred() ? Color.web("#FFD700") : Color.web("#78909C"));
        svg.setStrokeWidth(1);
        
        btn.setGraphic(svg);
        btn.setCursor(Cursor.HAND);
        
        btn.setOnAction(e -> {
            serverManager.toggleStar(server);
            mainApp.saveServers();
            refreshServerList(parentContainer);
        });
        
        return btn;
    }
    
    private void refreshServerList(VBox container) {
        container.getChildren().clear();
        List<ServerCore> servers = serverManager.getSortedServers();
        if (servers.isEmpty()) {
            Label empty = new Label("暂无服务器，点击添加");
            empty.setFont(Font.font("Microsoft YaHei", 14));
            empty.setTextFill(Color.GRAY);
            container.getChildren().add(empty);
        } else {
            for (ServerCore s : servers) {
                container.getChildren().add(createServerCard(s, container));
            }
        }
    }
    
    public void showAddServer() {
        VBox addView = new VBox(18);
        addView.setPadding(new Insets(25, 50, 25, 50));
        addView.setAlignment(Pos.TOP_CENTER);
        
        Label title = new Label("添加服务器");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 26));
        title.setTextFill(Color.WHITE);
        title.setPadding(new Insets(0, 0, 10, 0));
        
        VBox formPanel = new VBox(20);
        formPanel.setPadding(new Insets(10, 0, 10, 0));
        formPanel.setMaxWidth(550);
        
        TextField nameField = createGlassField("输入服务器名称...");
        VBox nameSection = createFormSection("服务器名称", nameField);
        
        TextField pathField = createGlassField("");
        pathField.setEditable(false);
        pathField.setPromptText("选择服务端JAR文件...");
        Button browseBtn = createGlassBtn("浏览", "#7986CB");
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        fileRow.getChildren().addAll(pathField, browseBtn);
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择服务器JAR");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR文件", "*.jar"));
            File file = chooser.showOpenDialog(mainApp.getPrimaryStage());
            if (file != null) pathField.setText(file.getAbsolutePath());
        });
        VBox fileSection = createFormSection("服务端文件", fileRow);
        
        VBox memorySection = createMemorySection();
        TextField minRamField = (TextField) memorySection.getProperties().get("minRamField");
        TextField maxRamField = (TextField) memorySection.getProperties().get("maxRamField");
        
        VBox noguiSection = createNoguiSection();
        CheckBox noguiBox = (CheckBox) noguiSection.getProperties().get("noguiBox");
        
        VBox advancedSection = createAdvancedSection();
        TextField javaField = (TextField) advancedSection.getProperties().get("javaField");
        TextField argsField = (TextField) advancedSection.getProperties().get("argsField");
        
        Button addBtn = createGlassBtn("添加服务器", "#81C784");
        addBtn.setPrefWidth(180);
        addBtn.setPrefHeight(44);
        addBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        VBox btnBox = new VBox(addBtn);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(15, 0, 0, 0));
        
        addBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String path = pathField.getText().trim();
            String minRam = minRamField.getText().trim();
            String maxRam = maxRamField.getText().trim();
            String java = javaField.getText().trim();
            String args = argsField.getText().trim();
            boolean nogui = noguiBox.isSelected();
            
            if (name.isEmpty() || path.isEmpty()) {
                showAlert("请填写服务器名称和选择JAR文件");
                return;
            }
            
            if (serverManager.exists(name)) {
                showAlert("服务器名称已存在");
                return;
            }
            
            if (java.isEmpty()) java = "java";
            boolean autoMemory = minRam.isEmpty() && maxRam.isEmpty();
            if (nogui && !args.contains("nogui")) {
                args = args.isEmpty() ? "nogui" : args + " nogui";
            }
            
            ServerCore server = new ServerCore(name, path, minRam, maxRam, java, args, autoMemory);
            serverManager.addServer(server);
            mainApp.saveServers();
            mainApp.setCurrentPage("list");
            showServerList();
        });
        
        formPanel.getChildren().addAll(nameSection, fileSection, memorySection, noguiSection, advancedSection, btnBox);
        addView.getChildren().addAll(title, formPanel);
        
        ScrollPane scrollPane = new ScrollPane(addView);
        scrollPane.setFitToWidth(true);
        scrollPane.setMaxWidth(680);
        scrollPane.setStyle(
            "-fx-background: transparent;" +
            "-fx-background-color: transparent;"
        );
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        String transitionType = determineTransition("add");
        switchView(scrollPane, transitionType);
        currentPage = "add";

        if (sidebar != null) {
            sidebar.hideSubPageNav();
        }
    }

    private TextField createGlassField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefHeight(40);
        field.setStyle(
            "-fx-background-color: rgba(0,0,0,0.2);" +
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: rgba(255,255,255,0.4);" +
            "-fx-background-radius: 8;" +
            "-fx-border-radius: 8;" +
            "-fx-border-color: rgba(255,255,255,0.1);" +
            "-fx-border-width: 1;" +
            "-fx-padding: 0 15;" +
            "-fx-font-size: 13;"
        );
        field.focusedProperty().addListener((obs, old, val) -> {
            if (val) {
                field.setStyle(
                    "-fx-background-color: rgba(0,0,0,0.3);" +
                    "-fx-text-fill: white;" +
                    "-fx-prompt-text-fill: rgba(255,255,255,0.4);" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-radius: 8;" +
                    "-fx-border-color: rgba(100,181,246,0.5);" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 0 15;" +
                    "-fx-font-size: 13;"
                );
            } else {
                field.setStyle(
                    "-fx-background-color: rgba(0,0,0,0.2);" +
                    "-fx-text-fill: white;" +
                    "-fx-prompt-text-fill: rgba(255,255,255,0.4);" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-radius: 8;" +
                    "-fx-border-color: rgba(255,255,255,0.1);" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 0 15;" +
                    "-fx-font-size: 13;"
                );
            }
        });
        return field;
    }
    
    private Button createGlassBtn(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        btn.setTextFill(Color.WHITE);
        btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 10 20;"
        );
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }
    
    private VBox createFormSection(String labelText, javafx.scene.Node input) {
        VBox section = new VBox(8);
        section.setAlignment(Pos.CENTER_LEFT);
        section.setFillWidth(true);
        
        Label label = new Label(labelText);
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        label.setTextFill(Color.WHITE);
        
        section.getChildren().addAll(label, input);
        return section;
    }
    
    private VBox createMemorySection() {
        VBox section = new VBox(10);
        section.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("内存分配");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        titleLabel.setTextFill(Color.WHITE);
        
        Label infoLabel = new Label("Java 会自动管理内存，一般无需手动设置");
        infoLabel.setFont(Font.font("Microsoft YaHei", 11));
        infoLabel.setTextFill(Color.rgb(150, 200, 255));
        
        HBox memoryRow = new HBox(12);
        memoryRow.setAlignment(Pos.CENTER_LEFT);
        
        TextField minRamField = createGlassField("最小 (如: 1G)");
        minRamField.setPrefWidth(120);
        TextField maxRamField = createGlassField("最大 (如: 2G)");
        maxRamField.setPrefWidth(120);
        
        Label sepLabel = new Label("~");
        sepLabel.setTextFill(Color.WHITE);
        
        Button autoBtn = createGlassBtn("自动", "#7986CB");
        autoBtn.setOnAction(e -> {
            minRamField.clear();
            maxRamField.clear();
            minRamField.setPromptText("自动");
            maxRamField.setPromptText("自动");
        });
        
        memoryRow.getChildren().addAll(minRamField, sepLabel, maxRamField, autoBtn);
        
        section.getProperties().put("minRamField", minRamField);
        section.getProperties().put("maxRamField", maxRamField);
        
        section.getChildren().addAll(titleLabel, infoLabel, memoryRow);
        return section;
    }
    
    private VBox createNoguiSection() {
        VBox section = new VBox(10);
        section.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("显示模式");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        titleLabel.setTextFill(Color.WHITE);
        
        CheckBox noguiBox = new CheckBox("无图形化模式 (nogui)");
        noguiBox.setFont(Font.font("Microsoft YaHei", 12));
        noguiBox.setTextFill(Color.WHITE);
        
        Label infoLabel = new Label("开启后服务器将在后台运行，不显示窗口，适合长期运行的服务器");
        infoLabel.setFont(Font.font("Microsoft YaHei", 11));
        infoLabel.setTextFill(Color.rgb(180, 180, 180));
        infoLabel.setWrapText(true);
        
        section.getProperties().put("noguiBox", noguiBox);
        section.getChildren().addAll(titleLabel, noguiBox, infoLabel);
        return section;
    }
    
    private VBox createAdvancedSection() {
        VBox section = new VBox(12);
        section.setAlignment(Pos.CENTER_LEFT);
        
        Button toggleBtn = new Button("高级选项 ▼");
        toggleBtn.setFont(Font.font("Microsoft YaHei", 12));
        toggleBtn.setTextFill(Color.rgb(180, 180, 180));
        toggleBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 0;"
        );
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(8, 0, 0, 0));
        content.setVisible(false);
        content.setManaged(false);
        
        TextField javaField = createGlassField("java");
        VBox javaSection = createFormSection("Java 路径", javaField);
        
        TextField argsField = createGlassField("如: -XX:+UseG1GC");
        VBox argsSection = createFormSection("JVM 参数", argsField);
        
        content.getChildren().addAll(javaSection, argsSection);
        
        toggleBtn.setOnAction(e -> {
            boolean willShow = !content.isVisible();
            content.setVisible(willShow);
            content.setManaged(willShow);
            toggleBtn.setText(willShow ? "高级选项 ▲" : "高级选项 ▼");
        });
        
        section.getProperties().put("javaField", javaField);
        section.getProperties().put("argsField", argsField);

        section.getChildren().addAll(toggleBtn, content);
        return section;
    }

    private HBox controlsBox;

    public void showConsole() {
        currentServer = serverManager.getSelected();
        if (currentServer == null) {
            showAlert("请先选择一个服务器");
            return;
        }
        
        VBox consoleView = new VBox(15);
        consoleView.setPadding(new Insets(10));
        
        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(currentServer.getName());
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        titleBox.getChildren().addAll(title);

        controlsBox = new HBox(15);
        controlsBox.setAlignment(Pos.CENTER_LEFT);

        statusIndicator = new javafx.scene.shape.Circle(6);
        statusIndicator.setFill(Color.ORANGE);

        statusLabel = new Label("已停止");
        statusLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        statusLabel.setTextFill(Color.ORANGE);

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.getChildren().addAll(statusIndicator, statusLabel);

        startStopBtn = createIconBtn("启动", "#4CAF50", "M8 5v14l11-7z");
        startStopBtn.setOnAction(e -> toggleServer());

        stopBtn = createIconBtn("关闭", "#FF9800", "M6 6h12v12H6z");
        stopBtn.setOnAction(e -> {
            showConfirmDialog("确认关闭", "确定要关闭服务器 '" + currentServer.getName() + "' 吗？", () -> currentServer.stop(), null, null, "#FF9800", "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z");
        });
        stopBtn.setDisable(true);

        forceStopBtn = createIconBtn("强制关闭", "#f44336", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z");
        forceStopBtn.setOnAction(e -> {
            showConfirmDialog("确认强制关闭", "强制关闭可能导致数据丢失，确定要继续吗？", () -> currentServer.forceStop(), null, null, "#f44336", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z");
        });
        forceStopBtn.setDisable(true);

        controlsBox.getChildren().addAll(statusBox, startStopBtn, stopBtn, forceStopBtn);

        HBox logButtons = new HBox(8);
        logButtons.setAlignment(Pos.TOP_RIGHT);
        logButtons.setPadding(new Insets(0, 5, 5, 0));

        Button copyBtn = createSmallIconBtn("", "#2196F3", "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z");
        Button clearBtn = createSmallIconBtn("", "#f44336", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
        logButtons.getChildren().addAll(copyBtn, clearBtn);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefSize(640, 350);
        logArea.setMinSize(640, 350);
        logArea.setMaxSize(640, 350);
        logArea.setStyle(
            "-fx-control-inner-background: rgba(0,0,0,0.7);" +
            "-fx-text-fill: #e0e0e0;" +
            "-fx-font-family: Consolas;" +
            "-fx-font-size: 12;"
        );

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            java.util.List<String> history = currentServer.getLogHistory(currentServer.getLogDisplayLines());
            StringBuilder sb = new StringBuilder();
            for (String line : history) {
                sb.append(line).append("\n");
            }
            String logText = sb.toString();
            Platform.runLater(() -> {
                logArea.setText(logText);
                logArea.positionCaret(logArea.getText().length());
            });
            executor.shutdown();
        });

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setStyle("-fx-background-color: white;");
        
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setStyle("-fx-text-fill: black;");
        copyItem.setOnAction(e -> {
            String selected = logArea.getSelectedText();
            if (!selected.isEmpty()) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(selected);
                clipboard.setContent(cc);
            }
        });
        
        MenuItem copyAllItem = new MenuItem("复制全部");
        copyAllItem.setStyle("-fx-text-fill: black;");
        copyAllItem.setOnAction(e -> {
            String content = logArea.getText();
            if (!content.isEmpty()) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(content);
                clipboard.setContent(cc);
                mainApp.showNotification("日志已复制到剪贴板", "success");
            }
        });
        
        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setStyle("-fx-text-fill: black;");
        selectAllItem.setOnAction(e -> logArea.selectAll());
        
        MenuItem clearItem = new MenuItem("清除");
        clearItem.setStyle("-fx-text-fill: black;");
        clearItem.setOnAction(e -> {
            logArea.clear();
            currentServer.clearLogCache();
            mainApp.showNotification("日志已清除", "success");
        });
        
        contextMenu.getItems().addAll(copyItem, copyAllItem, selectAllItem, clearItem);
        logArea.setContextMenu(contextMenu);

        copyBtn.setOnAction(e -> {
            String content = logArea.getText();
            if (!content.isEmpty()) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(content);
                clipboard.setContent(cc);
                mainApp.showNotification("日志已复制到剪贴板", "success");
            } else {
                mainApp.showNotification("日志内容为空", "warning");
            }
        });

        clearBtn.setOnAction(e -> {
            logArea.clear();
            currentServer.clearLogCache();
            mainApp.showNotification("日志已清除", "success");
        });

        VBox logContainer = new VBox(5);
        logContainer.setPrefSize(660, 380);
        logContainer.setMaxSize(660, 380);
        logContainer.getChildren().addAll(logButtons, logArea);

        ScrollPane scroll = new ScrollPane(logContainer);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setFitToWidth(true);
        scroll.setMaxWidth(660);
        scroll.setPrefHeight(420);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        currentServer.setLogCallback((msg, level) -> {
            Platform.runLater(() -> {
                logArea.appendText(msg + "\n");
                logArea.positionCaret(logArea.getText().length());
            });
        });
        
        currentServer.setStateChangeCallback(() -> updateStatus());
        
        currentServer.setSaveCallback(() -> {
            ServerDataStore.saveServers(serverManager.getServers());
        });
        
        currentServer.setEulaIssueCallback(() -> {
            showEulaDialog();
        });
        
        if (currentServer.hasEulaIssue()) {
            Platform.runLater(() -> showEulaDialog());
        }
        
        if (currentServer.getState() == ServerCore.ServerState.STOPPED && currentServer.canAttach()) {
            java.util.concurrent.ExecutorService attachExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            attachExecutor.submit(() -> {
                if (currentServer.attachToProcess()) {
                    Platform.runLater(() -> {
                        mainApp.showNotification("已重新连接到外部启动的服务器 (PID: " + currentServer.getProcessPid() + ")，只能强制关闭", "warning");
                    });
                }
                attachExecutor.shutdown();
            });
        }
        
        HBox cmdBox = new HBox(10);
        cmdBox.setAlignment(Pos.CENTER);
        
        commandField = new TextField();
        commandField.setPromptText("输入命令...");
        commandField.setPrefWidth(550);
        commandField.setPrefHeight(38);
        commandField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: gray;" +
            "-fx-background-radius: 8;"
        );
        commandField.setOnAction(e -> sendCommand());
        
        sendBtn = createSmallIconBtn("发送", "#2196F3", "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z");
        sendBtn.setOnAction(e -> sendCommand());
        
        cmdBox.getChildren().addAll(commandField, sendBtn);
        
        consoleView.getChildren().addAll(titleBox, controlsBox, scroll, cmdBox);

        String transitionType = determineTransition("console");
        switchView(consoleView, transitionType);
        currentPage = "console";
        updateStatus();

        if (sidebar != null) {
            sidebar.showSubPageNav(
                currentServer.getName(),
                () -> {
                    mainApp.setCurrentPage("list");
                    showServerList();
                },
                "console",
                () -> showConsole(),
                () -> showConfig(),
                () -> showLogSettingsPage(),
                () -> showGameRulesPage(),
                () -> showJavaSettingsPage(),
                () -> showServerAddress()
            );
        }
    }

    private void toggleServer() {
        if (currentServer.isRunning()) {
            currentServer.stop();
        } else {
            currentServer.start();
        }
        updateStatus();
    }
    
    private void updateStatus() {
        ServerCore.ServerState state = currentServer.getState();
        if (state == null) {
            state = ServerCore.ServerState.STOPPED;
        }

        stopStatusAnimation();

        switch (state) {
            case STOPPED -> {
                statusLabel.setText("已停止");
                statusLabel.setTextFill(Color.ORANGE);
                statusIndicator.setFill(Color.ORANGE);
                startStopBtn.setText("启动");
                startStopBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
                startStopBtn.setOnAction(e -> currentServer.start());
                startStopBtn.setDisable(false);
                stopBtn.setDisable(true);
                forceStopBtn.setDisable(true);
            }
            case STARTING -> {
                statusLabel.setText("启动中...");
                statusLabel.setTextFill(Color.YELLOW);
                statusIndicator.setFill(Color.YELLOW);
                startBreathingAnimation(Color.YELLOW);
                startStopBtn.setDisable(true);
                stopBtn.setDisable(true);
                forceStopBtn.setDisable(false);
            }
            case RUNNING -> {
                if (currentServer.isReattached()) {
                    statusLabel.setText("运行中 (外部启动)");
                } else {
                    statusLabel.setText("运行中");
                }
                statusLabel.setTextFill(Color.LIGHTGREEN);
                statusIndicator.setFill(Color.LIGHTGREEN);
                startBreathingAnimation(Color.LIGHTGREEN);
                startStopBtn.setDisable(true);
                stopBtn.setDisable(currentServer.isReattached());
                forceStopBtn.setDisable(false);
                if (commandField != null) {
                    commandField.setDisable(currentServer.isReattached());
                    commandField.setPromptText(currentServer.isReattached() ? "外部启动的服务器无法发送指令" : "输入命令...");
                }
                if (sendBtn != null) {
                    sendBtn.setDisable(currentServer.isReattached());
                }
            }
            case STOPPING -> {
                statusLabel.setText("停止中...");
                statusLabel.setTextFill(Color.ORANGE);
                statusIndicator.setFill(Color.ORANGE);
                startBreathingAnimation(Color.ORANGE);
                startStopBtn.setDisable(true);
                stopBtn.setDisable(true);
                forceStopBtn.setDisable(false);
            }
        }
    }

    private void startBreathingAnimation(Color baseColor) {
        javafx.animation.KeyValue kv1 = new javafx.animation.KeyValue(statusIndicator.opacityProperty(), 0.3);
        javafx.animation.KeyValue kv2 = new javafx.animation.KeyValue(statusIndicator.opacityProperty(), 1.0);
        javafx.animation.KeyValue kv3 = new javafx.animation.KeyValue(statusIndicator.opacityProperty(), 0.3);

        javafx.animation.KeyFrame kf1 = new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, kv1);
        javafx.animation.KeyFrame kf2 = new javafx.animation.KeyFrame(javafx.util.Duration.millis(800), kv2);
        javafx.animation.KeyFrame kf3 = new javafx.animation.KeyFrame(javafx.util.Duration.millis(1600), kv3);

        javafx.animation.Timeline breathing = new javafx.animation.Timeline(kf1, kf2, kf3);
        breathing.setCycleCount(javafx.animation.Animation.INDEFINITE);
        breathing.setAutoReverse(false);
        breathing.play();
        statusAnimation = breathing;
    }

    private void stopStatusAnimation() {
        if (statusAnimation != null) {
            statusAnimation.stop();
            statusAnimation = null;
        }
        statusIndicator.setOpacity(1.0);
    }
    
    private void sendCommand() {
        String cmd = commandField.getText().trim();
        if (!cmd.isEmpty() && currentServer != null) {
            currentServer.sendCommand(cmd);
            commandField.clear();
        }
    }
    
    private void showEulaDialog() {
        String message = "使用本软件启动 Minecraft 服务器前，您必须同意 Minecraft 最终用户许可协议(EULA)。\n\n点击\"确定\"表示您已阅读、理解并同意遵守 EULA 的全部条款。本软件仅提供技术便利，不对您的使用行为承担任何责任。";
        showConfirmDialog(
            "EULA 确认",
            message,
            () -> {
                if (currentServer.fixEula()) {
                    if (currentServer.getState() == ServerCore.ServerState.STOPPED) {
                        currentServer.start();
                    } else {
                        currentServer.forceStop();
                        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                            Thread t = new Thread(r);
                            t.setDaemon(true);
                            return t;
                        });
                        scheduler.schedule(() -> {
                            Platform.runLater(() -> currentServer.start());
                            scheduler.shutdown();
                        }, 1, java.util.concurrent.TimeUnit.SECONDS);
                    }
                }
            },
            "查看 Minecraft EULA",
            "https://www.minecraft.net/zh-hans/eula"
        );
    }
    
    public void showConfig() {
        currentServer = serverManager.getSelected();
        if (currentServer == null) {
            showAlert("请先选择一个服务器");
            return;
        }

        File configFile = GameRules.getConfigFile(currentServer.getWorkingDir());
        if (!configFile.exists()) {
            GameRules.createDefaultConfig(currentServer.getWorkingDir());
        }

        VBox configView = new VBox(15);
        configView.setPadding(new Insets(10));

        Label title = new Label("配置管理 - " + currentServer.getName());
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        ScrollPane scroll = new ScrollPane();
        scroll.setStyle(
            "-fx-background: transparent;" +
            "-fx-background-color: transparent;"
        );
        scroll.setFitToWidth(true);
        scroll.setMaxWidth(680);
        scroll.setPrefHeight(420);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox configBox = new VBox(12);
        configBox.setPadding(new Insets(10));

        Map<String, String> props = GameRules.loadProperties(configFile);
        Map<String, javafx.scene.control.Control> controlMap = new HashMap<>();

        List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(props.entrySet());
        sortedEntries.sort(Comparator.comparingInt(e -> GameRules.getPriority(e.getKey())));

        for (Map.Entry<String, String> entry : sortedEntries) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (!GameRules.shouldShowOption(key, value)) {
                continue;
            }

            VBox row = new VBox(4);
            row.setAlignment(Pos.CENTER_LEFT);

            String configTitle = GameRules.getTitle(key);
            String description = GameRules.getDescription(key);

            Label titleLabel = new Label(configTitle);
            titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            titleLabel.setTextFill(Color.WHITE);

            Label descLabel = new Label(description);
            descLabel.setFont(Font.font("Microsoft YaHei", 11));
            descLabel.setTextFill(Color.rgb(180, 180, 180));
            descLabel.setWrapText(true);

            javafx.scene.control.Control inputControl;

            if (GameRules.isBooleanOption(key)) {
                ComboBox<String> comboBox = new ComboBox<>();
                comboBox.getItems().addAll("true", "false");
                comboBox.setValue(value);
                comboBox.setPrefWidth(200);
                comboBox.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.15);" +
                    "-fx-text-fill: black;" +
                    "-fx-background-radius: 6;"
                );
                inputControl = comboBox;
            } else {
                List<String> enumOptions = GameRules.getEnumOptions(key);
                if (enumOptions != null && !GameRules.supportsCustomValue(key)) {
                    ComboBox<String> comboBox = new ComboBox<>();
                    comboBox.getItems().addAll(enumOptions);

                    String displayValue = value;
                    for (String option : enumOptions) {
                        if (option.startsWith(value + " ")) {
                            displayValue = option;
                            break;
                        }
                    }
                    comboBox.setValue(displayValue);
                    comboBox.setPrefWidth(280);
                    comboBox.setStyle(
                        "-fx-background-color: rgba(255,255,255,0.15);" +
                        "-fx-text-fill: black;" +
                        "-fx-background-radius: 6;"
                    );

                    inputControl = comboBox;
                } else {
                    TextField textField = new TextField(value);
                    textField.setPrefWidth(350);
                    textField.setStyle(
                        "-fx-background-color: rgba(255,255,255,0.1);" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
                    );
                    inputControl = textField;
                }
            }

            controlMap.put(key, inputControl);
            row.getChildren().addAll(titleLabel, descLabel, inputControl);
            configBox.getChildren().add(row);
        }

        scroll.setContent(configBox);

        Button saveBtn = createIconBtn("保存配置", "#4CAF50", "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z");
        saveBtn.setOnAction(e -> {
            Map<String, String> newProps = new LinkedHashMap<>();
            for (Map.Entry<String, javafx.scene.control.Control> entry : controlMap.entrySet()) {
                String key = entry.getKey();
                javafx.scene.control.Control control = entry.getValue();
                String value;
                if (control instanceof ComboBox<?>) {
                    String displayValue = ((ComboBox<?>) control).getValue().toString();
                    value = GameRules.getValueFromDisplay(key, displayValue);
                } else {
                    value = ((TextField) control).getText();
                }
                newProps.put(key, value != null ? value : "");
            }
            GameRules.saveProperties(configFile, newProps);
            showSuccess("配置已保存");
        });

        configView.getChildren().addAll(title, scroll, saveBtn);

        String transitionType = determineTransition("config");
        switchView(configView, transitionType);
        currentPage = "config";

        if (sidebar != null) {
            sidebar.showSubPageNav(
                currentServer.getName(),
                () -> {
                    mainApp.setCurrentPage("list");
                    showServerList();
                },
                "config",
                () -> showConsole(),
                () -> showConfig(),
                () -> showLogSettingsPage(),
                () -> showGameRulesPage(),
                () -> showJavaSettingsPage(),
                () -> showServerAddress()
            );
        }
    }

    private void showJavaSettingsPage() {
        if (currentServer == null) return;

        VBox javaView = new VBox(15);
        javaView.setAlignment(Pos.TOP_CENTER);
        javaView.setPadding(new Insets(20));
        javaView.setPrefWidth(680);

        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Java 设置 - " + currentServer.getName());
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBox.getChildren().addAll(title, spacer);

        VBox contentBox = new VBox(12);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.setPadding(new Insets(10));
        contentBox.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 8; -fx-padding: 20;");

        Label currentLabel = new Label();
        currentLabel.setFont(Font.font("Microsoft YaHei", 12));
        currentLabel.setTextFill(Color.rgb(180, 180, 180));
        updateJavaStatusLabel(currentLabel);

        Label loadingLabel = new Label("正在检测 Java...");
        loadingLabel.setFont(Font.font("Microsoft YaHei", 12));
        loadingLabel.setTextFill(Color.rgb(180, 180, 180));

        javafx.scene.control.ToggleGroup javaGroup = new javafx.scene.control.ToggleGroup();
        Map<javafx.scene.control.RadioButton, String> javaRadioMap = new HashMap<>();

        javafx.scene.control.RadioButton autoRadio = new javafx.scene.control.RadioButton("自动选择（使用系统默认 Java）");
        autoRadio.setToggleGroup(javaGroup);
        autoRadio.setTextFill(Color.WHITE);
        autoRadio.setFont(Font.font("Microsoft YaHei", 12));

        javafx.scene.control.RadioButton customRadio = new javafx.scene.control.RadioButton("自定义路径");
        customRadio.setToggleGroup(javaGroup);
        customRadio.setTextFill(Color.WHITE);
        customRadio.setFont(Font.font("Microsoft YaHei", 12));

        TextField javaPathField = new TextField();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            javaPathField.setPromptText("例如: C:\\Program Files\\Java\\jdk-21\\bin\\java.exe");
        } else {
            javaPathField.setPromptText("例如: /usr/lib/jvm/java-21-openjdk/bin/java");
        }
        javaPathField.setPrefWidth(400);
        javaPathField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: gray;" +
            "-fx-background-radius: 6;"
        );

        Button browseBtn = createIconBtn("浏览", "#2196F3", "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z");
        browseBtn.setOnAction(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("选择 Java 可执行文件");
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                fileChooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("可执行文件", "*.exe")
                );
            }
            java.io.File selectedFile = fileChooser.showOpenDialog(null);
            if (selectedFile != null) {
                javaPathField.setText(selectedFile.getAbsolutePath());
            }
        });

        Button verifyBtn = createIconBtn("验证", "#FF9800", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z");
        final java.util.concurrent.atomic.AtomicBoolean isVerifying = new java.util.concurrent.atomic.AtomicBoolean(false);
        verifyBtn.setOnAction(e -> {
            String javaPath = javaPathField.getText().trim();
            if (javaPath.isEmpty()) {
                showAlert("请输入 Java 路径");
                return;
            }
            if (!isVerifying.compareAndSet(false, true)) {
                return;
            }
            if (verifyExecutor == null || verifyExecutor.isShutdown()) {
                isVerifying.set(false);
                showAlert("验证服务不可用，请刷新页面重试");
                return;
            }
            verifyBtn.setDisable(true);
            verifyBtn.setText("验证中...");
            verifyExecutor.submit(() -> {
                try {
                    boolean isValid = verifyJavaPath(javaPath);
                    Platform.runLater(() -> {
                        isVerifying.set(false);
                        if (verifyBtn != null) {
                            verifyBtn.setDisable(false);
                            verifyBtn.setText("验证");
                        }
                        if (isValid) {
                            showSuccess("Java 路径有效");
                        } else {
                            showAlert("无效的 Java 路径，请检查文件是否存在且为可执行的 Java 程序");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        isVerifying.set(false);
                        if (verifyBtn != null) {
                            verifyBtn.setDisable(false);
                            verifyBtn.setText("验证");
                        }
                        showAlert("验证过程发生错误");
                    });
                }
            });
        });

        HBox customBox = new HBox(10);
        customBox.setAlignment(Pos.CENTER_LEFT);
        customBox.getChildren().addAll(javaPathField, browseBtn, verifyBtn);

        javaGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCustom = newVal == customRadio;
            javaPathField.setDisable(!isCustom);
            browseBtn.setDisable(!isCustom);
            verifyBtn.setDisable(!isCustom);
        });

        Button saveBtn = createIconBtn("保存设置", "#4CAF50", "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z");
        saveBtn.setOnAction(e -> {
            if (autoRadio.isSelected()) {
                currentServer.setJavaPath("java");
            } else if (customRadio.isSelected()) {
                String path = javaPathField.getText().trim();
                if (!path.isEmpty()) {
                    currentServer.setJavaPath(path);
                } else {
                    showAlert("请输入 Java 路径");
                    return;
                }
            } else {
                for (Map.Entry<javafx.scene.control.RadioButton, String> entry : javaRadioMap.entrySet()) {
                    if (entry.getKey().isSelected()) {
                        String javaInfo = entry.getValue();
                        int idx = javaInfo.indexOf(" - ");
                        if (idx > 0) {
                            currentServer.setJavaPath(javaInfo.substring(idx + 3));
                        }
                        break;
                    }
                }
            }
            ServerDataStore.saveServers(serverManager.getServers());
            updateJavaStatusLabel(currentLabel);
            showSuccess("Java 设置已保存");
        });

        contentBox.getChildren().addAll(currentLabel, loadingLabel);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            List<String> detectedJavaList = detectSystemJava();

            Platform.runLater(() -> {
                contentBox.getChildren().remove(loadingLabel);

                String currentJava = currentServer.getJavaPath();
                boolean isAuto = currentJava == null || currentJava.isEmpty() || currentJava.equals("java");

                autoRadio.setSelected(isAuto);
                contentBox.getChildren().add(autoRadio);

                for (String javaInfo : detectedJavaList) {
                    javafx.scene.control.RadioButton radio = new javafx.scene.control.RadioButton(javaInfo);
                    radio.setToggleGroup(javaGroup);
                    radio.setTextFill(Color.WHITE);
                    radio.setFont(Font.font("Microsoft YaHei", 12));

                    if (!isAuto && javaInfo.contains(currentJava)) {
                        radio.setSelected(true);
                    }

                    javaRadioMap.put(radio, javaInfo);
                    contentBox.getChildren().add(radio);
                }

                if (!isAuto && javaRadioMap.isEmpty()) {
                    customRadio.setSelected(true);
                }

                if (!isAuto) {
                    javaPathField.setText(currentJava);
                }

                contentBox.getChildren().addAll(customRadio, customBox, saveBtn);

                javaPathField.setDisable(!customRadio.isSelected());
                browseBtn.setDisable(!customRadio.isSelected());
                verifyBtn.setDisable(!customRadio.isSelected());
            });
            executor.shutdown();
        });

        ScrollPane scroll = new ScrollPane(contentBox);

        javaView.setOnScroll(e -> {
            if (e.getDeltaY() != 0) {
                scroll.setVvalue(scroll.getVvalue() - e.getDeltaY() / scroll.getHeight());
            }
        });
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setFitToWidth(true);
        scroll.setMaxWidth(680);
        scroll.setPrefHeight(420);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        javaView.getChildren().addAll(titleBox, scroll);

        String transitionType = determineTransition("java");
        switchView(javaView, transitionType);
        currentPage = "java";

        if (verifyExecutor != null && !verifyExecutor.isShutdown()) {
            verifyExecutor.shutdownNow();
        }
        verifyExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        if (sidebar != null) {
            sidebar.showSubPageNav(
                currentServer.getName(),
                () -> {
                    mainApp.setCurrentPage("list");
                    showServerList();
                },
                "java",
                () -> showConsole(),
                () -> showConfig(),
                () -> showLogSettingsPage(),
                () -> showGameRulesPage(),
                () -> showJavaSettingsPage(),
                () -> showServerAddress()
            );
        }
    }

    private void updateJavaStatusLabel(Label label) {
        String currentJava = currentServer.getJavaPath();
        String display = (currentJava == null || currentJava.isEmpty() || currentJava.equals("java"))
            ? "当前设置: 自动选择（使用系统默认 Java）" : "当前设置: " + currentJava;
        label.setText(display);
    }

    private void showLogSettingsPage() {
        if (currentServer == null) return;

        if (sidebar != null) {
            sidebar.showSubPageNav(
                currentServer.getName(),
                () -> {
                    mainApp.setCurrentPage("list");
                    showServerList();
                },
                "logsettings",
                () -> showConsole(),
                () -> showConfig(),
                () -> showLogSettingsPage(),
                () -> showGameRulesPage(),
                () -> showJavaSettingsPage(),
                () -> showServerAddress()
            );
        }

        VBox logSettingsView = new VBox(25);
        logSettingsView.setPadding(new Insets(25));
        logSettingsView.setAlignment(Pos.TOP_CENTER);

        Label title = new Label(currentServer.getName() + " - 日志设置");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        VBox settingsBox = new VBox(20);
        settingsBox.setMaxWidth(500);
        settingsBox.setAlignment(Pos.CENTER_LEFT);

        java.util.List<File> logFiles = ServerCore.getLogFiles(currentServer.getId());
        long totalSize = 0;
        for (File f : logFiles) {
            totalSize += f.length();
        }
        String sizeStr = totalSize < 1024 * 1024 ?
            String.format("%.2f KB", totalSize / 1024.0) :
            String.format("%.2f MB", totalSize / (1024.0 * 1024.0));

        Label infoLabel = new Label("当前日志文件: " + logFiles.size() + " 个    占用空间: " + sizeStr);
        infoLabel.setFont(Font.font("Microsoft YaHei", 14));
        infoLabel.setTextFill(Color.rgb(180, 180, 180));

        Label limitLabel = new Label("日志大小限制 (MB)");
        limitLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        limitLabel.setTextFill(Color.WHITE);

        Label limitHint = new Label("-1 = 不限制    0 = 不缓存日志");
        limitHint.setFont(Font.font("Microsoft YaHei", 12));
        limitHint.setTextFill(Color.rgb(150, 150, 150));

        HBox limitBox = new HBox(15);
        limitBox.setAlignment(Pos.CENTER_LEFT);
        TextField limitField = new TextField(String.valueOf(currentServer.getLogSizeLimit()));
        limitField.setPrefWidth(120);
        limitField.setFont(Font.font("Microsoft YaHei", 14));
        limitField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 6;"
        );
        Label mbLabel = new Label("MB");
        mbLabel.setFont(Font.font("Microsoft YaHei", 14));
        mbLabel.setTextFill(Color.WHITE);
        limitBox.getChildren().addAll(limitField, mbLabel);

        Label linesLabel = new Label("终端显示行数");
        linesLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        linesLabel.setTextFill(Color.WHITE);

        Label linesHint = new Label("设置终端控制台显示的最大日志行数");
        linesHint.setFont(Font.font("Microsoft YaHei", 12));
        linesHint.setTextFill(Color.rgb(150, 150, 150));

        HBox linesBox = new HBox(15);
        linesBox.setAlignment(Pos.CENTER_LEFT);
        TextField linesField = new TextField(String.valueOf(currentServer.getLogDisplayLines()));
        linesField.setPrefWidth(120);
        linesField.setFont(Font.font("Microsoft YaHei", 14));
        linesField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 6;"
        );
        Label linesUnitLabel = new Label("行");
        linesUnitLabel.setFont(Font.font("Microsoft YaHei", 14));
        linesUnitLabel.setTextFill(Color.WHITE);
        linesBox.getChildren().addAll(linesField, linesUnitLabel);

        Button saveBtn = createIconBtn("保存设置", "#4CAF50", "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z");
        saveBtn.setOnAction(e -> {
            try {
                int limit = Integer.parseInt(limitField.getText().trim());
                int lines = Integer.parseInt(linesField.getText().trim());
                if (lines < 100) lines = 100;
                if (lines > 10000) lines = 10000;
                currentServer.setLogSizeLimit(limit);
                currentServer.setLogDisplayLines(lines);
                currentServer.enforceLogLinesLimit();
                mainApp.saveServers();
                showSuccess("设置已保存");
            } catch (NumberFormatException ex) {
                showError("请输入有效的数字");
            }
        });

        Button clearBtn = createIconBtn("立即清理", "#FF9800", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
        clearBtn.setOnAction(e -> {
            currentServer.clearLogCache();
            showSuccess("日志已清空");
            showLogSettingsPage();
        });

        Button openDirBtn = createIconBtn("打开日志目录", "#2196F3", "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z");
        openDirBtn.setOnAction(e -> {
            openDirectory(new File("msh/logs"));
        });

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER_LEFT);
        btnBox.getChildren().addAll(saveBtn, clearBtn, openDirBtn);

        settingsBox.getChildren().addAll(
            infoLabel,
            new Region() {{ setPrefHeight(10); }},
            limitLabel,
            limitHint,
            limitBox,
            new Region() {{ setPrefHeight(15); }},
            linesLabel,
            linesHint,
            linesBox,
            new Region() {{ setPrefHeight(10); }},
            btnBox
        );

        logSettingsView.getChildren().addAll(title, settingsBox);

        String transitionType = determineTransition("logsettings");
        switchView(logSettingsView, transitionType);
        currentPage = "logsettings";
    }

    public void showSettings() {
        showAppearanceSettings();
    }

    public void showAppearanceSettings() {
        VBox settingsView = new VBox(20);
        settingsView.setPadding(new Insets(20));
        settingsView.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("外观设置");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);

        VBox settingsBox = new VBox(25);
        settingsBox.setMaxWidth(500);
        settingsBox.setAlignment(Pos.TOP_CENTER);

        VBox bgSection = new VBox(12);
        bgSection.setAlignment(Pos.CENTER_LEFT);

        Label bgTitle = new Label("背景图片");
        bgTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        bgTitle.setTextFill(Color.WHITE);

        Label bgDesc = new Label("选择一张图片作为程序背景");
        bgDesc.setFont(Font.font("Microsoft YaHei", 12));
        bgDesc.setTextFill(Color.rgb(180, 180, 180));

        HBox bgControls = new HBox(12);
        bgControls.setAlignment(Pos.CENTER_LEFT);

        String bgName = mainApp.getBackgroundImageName();
        Label currentBgLabel = new Label("当前: " + (bgName.isEmpty() ? "默认" : bgName));
        currentBgLabel.setFont(Font.font("Microsoft YaHei", 12));
        currentBgLabel.setTextFill(Color.rgb(200, 200, 200));

        Button selectBgBtn = createSmallIconBtn("选择图片", "#2196F3", "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z");
        selectBgBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择背景图片");
            chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            File file = chooser.showOpenDialog(mainApp.getPrimaryStage());
            if (file != null) {
                mainApp.setBackgroundImage(file.getAbsolutePath());
                currentBgLabel.setText("当前: " + mainApp.getBackgroundImageName());
                showSuccess("背景图片已设置");
            }
        });

        Button clearBgBtn = createSmallIconBtn("恢复默认", "#757575", "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z");
        clearBgBtn.setOnAction(e -> {
            mainApp.setBackgroundImage("");
            currentBgLabel.setText("当前: 默认");
            showSuccess("已恢复默认背景");
        });

        bgControls.getChildren().addAll(currentBgLabel, selectBgBtn, clearBgBtn);

        Label bgTip = new Label("建议：选择 900x600 或 16:9 比例的图片以获得最佳效果");
        bgTip.setFont(Font.font("Microsoft YaHei", 11));
        bgTip.setTextFill(Color.rgb(150, 150, 150));

        bgSection.getChildren().addAll(bgTitle, bgDesc, bgControls, bgTip);

        VBox animSection = new VBox(12);
        animSection.setAlignment(Pos.CENTER_LEFT);

        Label animTitle = new Label("动画效果");
        animTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        animTitle.setTextFill(Color.WHITE);

        Label animDesc = new Label("点击按钮切换动画效果");
        animDesc.setFont(Font.font("Microsoft YaHei", 12));
        animDesc.setTextFill(Color.rgb(180, 180, 180));

        FlowPane animButtons = new FlowPane();
        animButtons.setHgap(10);
        animButtons.setVgap(10);
        animButtons.setPrefWrapLength(480);

        String currentMode = mainApp.getAnimationMode();
        String[][] animOptions = {
            {"左右滑动", "slide"},
            {"子页面", "subpage"},
            {"缩放", "scale"},
            {"弹性", "bounce"},
            {"淡入放大", "fade_zoom"},
            {"上滑", "slide_up"},
            {"3D翻转", "flip"},
            {"遮罩", "reveal"},
            {"模糊", "blur"},
            {"随机", "random"},
            {"关闭", "none"}
        };
        Button[] animBtns = new Button[animOptions.length];

        for (int i = 0; i < animOptions.length; i++) {
            String name = animOptions[i][0];
            String mode = animOptions[i][1];
            Button btn = new Button(name);
            btn.setPrefWidth(70);
            btn.setPrefHeight(32);
            btn.setFont(Font.font("Microsoft YaHei", 11));
            
            boolean isSelected = mode.equals(currentMode) || (mode.equals("slide") && !"subpage".equals(currentMode) && !"scale".equals(currentMode) && !"flip".equals(currentMode) && !"reveal".equals(currentMode) && !"blur".equals(currentMode) && !"bounce".equals(currentMode) && !"fade_zoom".equals(currentMode) && !"slide_up".equals(currentMode) && !"random".equals(currentMode) && !"none".equals(currentMode));
            
            updateAnimButtonStyle(btn, isSelected);
            
            final int idx = i;
            btn.setOnAction(e -> {
                mainApp.setAnimationMode(mode);
                for (int j = 0; j < animBtns.length; j++) {
                    updateAnimButtonStyle(animBtns[j], j == idx);
                }
                showSuccess("已切换到 " + name);
            });
            
            animBtns[i] = btn;
            animButtons.getChildren().add(btn);
        }

        animSection.getChildren().addAll(animTitle, animDesc, animButtons);

        VBox notifSection = new VBox(12);
        notifSection.setAlignment(Pos.CENTER_LEFT);

        Label notifTitle = new Label("通知显示时间");
        notifTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        notifTitle.setTextFill(Color.WHITE);

        Label notifDesc = new Label("设置通知弹窗的显示时长（1-30秒）");
        notifDesc.setFont(Font.font("Microsoft YaHei", 12));
        notifDesc.setTextFill(Color.rgb(180, 180, 180));

        HBox notifControls = new HBox(15);
        notifControls.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Slider notifSlider = new javafx.scene.control.Slider(1, 30, mainApp.getNotificationDuration());
        notifSlider.setPrefWidth(300);
        notifSlider.setMajorTickUnit(5);
        notifSlider.setMinorTickCount(4);
        notifSlider.setSnapToTicks(true);
        notifSlider.setShowTickLabels(false);
        notifSlider.setShowTickMarks(true);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.Node track = notifSlider.lookup(".track");
            if (track != null) {
                track.setStyle(
                    "-fx-background-color: rgba(100, 100, 100, 0.5);" +
                    "-fx-background-radius: 3;" +
                    "-fx-pref-height: 6;"
                );
            }
            javafx.scene.Node thumb = notifSlider.lookup(".thumb");
            if (thumb != null) {
                thumb.setStyle(
                    "-fx-background-color: #64B5F6;" +
                    "-fx-background-radius: 50%;" +
                    "-fx-pref-width: 18;" +
                    "-fx-pref-height: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"
                );
            }
        });

        notifSlider.setOnMouseEntered(e -> {
            e.consume();
            javafx.scene.Node thumb = notifSlider.lookup(".thumb");
            if (thumb != null) {
                thumb.setStyle(
                    "-fx-background-color: #90CAF9;" +
                    "-fx-background-radius: 50%;" +
                    "-fx-pref-width: 20;" +
                    "-fx-pref-height: 20;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 6, 0, 0, 3);"
                );
            }
        });
        notifSlider.setOnMouseExited(e -> {
            e.consume();
            javafx.scene.Node thumb = notifSlider.lookup(".thumb");
            if (thumb != null) {
                thumb.setStyle(
                    "-fx-background-color: #64B5F6;" +
                    "-fx-background-radius: 50%;" +
                    "-fx-pref-width: 18;" +
                    "-fx-pref-height: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"
                );
            }
        });

        Label notifValueLabel = new Label(mainApp.getNotificationDuration() + " 秒");
        notifValueLabel.setFont(Font.font("Microsoft YaHei", 14));
        notifValueLabel.setTextFill(Color.rgb(100, 180, 255));
        notifValueLabel.setPrefWidth(60);

        notifSlider.valueProperty().addListener((obs, old, val) -> {
            int value = val.intValue();
            notifValueLabel.setText(value + " 秒");
            mainApp.setNotificationDuration(value);
        });

        notifControls.getChildren().addAll(notifSlider, notifValueLabel);
        notifSection.getChildren().addAll(notifTitle, notifDesc, notifControls);

        VBox dialogAnimSection = new VBox(12);
        dialogAnimSection.setAlignment(Pos.CENTER_LEFT);

        Label dialogAnimTitle = new Label("弹窗动画");
        dialogAnimTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        dialogAnimTitle.setTextFill(Color.WHITE);

        Label dialogAnimDesc = new Label("选择弹窗的进入动画效果");
        dialogAnimDesc.setFont(Font.font("Microsoft YaHei", 12));
        dialogAnimDesc.setTextFill(Color.rgb(180, 180, 180));

        FlowPane dialogAnimButtons = new FlowPane();
        dialogAnimButtons.setHgap(10);
        dialogAnimButtons.setVgap(10);
        dialogAnimButtons.setPrefWrapLength(480);

        DialogAnimation currentDialogAnim = getDialogAnimation();
        String[][] dialogAnimOptions = {
            {"缩放", "SCALE"},
            {"放大", "ZOOM"},
            {"淡入", "FADE"},
            {"上滑", "SLIDE_UP"},
            {"下滑", "SLIDE_DOWN"},
            {"左滑", "SLIDE_LEFT"},
            {"右滑", "SLIDE_RIGHT"},
            {"弹跳", "BOUNCE"},
            {"翻转", "FLIP"},
            {"旋转", "ROTATE"},
            {"弹性", "ELASTIC"},
            {"果冻", "JELLO"},
            {"脉冲", "PULSE"},
            {"摇晃", "SHAKE"},
            {"摆动", "SWING"},
            {"摇摆", "WOBBLE"},
            {"心跳", "HEART_BEAT"},
            {"橡皮筋", "RUBBER_BAND"},
            {"惊喜", "TADA"},
            {"闪烁", "BLINK"},
            {"发光", "GLOW"},
            {"随机", "RANDOM"},
            {"无", "NONE"}
        };
        Button[] dialogAnimBtns = new Button[dialogAnimOptions.length];

        for (int i = 0; i < dialogAnimOptions.length; i++) {
            String name = dialogAnimOptions[i][0];
            String mode = dialogAnimOptions[i][1];
            Button btn = new Button(name);
            btn.setPrefWidth(70);
            btn.setPrefHeight(32);
            btn.setFont(Font.font("Microsoft YaHei", 11));

            boolean isSelected = mode.equals(currentDialogAnim.name());
            updateAnimButtonStyle(btn, isSelected);

            final int idx = i;
            btn.setOnAction(e -> {
                setDialogAnimation(DialogAnimation.valueOf(mode));
                for (int j = 0; j < dialogAnimBtns.length; j++) {
                    updateAnimButtonStyle(dialogAnimBtns[j], j == idx);
                }
                showSuccess("已切换到 " + name);
            });

            dialogAnimBtns[i] = btn;
            dialogAnimButtons.getChildren().add(btn);
        }

        dialogAnimSection.getChildren().addAll(dialogAnimTitle, dialogAnimDesc, dialogAnimButtons);

        settingsBox.getChildren().addAll(bgSection, animSection, notifSection, dialogAnimSection);

        ScrollPane scrollPane = new ScrollPane(settingsBox);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(480);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        settingsView.getChildren().addAll(title, scrollPane);

        String transitionType = determineTransition("appearance");
        switchView(settingsView, transitionType);
        currentPage = "appearance";

        if (sidebar != null) {
            sidebar.showSettingsSubPageNav(
                () -> {
                    mainApp.setCurrentPage("home");
                    showHome();
                },
                "appearance",
                () -> showAppearanceSettings(),
                () -> showUpdateSettings()
            );
        }
    }

    public void showUpdateSettings() {
        VBox updateView = new VBox(20);
        updateView.setPadding(new Insets(20));
        updateView.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("软件更新");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);

        VBox updateBox = new VBox(25);
        updateBox.setMaxWidth(500);
        updateBox.setAlignment(Pos.TOP_CENTER);

        VBox autoCheckSection = new VBox(12);
        autoCheckSection.setAlignment(Pos.CENTER_LEFT);

        Label autoCheckTitle = new Label("自动检查更新");
        autoCheckTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        autoCheckTitle.setTextFill(Color.WHITE);

        HBox autoCheckBox = new HBox(15);
        autoCheckBox.setAlignment(Pos.CENTER_LEFT);

        ToggleSwitch autoCheckSwitch = new ToggleSwitch();
        autoCheckSwitch.setSelected(mainApp.isAutoCheckUpdate());
        autoCheckSwitch.selectedProperty().addListener((obs, old, val) -> {
            mainApp.setAutoCheckUpdate(val);
            showSuccess(val ? "已开启自动检查更新" : "已关闭自动检查更新");
        });

        Label autoCheckDesc = new Label("启动时自动检查更新并提示");
        autoCheckDesc.setFont(Font.font("Microsoft YaHei", 12));
        autoCheckDesc.setTextFill(Color.rgb(180, 180, 180));

        autoCheckBox.getChildren().addAll(autoCheckSwitch, autoCheckDesc);
        autoCheckSection.getChildren().addAll(autoCheckTitle, autoCheckBox);

        VBox currentVersionSection = new VBox(8);
        currentVersionSection.setAlignment(Pos.CENTER_LEFT);

        Label currentVersionTitle = new Label("当前版本");
        currentVersionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        currentVersionTitle.setTextFill(Color.WHITE);

        Label currentVersionLabel = new Label(Main.VERSION);
        currentVersionLabel.setFont(Font.font("Microsoft YaHei", 14));
        currentVersionLabel.setTextFill(Color.rgb(100, 180, 255));

        currentVersionSection.getChildren().addAll(currentVersionTitle, currentVersionLabel);

        VBox latestVersionSection = new VBox(8);
        latestVersionSection.setAlignment(Pos.CENTER_LEFT);

        Label latestVersionTitle = new Label("最新版本");
        latestVersionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        latestVersionTitle.setTextFill(Color.WHITE);

        Label latestVersionLabel = new Label("加载中...");
        latestVersionLabel.setFont(Font.font("Microsoft YaHei", 14));
        latestVersionLabel.setTextFill(Color.rgb(180, 180, 180));

        latestVersionSection.getChildren().addAll(latestVersionTitle, latestVersionLabel);

        VBox checkSection = new VBox(8);
        checkSection.setAlignment(Pos.CENTER_LEFT);

        Label checkTitle = new Label("检查更新");
        checkTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        checkTitle.setTextFill(Color.WHITE);

        Label checkDesc = new Label("点击按钮检查是否有新版本可用");
        checkDesc.setFont(Font.font("Microsoft YaHei", 12));
        checkDesc.setTextFill(Color.rgb(180, 180, 180));

        Button checkBtn = createSmallIconBtn("检查更新", "#2196F3", "M12 16.5l4-4h-3v-6h-2v6H8l4 4zm-6 2h12v2H6v-2z");

        Label resultLabel = new Label("");
        resultLabel.setFont(Font.font("Microsoft YaHei", 13));
        resultLabel.setTextFill(Color.WHITE);
        resultLabel.setWrapText(true);

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setVisible(false);
        progressBar.setStyle(
            "-fx-accent: #2196F3;"
        );

        Button updateBtn = createSmallIconBtn("立即更新", "#4CAF50", "M12 16.5l4-4h-3v-6h-2v6H8l4 4zm-6 2h12v2H6v-2z");
        updateBtn.setVisible(false);

        UpdateChecker.UpdateResult[] checkResult = new UpdateChecker.UpdateResult[1];
        final javafx.scene.text.Text[] changelogContentRef = new javafx.scene.text.Text[1];

        checkBtn.setOnAction(e -> {
            resultLabel.setText("正在检查更新...");
            checkBtn.setDisable(true);
            updateBtn.setVisible(false);
            progressBar.setVisible(false);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            executor.submit(() -> {
                try {
                    UpdateChecker checker = new UpdateChecker(Main.VERSION);
                    checkResult[0] = checker.checkUpdate();

                    String changelogText = checker.fetchChangelog();

                    Platform.runLater(() -> {
                        checkBtn.setDisable(false);
                        latestVersionLabel.setText(checkResult[0].latestVersion);
                        latestVersionLabel.setTextFill(Color.rgb(100, 255, 100));
                        if (changelogContentRef[0] != null) {
                            changelogContentRef[0].setText(changelogText);
                        }

                        switch (checkResult[0].status) {
                            case FORCE_UPDATE -> {
                                resultLabel.setTextFill(Color.rgb(255, 100, 100));
                                resultLabel.setText("发现新版本: " + checkResult[0].latestVersion + " (强制更新)\n" +
                                        "发布日期: " + checkResult[0].updateDate + "\n" +
                                        "请尽快更新以获得最佳体验");
                                updateBtn.setVisible(true);
                            }
                            case OPTIONAL_UPDATE -> {
                                resultLabel.setTextFill(Color.rgb(100, 255, 100));
                                resultLabel.setText("发现新版本: " + checkResult[0].latestVersion + "\n" +
                                        "发布日期: " + checkResult[0].updateDate + "\n" +
                                        "可选择是否更新");
                                updateBtn.setVisible(true);
                            }
                            case NO_UPDATE -> {
                                resultLabel.setTextFill(Color.rgb(180, 180, 180));
                                resultLabel.setText("当前已是最新版本");
                                updateBtn.setVisible(false);
                            }
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        checkBtn.setDisable(false);
                        resultLabel.setTextFill(Color.rgb(255, 100, 100));
                        resultLabel.setText("检查更新失败: " + ex.getMessage());
                        showAlert("检查更新失败，请检查网络连接或稍后重试");
                    });
                }
                executor.shutdown();
            });
        });

        updateBtn.setOnAction(e -> {
            if (checkResult[0] == null) return;

            updateBtn.setDisable(true);
            checkBtn.setDisable(true);
            progressBar.setVisible(true);
            resultLabel.setText("正在下载更新...");
            resultLabel.setTextFill(Color.rgb(200, 200, 200));

            UpdateDownloader.downloadUpdate(
                checkResult[0].downloadUrl,
                checkResult[0].sha256,
                checkResult[0].latestVersion,
                new UpdateDownloader.DownloadCallback() {
                    @Override
                    public void onProgress(int percentage, String speed) {
                        Platform.runLater(() -> progressBar.setProgress(percentage / 100.0));
                    }

                    @Override
                    public void onComplete(boolean success, String message) {
                    }

                    @Override
                    public void onComplete(boolean success, String message, String newJarPath) {
                        Platform.runLater(() -> {
                            if (success) {
                                resultLabel.setTextFill(Color.rgb(100, 255, 100));
                                resultLabel.setText(message);
                            } else {
                                resultLabel.setTextFill(Color.rgb(255, 100, 100));
                                resultLabel.setText(message);
                                updateBtn.setDisable(false);
                                checkBtn.setDisable(false);
                            }
                        });
                    }
                }
            );
        });

        checkSection.getChildren().addAll(checkTitle, checkDesc, checkBtn, resultLabel, progressBar, updateBtn);

        VBox changelogSection = new VBox(8);
        changelogSection.setAlignment(Pos.CENTER_LEFT);

        Label changelogTitle = new Label("更新日志");
        changelogTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        changelogTitle.setTextFill(Color.WHITE);

        VBox changelogBox = new VBox(10);
        changelogBox.setAlignment(Pos.TOP_LEFT);
        changelogBox.setPadding(new Insets(15));
        changelogBox.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-background-radius: 8;"
        );

        javafx.scene.text.Text changelogContent = new javafx.scene.text.Text("点击检查更新按钮获取更新日志...");
        changelogContent.setFont(Font.font("Microsoft YaHei", 12));
        changelogContent.setFill(javafx.scene.paint.Color.rgb(200, 200, 200));
        changelogContent.setWrappingWidth(400);
        changelogContentRef[0] = changelogContent;

        VBox textContainer = new VBox(changelogContent);
        textContainer.setPrefWidth(400);
        textContainer.setMaxWidth(400);

        ScrollPane changelogScroll = new ScrollPane(textContainer);
        changelogScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        changelogScroll.setFitToWidth(false);
        changelogScroll.setPrefHeight(150);
        changelogScroll.setMaxHeight(150);
        changelogScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        changelogScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        changelogBox.getChildren().add(changelogScroll);
        changelogSection.getChildren().addAll(changelogTitle, changelogBox);

        updateBox.getChildren().addAll(autoCheckSection, currentVersionSection, latestVersionSection, checkSection, changelogSection);
        updateView.getChildren().addAll(title, updateBox);

        ScrollPane scroll = new ScrollPane(updateView);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setFitToWidth(true);
        scroll.setMaxWidth(680);
        scroll.setPrefHeight(520);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        String transitionType = determineTransition("update");
        switchView(scroll, transitionType);
        currentPage = "update";

        if (sidebar != null) {
            sidebar.showSettingsSubPageNav(
                () -> {
                    mainApp.setCurrentPage("home");
                    showHome();
                },
                "update",
                () -> showAppearanceSettings(),
                () -> showUpdateSettings()
            );
        }
    }

    private List<String> detectSystemJava() {
        List<String> javaList = new ArrayList<>();

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-version");
            pb.redirectErrorStream(true);
            process = pb.start();
            boolean finished = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            if (finished) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && (line.contains("java") || line.contains("openjdk"))) {
                        javaList.add("系统默认 - java");
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }

        String[] commonPaths;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            commonPaths = new String[]{
                System.getenv("ProgramFiles") + "\\Java",
                System.getenv("ProgramFiles(x86)") + "\\Java",
                "C:\\Program Files\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft"
            };
        } else {
            commonPaths = new String[]{
                "/usr/lib/jvm",
                "/usr/java",
                "/opt/java"
            };
        }
        
        for (String basePath : commonPaths) {
            if (basePath == null) continue;
            File baseDir = new File(basePath);
            if (baseDir.exists() && baseDir.isDirectory()) {
                File[] subDirs = baseDir.listFiles();
                if (subDirs != null) {
                    for (File subDir : subDirs) {
                        if (subDir.isDirectory()) {
                            File javaExe = new File(subDir, "bin/java.exe");
                            File javaFile = new File(subDir, "bin/java");
                            if (javaExe.exists()) {
                                javaList.add(subDir.getName() + " - " + javaExe.getAbsolutePath());
                            } else if (javaFile.exists()) {
                                javaList.add(subDir.getName() + " - " + javaFile.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        }
        
        return javaList;
    }

    private boolean verifyJavaPath(String javaPath) {
        File javaFile = new File(javaPath);
        if (!javaFile.exists()) {
            return false;
        }
        final Process[] processHolder = new Process[1];
        java.util.concurrent.ExecutorService readExecutor = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(javaPath, "-version");
            pb.redirectErrorStream(true);
            processHolder[0] = pb.start();

            readExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
            final java.util.concurrent.ExecutorService executorRef = readExecutor;
            java.util.concurrent.Future<String> future = readExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(processHolder[0].getInputStream()))) {
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    return output.toString();
                } catch (java.io.IOException e) {
                    if (processHolder[0] != null && processHolder[0].isAlive()) {
                        processHolder[0].destroyForcibly();
                    }
                    executorRef.shutdownNow();
                    return null;
                }
            });

            String result;
            try {
                result = future.get(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                return false;
            } catch (java.util.concurrent.CancellationException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            if (result == null) {
                return false;
            }
            return result.contains("java") || result.contains("openjdk");
        } catch (Exception e) {
            return false;
        } finally {
            if (readExecutor != null) {
                readExecutor.shutdownNow();
            }
            if (processHolder[0] != null && processHolder[0].isAlive()) {
                processHolder[0].destroyForcibly();
            }
        }
    }

    private GameRulesManager gameRulesManager;
    private VBox gameRulesContainer;
    private Label gameRulesVersionLabel;
    
    public void showGameRulesPage() {
        currentServer = serverManager.getSelected();
        if (currentServer == null) {
            showAlert("请先选择一个服务器");
            return;
        }
        
        VBox gameRulesView = new VBox(15);
        gameRulesView.setPadding(new Insets(10));
        
        Label title = new Label("游戏规则 - " + currentServer.getName());
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);
        
        HBox versionBox = new HBox(12);
        versionBox.setAlignment(Pos.CENTER_LEFT);
        
        gameRulesVersionLabel = new Label("正在加载配置...");
        gameRulesVersionLabel.setFont(Font.font("Microsoft YaHei", 12));
        gameRulesVersionLabel.setTextFill(Color.rgb(150, 200, 255));
        
        Button refreshBtn = createSmallIconBtn("刷新规则", "#2196F3", "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z");
        refreshBtn.setOnAction(e -> loadGameRulesForServer(currentServer));
        
        Button refreshConfigBtn = createSmallIconBtn("更新配置", "#FF9800", "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L5.03 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z");
        refreshConfigBtn.setOnAction(e -> {
            if (gameRulesManager != null) {
                gameRulesManager.refreshIndex();
                loadGameRulesForServer(currentServer);
                showSuccess("配置文件已更新");
            }
        });
        
        versionBox.getChildren().addAll(gameRulesVersionLabel, refreshBtn, refreshConfigBtn);
        
        gameRulesContainer = new VBox(10);
        gameRulesContainer.setPadding(new Insets(10));
        
        VBox loadingBox = new VBox(15);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(100, 0, 0, 0));
        Label loadingLabel = new Label("正在加载游戏规则配置...");
        loadingLabel.setFont(Font.font("Microsoft YaHei", 14));
        loadingLabel.setTextFill(Color.rgb(150, 200, 255));
        loadingBox.getChildren().add(loadingLabel);
        gameRulesContainer.getChildren().add(loadingBox);
        
        ScrollPane scrollPane = new ScrollPane(gameRulesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setMaxWidth(680);
        scrollPane.setPrefHeight(420);
        scrollPane.setStyle(
            "-fx-background: transparent;" +
            "-fx-background-color: transparent;"
        );
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        gameRulesView.getChildren().addAll(title, versionBox, scrollPane);
        
        String transitionType = determineTransition("gamerules");
        switchView(gameRulesView, transitionType);
        currentPage = "gamerules";
        
        if (sidebar != null) {
            sidebar.showSubPageNav(
                currentServer.getName(),
                () -> {
                    mainApp.setCurrentPage("list");
                    showServerList();
                },
                "gamerules",
                () -> showConsole(),
                () -> showConfig(),
                () -> showLogSettingsPage(),
                () -> showGameRulesPage(),
                () -> showJavaSettingsPage(),
                () -> showServerAddress()
            );
        }

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            if (gameRulesManager == null) {
                gameRulesManager = new GameRulesManager();
            }
            Platform.runLater(() -> loadGameRulesForServer(currentServer));
            executor.shutdown();
        });
    }
    
    private void loadGameRulesForServer(ServerCore server) {
        if (server == null || gameRulesContainer == null) return;
        
        gameRulesContainer.getChildren().clear();
        
        boolean serverRunning = server.isRunning();
        
        if (!serverRunning) {
            gameRulesVersionLabel.setText("服务器未运行，请启动服务器后查看游戏规则");
            
            VBox tipBox = new VBox(15);
            tipBox.setAlignment(Pos.CENTER);
            tipBox.setPadding(new Insets(100, 0, 0, 0));
            
            Label tipLabel = new Label("⚠ 服务器未运行");
            tipLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
            tipLabel.setTextFill(Color.ORANGE);
            
            Label descLabel = new Label("请启动服务器后再查看游戏规则");
            descLabel.setFont(Font.font("Microsoft YaHei", 13));
            descLabel.setTextFill(Color.rgb(180, 180, 180));
            
            tipBox.getChildren().addAll(tipLabel, descLabel);
            gameRulesContainer.getChildren().add(tipBox);
            return;
        }
        
        String version = server.getDetectedVersion();
        if (version == null || version.isEmpty()) {
            version = "1.21.11";
        }
        
        String matchedVersion = gameRulesManager.getMatchedVersion(version);
        gameRulesVersionLabel.setText("检测版本: " + version + " (使用规则: " + matchedVersion + ") - 服务器运行中");
        
        List<GameRuleItem> rules = gameRulesManager.getGameRules(version);
        
        if (rules.isEmpty()) {
            Label emptyLabel = new Label("无法加载游戏规则，请检查网络连接");
            emptyLabel.setFont(Font.font("Microsoft YaHei", 14));
            emptyLabel.setTextFill(Color.GRAY);
            gameRulesContainer.getChildren().add(emptyLabel);
            return;
        }
        
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            for (GameRuleItem rule : rules) {
                String currentValue = server.queryGameRuleValue(rule.getName());
                if (currentValue != null) {
                    if (rule.isBoolean()) {
                        rule.setCurrentValue(Boolean.parseBoolean(currentValue));
                    } else if (rule.isInteger()) {
                        try {
                            rule.setCurrentValue(Integer.parseInt(currentValue));
                        } catch (NumberFormatException e) {
                        }
                    }
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            Platform.runLater(() -> {
                gameRulesContainer.getChildren().clear();
                for (GameRuleItem rule : rules) {
                    HBox ruleBox = createGameRuleBox(rule, server);
                    gameRulesContainer.getChildren().add(ruleBox);
                }
            });
            executor.shutdown();
        });
    }
    
    private HBox createGameRuleBox(GameRuleItem rule, ServerCore server) {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12));
        box.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-background-radius: 8;"
        );
        
        VBox infoBox = new VBox(5);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        Label nameLabel = new Label(rule.getDisplayName());
        nameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        nameLabel.setTextFill(Color.WHITE);
        
        Label descLabel = new Label(rule.getDescription());
        descLabel.setFont(Font.font("Microsoft YaHei", 11));
        descLabel.setTextFill(Color.rgb(180, 180, 180));
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(400);
        
        infoBox.getChildren().addAll(nameLabel, descLabel);
        
        javafx.scene.Node control;
        
        if (rule.isBoolean()) {
            ToggleSwitch toggle = new ToggleSwitch();
            toggle.setSelected(rule.getBooleanValue());
            toggle.selectedProperty().addListener((obs, old, val) -> {
                rule.setCurrentValue(val);
                applyGameRule(rule, server);
            });
            control = toggle;
        } else if (rule.isInteger()) {
            HBox numBox = new HBox(8);
            numBox.setAlignment(Pos.CENTER);
            
            Slider slider = new Slider();
            if (rule.getMinValue() != null) slider.setMin(rule.getMinValue());
            if (rule.getMaxValue() != null) slider.setMax(rule.getMaxValue());
            slider.setValue(rule.getIntegerValue());
            slider.setPrefWidth(120);
            slider.setStyle(
                "-fx-control-inner-background: rgba(255,255,255,0.2);"
            );
            
            TextField numField = new TextField(String.valueOf(rule.getIntegerValue()));
            numField.setPrefWidth(60);
            numField.setStyle(
                "-fx-background-color: rgba(255,255,255,0.1);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 4;"
            );
            
            slider.valueProperty().addListener((obs, old, val) -> {
                int intVal = val.intValue();
                numField.setText(String.valueOf(intVal));
                rule.setCurrentValue(intVal);
            });
            
            numField.setOnAction(e -> {
                try {
                    int val = Integer.parseInt(numField.getText());
                    if (rule.getMinValue() != null && val < rule.getMinValue()) val = rule.getMinValue();
                    if (rule.getMaxValue() != null && val > rule.getMaxValue()) val = rule.getMaxValue();
                    slider.setValue(val);
                    rule.setCurrentValue(val);
                    applyGameRule(rule, server);
                } catch (NumberFormatException ex) {
                    numField.setText(String.valueOf(rule.getIntegerValue()));
                }
            });
            
            numField.focusedProperty().addListener((obs, old, focused) -> {
                if (!focused) {
                    try {
                        int val = Integer.parseInt(numField.getText());
                        if (rule.getMinValue() != null && val < rule.getMinValue()) val = rule.getMinValue();
                        if (rule.getMaxValue() != null && val > rule.getMaxValue()) val = rule.getMaxValue();
                        slider.setValue(val);
                        rule.setCurrentValue(val);
                        applyGameRule(rule, server);
                    } catch (NumberFormatException ex) {
                        numField.setText(String.valueOf(rule.getIntegerValue()));
                    }
                }
            });
            
            numBox.getChildren().addAll(slider, numField);
            control = numBox;
        } else {
            control = new Label("未知类型");
        }
        
        box.getChildren().addAll(infoBox, control);
        
        box.setOnMouseEntered(e -> box.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-background-radius: 8;"
        ));
        box.setOnMouseExited(e -> box.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-background-radius: 8;"
        ));
        
        return box;
    }
    
    private void applyGameRule(GameRuleItem rule, ServerCore server) {
        if (server == null || !server.isRunning()) {
            showAlert("服务器未运行，无法应用规则");
            return;
        }
        
        String command = rule.getCommand();
        if (command != null) {
            server.sendCommand(command);
            showSuccess("已应用规则: " + rule.getDisplayName());
        }
    }

    public void showAbout() {
        VBox aboutView = new VBox(30);
        aboutView.setAlignment(Pos.CENTER);
        aboutView.setPadding(new Insets(40));
        
        Label title = new Label("MSH2");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 56));
        title.setTextFill(Color.WHITE);
        DropShadow glow = new DropShadow(20, Color.rgb(100, 180, 255, 0.8));
        glow.setSpread(0.3);
        title.setEffect(glow);
        
        Label version = new Label("版本 " + Main.VERSION);
        version.setFont(Font.font("Microsoft YaHei", 14));
        version.setTextFill(Color.rgb(180, 180, 180));
        
        Label desc = new Label("Minecraft Server Hub 2 - 服务器管理工具");
        desc.setFont(Font.font("Microsoft YaHei", 13));
        desc.setTextFill(Color.rgb(150, 150, 150));
        
        VBox infoBox = new VBox(8);
        infoBox.setAlignment(Pos.CENTER);
        infoBox.getChildren().addAll(version, desc);
        
        aboutView.getChildren().addAll(title, infoBox);

        String transitionType = determineTransition("about");
        switchView(aboutView, transitionType);
        currentPage = "about";

        if (sidebar != null) {
            sidebar.hideSubPageNav();
        }

        javafx.application.Platform.runLater(() -> {
            AnimationUtils.applyGlowPulse(title, glow);

            java.util.List<javafx.scene.Node> infoNodes = java.util.Arrays.asList(version, desc);
            AnimationUtils.applyStaggeredFadeUp(infoNodes, 150);
        });
    }

    private Button createSmallIconBtn(String text, String color, String iconPath) {
        Button btn = new Button(text);
        btn.setPrefHeight(32);
        btn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        btn.setTextFill(Color.WHITE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 8, 0, 6));

        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent(iconPath);
        icon.setFill(Color.WHITE);
        icon.setScaleX(0.5);
        icon.setScaleY(0.5);
        btn.setGraphic(icon);
        btn.setGraphicTextGap(4);

        String rgbaColor = colorToRgba(color, 0.7);
        btn.setStyle(
            "-fx-background-color: " + rgbaColor + ";" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + rgbaColor + ";" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        return btn;
    }

    private Button createIconBtn(String text, String color, String iconPath) {
        Button btn = new Button(text);
        btn.setPrefHeight(32);
        btn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        btn.setTextFill(Color.WHITE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 10, 0, 8));

        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent(iconPath);
        icon.setFill(Color.WHITE);
        icon.setScaleX(0.5);
        icon.setScaleY(0.5);
        btn.setGraphic(icon);
        btn.setGraphicTextGap(4);

        String rgbaColor = colorToRgba(color, 0.7);
        btn.setStyle(
            "-fx-background-color: " + rgbaColor + ";" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + rgbaColor + ";" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        return btn;
    }
    
    private String colorToRgba(String hex, double alpha) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return String.format("rgba(%d,%d,%d,%.1f)", r, g, b, alpha);
    }
    
    private void showAlert(String msg) {
        if (mainApp != null) {
            mainApp.showNotification(msg, "info");
        }
    }

    private void showSuccess(String msg) {
        if (mainApp != null) {
            mainApp.showNotification(msg, "success");
        }
    }

    private void showError(String msg) {
        if (mainApp != null) {
            mainApp.showNotification(msg, "error");
        }
    }

    private boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }

    private void openDirectory(File dir) {
        try {
            if (isLinux()) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
            } else {
                java.awt.Desktop.getDesktop().open(dir);
            }
        } catch (Exception ex) {
            showError("无法打开目录: " + ex.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            if (isLinux()) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            } else {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception ex) {
            showError("无法打开链接: " + ex.getMessage());
        }
    }
    
    private void showConfirmDialog(String title, String message, Runnable onConfirm, String linkText, String linkUrl) {
        showConfirmDialog(title, message, onConfirm, linkText, linkUrl, "#4CAF50", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z");
    }
    
    private void showConfirmDialog(String title, String message, Runnable onConfirm, String linkText, String linkUrl, String iconColor, String iconPath) {
        if (rootContainer == null) {
            System.out.println("rootContainer is null, cannot show dialog");
            return;
        }

        Platform.runLater(() -> {
            StackPane overlay = new StackPane();
            overlay.setStyle("-fx-background-color: rgba(0,0,0,0.5);");
            overlay.setPrefSize(720, 568);
            overlay.setAlignment(Pos.CENTER);

            VBox dialog = new VBox(12);
            dialog.setAlignment(Pos.CENTER);
            dialog.setPadding(new Insets(20, 25, 20, 25));
            dialog.setMaxWidth(linkUrl != null ? 360 : 320);
            dialog.setMaxHeight(Region.USE_PREF_SIZE);
            dialog.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 6;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
            );

            javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
            icon.setContent(iconPath);
            icon.setFill(Color.web(iconColor));
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
            msgLabel.setMaxWidth(linkUrl != null ? 310 : 270);

            HBox btnBox = new HBox(15);
            btnBox.setAlignment(Pos.CENTER);
            btnBox.setPadding(new Insets(10, 0, 0, 0));

            Button cancelBtn = new Button("取消");
            cancelBtn.setFont(Font.font("Microsoft YaHei", 13));
            cancelBtn.setTextFill(Color.web("#666666"));
            cancelBtn.setPrefWidth(90);
            cancelBtn.setStyle(
                "-fx-background-color: #f0f0f0;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 20;"
            );
            cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(
                "-fx-background-color: #e0e0e0;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 20;"
            ));
            cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(
                "-fx-background-color: #f0f0f0;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 20;"
            ));
            cancelBtn.setOnAction(e -> rootContainer.getChildren().remove(overlay));

            Button confirmBtn = new Button("确定");
            confirmBtn.setFont(Font.font("Microsoft YaHei", 13));
            confirmBtn.setTextFill(Color.WHITE);
            confirmBtn.setPrefWidth(90);
            String hoverColor = iconColor.equals("#4CAF50") ? "#45a049" : 
                               iconColor.equals("#FF9800") ? "#f57c00" : 
                               iconColor.equals("#f44336") ? "#d32f2f" : iconColor;
            confirmBtn.setStyle(
                "-fx-background-color: " + iconColor + ";" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 20;"
            );
            confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(
                "-fx-background-color: " + hoverColor + ";" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 20;"
            ));
            confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(
                "-fx-background-color: " + iconColor + ";" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 8 20;"
            ));
            confirmBtn.setOnAction(e -> {
                rootContainer.getChildren().remove(overlay);
                if (onConfirm != null) {
                    onConfirm.run();
                }
            });

            btnBox.getChildren().addAll(cancelBtn, confirmBtn);

            if (linkUrl != null && linkText != null) {
                javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(linkText);
                link.setFont(Font.font("Microsoft YaHei", 13));
                link.setTextFill(Color.web("#2196F3"));
                link.setOnAction(e -> {
                    openUrl(linkUrl);
                });
                dialog.getChildren().addAll(icon, titleLabel, msgLabel, link, btnBox);
            } else {
                dialog.getChildren().addAll(icon, titleLabel, msgLabel, btnBox);
            }
            
            overlay.getChildren().add(dialog);
            overlay.setOnMouseClicked(e -> {
                if (e.getTarget() == overlay) {
                    rootContainer.getChildren().remove(overlay);
                }
            });
            
            rootContainer.getChildren().add(overlay);
            animateDialog(dialog, dialogAnimation);
        });
    }

    public enum DialogAnimation {
        FADE, SCALE, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, ZOOM,
        BOUNCE, FLIP, ROTATE, ELASTIC, JELLO, PULSE, SHAKE, SWING, WOBBLE,
        HEART_BEAT, RUBBER_BAND, TADA, BLINK, GLOW, NONE, RANDOM
    }

    private DialogAnimation dialogAnimation = DialogAnimation.SCALE;

    public void setDialogAnimation(DialogAnimation animation) {
        this.dialogAnimation = animation;
        saveDialogAnimation();
    }

    public DialogAnimation getDialogAnimation() {
        return dialogAnimation;
    }

    private void saveDialogAnimation() {
        try {
            File configFile = new File("msh/config.json");
            String content = "{}";
            if (configFile.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            } else {
                configFile.getParentFile().mkdirs();
            }

            String animName = dialogAnimation.name();
            if (content.contains("\"dialogAnimation\"")) {
                int start = content.indexOf("\"dialogAnimation\"") + 19;
                int end = content.indexOf("\"", start);
                if (end > start) {
                    content = content.substring(0, start) + animName + content.substring(end);
                } else {
                    content = content.replace("}", ",\"dialogAnimation\":\"" + animName + "\"}");
                }
            } else {
                content = content.replace("}", ",\"dialogAnimation\":\"" + animName + "\"}");
            }

            java.nio.file.Files.write(configFile.toPath(), content.getBytes());
        } catch (Exception e) {
            System.out.println("保存弹窗动画配置失败: " + e.getMessage());
        }
    }

    private void loadDialogAnimation() {
        try {
            File configFile = new File("msh/config.json");
            if (configFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                if (content.contains("\"dialogAnimation\"")) {
                    int start = content.indexOf("\"dialogAnimation\"") + 19;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        String animName = content.substring(start, end);
                        dialogAnimation = DialogAnimation.valueOf(animName);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("加载弹窗动画配置失败: " + e.getMessage());
        }
    }

    private void animateDialog(VBox dialog, DialogAnimation animation) {
        Duration duration = Duration.millis(250);

        dialog.setScaleX(1);
        dialog.setScaleY(1);
        dialog.setTranslateX(0);
        dialog.setTranslateY(0);
        dialog.setRotate(0);

        switch (animation) {
            case FADE -> {
                dialog.setOpacity(0);
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                ft.play();
            }
            case SCALE -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.8);
                dialog.setScaleY(0.8);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                ScaleTransition st = new ScaleTransition(duration, dialog);
                st.setFromX(0.8);
                st.setFromY(0.8);
                st.setToX(1);
                st.setToY(1);
                pt.getChildren().addAll(ft, st);
                pt.play();
            }
            case ZOOM -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.5);
                dialog.setScaleY(0.5);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                ScaleTransition st = new ScaleTransition(duration, dialog);
                st.setFromX(0.5);
                st.setFromY(0.5);
                st.setToX(1);
                st.setToY(1);
                pt.getChildren().addAll(ft, st);
                pt.play();
            }
            case SLIDE_UP -> {
                dialog.setOpacity(0);
                dialog.setTranslateY(50);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                TranslateTransition tt = new TranslateTransition(duration, dialog);
                tt.setFromY(50);
                tt.setToY(0);
                pt.getChildren().addAll(ft, tt);
                pt.play();
            }
            case SLIDE_DOWN -> {
                dialog.setOpacity(0);
                dialog.setTranslateY(-50);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                TranslateTransition tt = new TranslateTransition(duration, dialog);
                tt.setFromY(-50);
                tt.setToY(0);
                pt.getChildren().addAll(ft, tt);
                pt.play();
            }
            case SLIDE_LEFT -> {
                dialog.setOpacity(0);
                dialog.setTranslateX(50);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                TranslateTransition tt = new TranslateTransition(duration, dialog);
                tt.setFromX(50);
                tt.setToX(0);
                pt.getChildren().addAll(ft, tt);
                pt.play();
            }
            case SLIDE_RIGHT -> {
                dialog.setOpacity(0);
                dialog.setTranslateX(-50);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                TranslateTransition tt = new TranslateTransition(duration, dialog);
                tt.setFromX(-50);
                tt.setToX(0);
                pt.getChildren().addAll(ft, tt);
                pt.play();
            }
            case BOUNCE -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.3);
                dialog.setScaleY(0.3);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO,
                    new KeyValue(dialog.opacityProperty(), 0),
                    new KeyValue(dialog.scaleXProperty(), 0.3),
                    new KeyValue(dialog.scaleYProperty(), 0.3));
                KeyFrame kf2 = new KeyFrame(Duration.millis(150),
                    new KeyValue(dialog.opacityProperty(), 1),
                    new KeyValue(dialog.scaleXProperty(), 1.1),
                    new KeyValue(dialog.scaleYProperty(), 1.1));
                KeyFrame kf3 = new KeyFrame(Duration.millis(250),
                    new KeyValue(dialog.scaleXProperty(), 1),
                    new KeyValue(dialog.scaleYProperty(), 1));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3);
                timeline.play();
            }
            case FLIP -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                ScaleTransition st = new ScaleTransition(duration, dialog);
                st.setFromX(0);
                st.setFromY(1);
                st.setToX(1);
                st.setToY(1);
                pt.getChildren().addAll(ft, st);
                pt.play();
            }
            case ROTATE -> {
                dialog.setOpacity(0);
                dialog.setRotate(-180);
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(duration, dialog);
                ft.setFromValue(0);
                ft.setToValue(1);
                RotateTransition rt = new RotateTransition(duration, dialog);
                rt.setFromAngle(-180);
                rt.setToAngle(0);
                pt.getChildren().addAll(ft, rt);
                pt.play();
            }
            case ELASTIC -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0);
                dialog.setScaleY(0);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO,
                    new KeyValue(dialog.opacityProperty(), 0),
                    new KeyValue(dialog.scaleXProperty(), 0),
                    new KeyValue(dialog.scaleYProperty(), 0));
                KeyFrame kf2 = new KeyFrame(Duration.millis(100),
                    new KeyValue(dialog.opacityProperty(), 1),
                    new KeyValue(dialog.scaleXProperty(), 1.2),
                    new KeyValue(dialog.scaleYProperty(), 1.2));
                KeyFrame kf3 = new KeyFrame(Duration.millis(200),
                    new KeyValue(dialog.scaleXProperty(), 0.9),
                    new KeyValue(dialog.scaleYProperty(), 0.9));
                KeyFrame kf4 = new KeyFrame(Duration.millis(300),
                    new KeyValue(dialog.scaleXProperty(), 1),
                    new KeyValue(dialog.scaleYProperty(), 1));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4);
                timeline.play();
            }
            case JELLO -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.8);
                dialog.setScaleY(0.8);
                dialog.setRotate(-5);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO,
                    new KeyValue(dialog.opacityProperty(), 0),
                    new KeyValue(dialog.scaleXProperty(), 0.8),
                    new KeyValue(dialog.scaleYProperty(), 0.8),
                    new KeyValue(dialog.rotateProperty(), -5));
                KeyFrame kf2 = new KeyFrame(Duration.millis(100),
                    new KeyValue(dialog.opacityProperty(), 1),
                    new KeyValue(dialog.scaleXProperty(), 1.05),
                    new KeyValue(dialog.scaleYProperty(), 0.95),
                    new KeyValue(dialog.rotateProperty(), 5));
                KeyFrame kf3 = new KeyFrame(Duration.millis(200),
                    new KeyValue(dialog.scaleXProperty(), 0.95),
                    new KeyValue(dialog.scaleYProperty(), 1.05),
                    new KeyValue(dialog.rotateProperty(), -3));
                KeyFrame kf4 = new KeyFrame(Duration.millis(300),
                    new KeyValue(dialog.scaleXProperty(), 1.02),
                    new KeyValue(dialog.scaleYProperty(), 0.98),
                    new KeyValue(dialog.rotateProperty(), 2));
                KeyFrame kf5 = new KeyFrame(Duration.millis(400),
                    new KeyValue(dialog.scaleXProperty(), 1),
                    new KeyValue(dialog.scaleYProperty(), 1),
                    new KeyValue(dialog.rotateProperty(), 0));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5);
                timeline.play();
            }
            case PULSE -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.8);
                dialog.setScaleY(0.8);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO,
                    new KeyValue(dialog.opacityProperty(), 0),
                    new KeyValue(dialog.scaleXProperty(), 0.8),
                    new KeyValue(dialog.scaleYProperty(), 0.8));
                KeyFrame kf2 = new KeyFrame(Duration.millis(125),
                    new KeyValue(dialog.opacityProperty(), 1),
                    new KeyValue(dialog.scaleXProperty(), 1.05),
                    new KeyValue(dialog.scaleYProperty(), 1.05));
                KeyFrame kf3 = new KeyFrame(Duration.millis(250),
                    new KeyValue(dialog.scaleXProperty(), 1),
                    new KeyValue(dialog.scaleYProperty(), 1));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3);
                timeline.play();
            }
            case SHAKE -> {
                dialog.setOpacity(0);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO, new KeyValue(dialog.opacityProperty(), 0));
                KeyFrame kf2 = new KeyFrame(Duration.millis(100), new KeyValue(dialog.opacityProperty(), 1));
                KeyFrame kf3 = new KeyFrame(Duration.millis(130), new KeyValue(dialog.translateXProperty(), -10));
                KeyFrame kf4 = new KeyFrame(Duration.millis(160), new KeyValue(dialog.translateXProperty(), 10));
                KeyFrame kf5 = new KeyFrame(Duration.millis(190), new KeyValue(dialog.translateXProperty(), -10));
                KeyFrame kf6 = new KeyFrame(Duration.millis(220), new KeyValue(dialog.translateXProperty(), 10));
                KeyFrame kf7 = new KeyFrame(Duration.millis(250), new KeyValue(dialog.translateXProperty(), -5));
                KeyFrame kf8 = new KeyFrame(Duration.millis(280), new KeyValue(dialog.translateXProperty(), 5));
                KeyFrame kf9 = new KeyFrame(Duration.millis(310), new KeyValue(dialog.translateXProperty(), 0));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5, kf6, kf7, kf8, kf9);
                timeline.play();
            }
            case SWING -> {
                dialog.setOpacity(0);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO, new KeyValue(dialog.opacityProperty(), 0));
                KeyFrame kf2 = new KeyFrame(Duration.millis(100), new KeyValue(dialog.opacityProperty(), 1));
                KeyFrame kf3 = new KeyFrame(Duration.millis(150), new KeyValue(dialog.rotateProperty(), 15));
                KeyFrame kf4 = new KeyFrame(Duration.millis(200), new KeyValue(dialog.rotateProperty(), -10));
                KeyFrame kf5 = new KeyFrame(Duration.millis(250), new KeyValue(dialog.rotateProperty(), 5));
                KeyFrame kf6 = new KeyFrame(Duration.millis(300), new KeyValue(dialog.rotateProperty(), -5));
                KeyFrame kf7 = new KeyFrame(Duration.millis(350), new KeyValue(dialog.rotateProperty(), 0));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5, kf6, kf7);
                timeline.play();
            }
            case WOBBLE -> {
                dialog.setOpacity(0);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO, new KeyValue(dialog.opacityProperty(), 0));
                KeyFrame kf2 = new KeyFrame(Duration.millis(100), new KeyValue(dialog.opacityProperty(), 1));
                KeyFrame kf3 = new KeyFrame(Duration.millis(150), new KeyValue(dialog.translateXProperty(), -25), new KeyValue(dialog.rotateProperty(), -5));
                KeyFrame kf4 = new KeyFrame(Duration.millis(200), new KeyValue(dialog.translateXProperty(), 20), new KeyValue(dialog.rotateProperty(), 3));
                KeyFrame kf5 = new KeyFrame(Duration.millis(250), new KeyValue(dialog.translateXProperty(), -15), new KeyValue(dialog.rotateProperty(), -3));
                KeyFrame kf6 = new KeyFrame(Duration.millis(300), new KeyValue(dialog.translateXProperty(), 10), new KeyValue(dialog.rotateProperty(), 2));
                KeyFrame kf7 = new KeyFrame(Duration.millis(350), new KeyValue(dialog.translateXProperty(), -5), new KeyValue(dialog.rotateProperty(), -1));
                KeyFrame kf8 = new KeyFrame(Duration.millis(400), new KeyValue(dialog.translateXProperty(), 0), new KeyValue(dialog.rotateProperty(), 0));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5, kf6, kf7, kf8);
                timeline.play();
            }
            case HEART_BEAT -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.8);
                dialog.setScaleY(0.8);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO,
                    new KeyValue(dialog.opacityProperty(), 0),
                    new KeyValue(dialog.scaleXProperty(), 0.8),
                    new KeyValue(dialog.scaleYProperty(), 0.8));
                KeyFrame kf2 = new KeyFrame(Duration.millis(100),
                    new KeyValue(dialog.opacityProperty(), 1),
                    new KeyValue(dialog.scaleXProperty(), 1.15),
                    new KeyValue(dialog.scaleYProperty(), 1.15));
                KeyFrame kf3 = new KeyFrame(Duration.millis(200),
                    new KeyValue(dialog.scaleXProperty(), 0.9),
                    new KeyValue(dialog.scaleYProperty(), 0.9));
                KeyFrame kf4 = new KeyFrame(Duration.millis(300),
                    new KeyValue(dialog.scaleXProperty(), 1.05),
                    new KeyValue(dialog.scaleYProperty(), 1.05));
                KeyFrame kf5 = new KeyFrame(Duration.millis(400),
                    new KeyValue(dialog.scaleXProperty(), 1),
                    new KeyValue(dialog.scaleYProperty(), 1));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5);
                timeline.play();
            }
            case RUBBER_BAND -> {
                dialog.setOpacity(0);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO, new KeyValue(dialog.opacityProperty(), 0));
                KeyFrame kf2 = new KeyFrame(Duration.millis(100), new KeyValue(dialog.opacityProperty(), 1));
                KeyFrame kf3 = new KeyFrame(Duration.millis(150), new KeyValue(dialog.scaleXProperty(), 1.25), new KeyValue(dialog.scaleYProperty(), 0.75));
                KeyFrame kf4 = new KeyFrame(Duration.millis(200), new KeyValue(dialog.scaleXProperty(), 0.75), new KeyValue(dialog.scaleYProperty(), 1.25));
                KeyFrame kf5 = new KeyFrame(Duration.millis(250), new KeyValue(dialog.scaleXProperty(), 1.15), new KeyValue(dialog.scaleYProperty(), 0.85));
                KeyFrame kf6 = new KeyFrame(Duration.millis(300), new KeyValue(dialog.scaleXProperty(), 0.95), new KeyValue(dialog.scaleYProperty(), 1.05));
                KeyFrame kf7 = new KeyFrame(Duration.millis(350), new KeyValue(dialog.scaleXProperty(), 1.02), new KeyValue(dialog.scaleYProperty(), 0.98));
                KeyFrame kf8 = new KeyFrame(Duration.millis(400), new KeyValue(dialog.scaleXProperty(), 1), new KeyValue(dialog.scaleYProperty(), 1));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5, kf6, kf7, kf8);
                timeline.play();
            }
            case TADA -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.5);
                dialog.setScaleY(0.5);
                dialog.setRotate(-15);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO,
                    new KeyValue(dialog.opacityProperty(), 0),
                    new KeyValue(dialog.scaleXProperty(), 0.5),
                    new KeyValue(dialog.scaleYProperty(), 0.5),
                    new KeyValue(dialog.rotateProperty(), -15));
                KeyFrame kf2 = new KeyFrame(Duration.millis(150),
                    new KeyValue(dialog.opacityProperty(), 1),
                    new KeyValue(dialog.scaleXProperty(), 0.9),
                    new KeyValue(dialog.scaleYProperty(), 0.9),
                    new KeyValue(dialog.rotateProperty(), -5));
                KeyFrame kf3 = new KeyFrame(Duration.millis(250),
                    new KeyValue(dialog.scaleXProperty(), 1.1),
                    new KeyValue(dialog.scaleYProperty(), 1.1),
                    new KeyValue(dialog.rotateProperty(), 3));
                KeyFrame kf4 = new KeyFrame(Duration.millis(350),
                    new KeyValue(dialog.scaleXProperty(), 0.95),
                    new KeyValue(dialog.scaleYProperty(), 0.95),
                    new KeyValue(dialog.rotateProperty(), -2));
                KeyFrame kf5 = new KeyFrame(Duration.millis(450),
                    new KeyValue(dialog.scaleXProperty(), 1),
                    new KeyValue(dialog.scaleYProperty(), 1),
                    new KeyValue(dialog.rotateProperty(), 0));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5);
                timeline.play();
            }
            case BLINK -> {
                dialog.setOpacity(0);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO, new KeyValue(dialog.opacityProperty(), 0));
                KeyFrame kf2 = new KeyFrame(Duration.millis(80), new KeyValue(dialog.opacityProperty(), 1));
                KeyFrame kf3 = new KeyFrame(Duration.millis(160), new KeyValue(dialog.opacityProperty(), 0.3));
                KeyFrame kf4 = new KeyFrame(Duration.millis(240), new KeyValue(dialog.opacityProperty(), 1));
                KeyFrame kf5 = new KeyFrame(Duration.millis(320), new KeyValue(dialog.opacityProperty(), 0.5));
                KeyFrame kf6 = new KeyFrame(Duration.millis(400), new KeyValue(dialog.opacityProperty(), 1));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5, kf6);
                timeline.play();
            }
            case GLOW -> {
                dialog.setOpacity(0);
                dialog.setScaleX(0.9);
                dialog.setScaleY(0.9);
                Timeline timeline = new Timeline();
                KeyFrame kf1 = new KeyFrame(Duration.ZERO,
                    new KeyValue(dialog.opacityProperty(), 0),
                    new KeyValue(dialog.scaleXProperty(), 0.9),
                    new KeyValue(dialog.scaleYProperty(), 0.9));
                KeyFrame kf2 = new KeyFrame(Duration.millis(150),
                    new KeyValue(dialog.opacityProperty(), 0.8),
                    new KeyValue(dialog.scaleXProperty(), 1.02),
                    new KeyValue(dialog.scaleYProperty(), 1.02));
                KeyFrame kf3 = new KeyFrame(Duration.millis(300),
                    new KeyValue(dialog.opacityProperty(), 1),
                    new KeyValue(dialog.scaleXProperty(), 1),
                    new KeyValue(dialog.scaleYProperty(), 1));
                timeline.getKeyFrames().addAll(kf1, kf2, kf3);
                timeline.play();
            }
            case NONE -> {
                dialog.setOpacity(1);
            }
            case RANDOM -> {
                DialogAnimation[] allAnims = DialogAnimation.values();
                java.util.List<DialogAnimation> validAnims = new java.util.ArrayList<>();
                for (DialogAnimation anim : allAnims) {
                    if (anim != DialogAnimation.NONE && anim != DialogAnimation.RANDOM) {
                        validAnims.add(anim);
                    }
                }
                DialogAnimation randomAnim = validAnims.get((int)(Math.random() * validAnims.size()));
                animateDialog(dialog, randomAnim);
            }
        }
    }

    private StackPane rootContainer;

    public void setRootContainer(StackPane container) {
        this.rootContainer = container;
    }

    public void setSidebar(Sidebar sidebar) {
        this.sidebar = sidebar;
    }
    
    private int getPageIndex(String page) {
        return switch (page) {
            case "home" -> 0;
            case "list" -> 1;
            case "add" -> 2;
            case "gamerules" -> 3;
            case "console" -> 4;
            case "config" -> 5;
            case "settings" -> 6;
            case "about" -> 7;
            case "logsettings" -> 8;
            case "appearance" -> 9;
            case "update" -> 10;
            case "address" -> 11;
            default -> -1;
        };
    }
    
    private String determineTransition(String targetPage) {
        String animMode = mainApp.getAnimationMode();
        if ("none".equals(animMode)) {
            return "none";
        }
        
        if ("scale".equals(animMode) || "flip".equals(animMode) || "reveal".equals(animMode) || "blur".equals(animMode) || "bounce".equals(animMode) || "fade_zoom".equals(animMode) || "slide_up".equals(animMode) || "random".equals(animMode)) {
            return animMode;
        }
        
        if (currentPage.isEmpty()) {
            return "fade";
        }
        
        int currentIdx = getPageIndex(currentPage);
        int targetIdx = getPageIndex(targetPage);
        
        if (currentIdx < 0 || targetIdx < 0) {
            return "fade";
        }
        
        boolean isCurrentSubPage = isSubPage(currentPage);
        boolean isTargetSubPage = isSubPage(targetPage);
        boolean isEnteringSubPage = isTargetSubPage && !isCurrentSubPage;
        boolean isExitingSubPage = isCurrentSubPage && !isTargetSubPage;
        boolean isSwitchingSubPage = isCurrentSubPage && isTargetSubPage;
        
        if (isEnteringSubPage) {
            return "forward";
        } else if (isExitingSubPage) {
            return "backward";
        } else if (isSwitchingSubPage) {
            if (targetIdx > currentIdx) {
                return "swipe_left";
            } else if (targetIdx < currentIdx) {
                return "swipe_right";
            } else {
                return "fade";
            }
        } else if (targetIdx > currentIdx) {
            return "swipe_left";
        } else if (targetIdx < currentIdx) {
            return "swipe_right";
        } else {
            return "fade";
        }
    }
    
    private boolean isSubPage(String page) {
        return page.equals("console") || page.equals("config") || page.equals("logsettings") || page.equals("gamerules") || page.equals("java") || page.equals("appearance") || page.equals("update") || page.equals("address");
    }
    
    private void switchView(Region newView, String transitionType) {
        long currentTime = System.currentTimeMillis();

        if (isAnimating || (currentTime - lastSwitchTime) < MIN_SWITCH_INTERVAL) {
            return;
        }

        if (verifyExecutor != null && !verifyExecutor.isShutdown()) {
            verifyExecutor.shutdownNow();
            verifyExecutor = null;
        }

        lastSwitchTime = currentTime;
        newView.setMaxWidth(680);
        newView.setMaxHeight(528);
        newView.setOpacity(0);

        if (currentView != null) {
            isAnimating = true;

            Region oldView = currentView;

            if (currentAnimation != null) {
                currentAnimation.stop();
            }

            AnimationUtils.resetNodeState(oldView);
            AnimationUtils.resetNodeState(newView);

            getChildren().add(newView);

            if ("none".equals(transitionType)) {
                currentView = newView;
                getChildren().remove(oldView);
                isAnimating = false;
                return;
            }

            String finalTransitionType = transitionType;
            if ("random".equals(transitionType)) {
                String[] effects = {"scale", "flip", "reveal", "blur", "bounce", "fade_zoom", "slide_up", "fade", "swipe_left", "swipe_right"};
                String selectedEffect;
                do {
                    selectedEffect = effects[(int)(Math.random() * effects.length)];
                } while (selectedEffect.equals(lastRandomEffect));
                lastRandomEffect = selectedEffect;
                finalTransitionType = selectedEffect;
            }
            
            AnimationUtils.TransitionEffect effect = switch (finalTransitionType) {
                case "swipe_left" -> AnimationUtils.SWIPE_LEFT;
                case "swipe_right" -> AnimationUtils.SWIPE_RIGHT;
                case "forward" -> AnimationUtils.FORWARD;
                case "backward" -> AnimationUtils.BACKWARD;
                case "scale" -> AnimationUtils.SCALE;
                case "flip" -> AnimationUtils.FLIP;
                case "reveal" -> AnimationUtils.REVEAL;
                case "blur" -> AnimationUtils.BLUR;
                case "bounce" -> AnimationUtils.BOUNCE;
                case "fade_zoom" -> AnimationUtils.FADE_ZOOM;
                case "slide_up" -> AnimationUtils.SLIDE_UP;
                default -> AnimationUtils.FADE;
            };

            currentAnimation = effect.create(this, oldView, newView);
            currentAnimation.setOnFinished(e -> {
                isAnimating = false;
                currentAnimation = null;
                currentView = newView;
                getChildren().remove(oldView);
            });
            currentAnimation.play();
        } else {
            currentView = newView;
            getChildren().add(newView);

            FadeTransition fade = new FadeTransition(Duration.millis(300), newView);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
    }
    
    private void fadeIn(Node node, double millis) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(millis), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void updateAnimButtonStyle(Button btn, boolean selected) {
        if (selected) {
            btn.setStyle(
                "-fx-background-color: #4CAF50;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            );
        } else {
            btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.15);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;"
            );
        }
    }

    private String publicIpAddress = null;
    private boolean publicIpWarningAccepted = false;
    private volatile boolean isRefreshingPublicIp = false;

    public void showServerAddress() {
        currentServer = serverManager.getSelected();
        if (currentServer == null) {
            showAlert("请先选择一个服务器");
            return;
        }

        VBox addressView = new VBox(15);
        addressView.setPadding(new Insets(10));
        addressView.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("服务器地址信息");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        Label statusLabel = new Label("地址获取完成");
        statusLabel.setFont(Font.font("Microsoft YaHei", 14));
        statusLabel.setTextFill(Color.LIGHTGREEN);

        int port = getServerPort();

        ScrollPane scroll = new ScrollPane();
        scroll.setStyle(
            "-fx-background: transparent;" +
            "-fx-background-color: transparent;"
        );
        scroll.setFitToWidth(true);
        scroll.setMaxWidth(680);
        scroll.setPrefHeight(420);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(10));

        HBox localBox = createAddressBox("本地回环", "127.0.0.1:" + port, "只能在自己这台电脑上连接，适合测试服务器或单机游戏", Color.LIGHTGREEN);
        HBox lanBox = createAddressBox("内网地址", getLanAddress() + ":" + port, "同一局域网内的设备可以连接，适合家庭或宿舍联机", Color.LIGHTGREEN);

        HBox publicBox = new HBox(12);
        publicBox.setAlignment(Pos.CENTER_LEFT);
        publicBox.setPadding(new Insets(12));
        publicBox.setStyle(
            "-fx-background-color: rgba(255,200,200,0.08);" +
            "-fx-border-color: rgba(255,100,100,0.3);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        );

        VBox publicInfo = new VBox(5);
        publicInfo.setMaxWidth(420);
        HBox.setHgrow(publicInfo, Priority.ALWAYS);

        Label publicTitle = new Label("公网地址");
        publicTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        publicTitle.setTextFill(Color.web("#ff6b6b"));

        Label publicValue = new Label(publicIpWarningAccepted && publicIpAddress != null ? publicIpAddress + ":" + port : "点击右侧按钮并同意免责声明后显示");
        publicValue.setFont(Font.font("Microsoft YaHei", 12));
        publicValue.setTextFill(publicIpWarningAccepted && publicIpAddress != null ? Color.WHITE : Color.web("#ff9999"));
        publicValue.setStyle(publicIpWarningAccepted && publicIpAddress != null ? "" : "-fx-font-style: italic;");
        publicValue.setWrapText(true);
        publicValue.setMaxWidth(400);

        publicInfo.getChildren().addAll(publicTitle, publicValue);

        Button showPublicBtn = createSmallIconBtn("显示公网地址", "#ff4757", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z");
        showPublicBtn.setOnAction(e -> {
            if (!publicIpWarningAccepted && !"获取中...".equals(publicIpAddress)) {
                publicIpWarningAccepted = true;
                publicIpAddress = "获取中...";
                showServerAddress();
                showPublicIpWarning(() -> {
                    refreshPublicIp(port);
                });
            }
        });

        Button copyPublicBtn = createSmallIconBtn("复制", "#4CAF50", "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z");
        copyPublicBtn.setOnAction(e -> {
            if (publicIpAddress != null) {
                copyToClipboard(publicIpAddress + ":" + port);
                showSuccess("公网地址已复制");
            }
        });
        copyPublicBtn.setDisable(!publicIpWarningAccepted || publicIpAddress == null);

        HBox publicBtnBox = new HBox(8);
        publicBtnBox.setAlignment(Pos.CENTER_RIGHT);
        publicBtnBox.getChildren().addAll(showPublicBtn, copyPublicBtn);

        publicBox.getChildren().addAll(publicInfo, publicBtnBox);

        VBox descriptionBox = new VBox(10);
        descriptionBox.setPadding(new Insets(15, 0, 0, 0));

        Label descTitle = new Label("连接说明:");
        descTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        descTitle.setTextFill(Color.WHITE);

        VBox descContent = new VBox(8);
        descContent.getChildren().addAll(
            createDescItem("本地回环地址", "只能在自己这台电脑上连接，适合测试服务器或单机游戏"),
            createDescItem("内网地址", "同一WiFi或局域网内的设备可以连接，适合家庭或宿舍联机"),
            createDescItem("公网地址", "互联网上的任何设备都可以连接，需要进行端口映射才能使用。注意：即使显示公网地址，连接也不一定成功，取决于你的网络环境")
        );

        VBox solutionBox = new VBox(10);
        solutionBox.setPadding(new Insets(15, 0, 0, 0));

        Label solutionTitle = new Label("公网连接失败的解决办法:");
        solutionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        solutionTitle.setTextFill(Color.WHITE);

        VBox solutionContent = new VBox(8);
        solutionContent.getChildren().addAll(
            createDescItem("1. 内网穿透工具", "使用 SakuraFrp、花生壳、Ngrok 等工具，无需公网IP即可让外网访问"),
            createDescItem("2. 端口映射", "在路由器设置中将服务器端口映射到公网，需要路由器有公网IP"),
            createDescItem("3. 虚拟局域网", "使用 Radmin VPN、Hamachi、ZeroTier 等工具创建虚拟局域网，好友加入后即可联机"),
            createDescItem("4. 云服务器", "购买云服务器（阿里云、腾讯云等）部署服务器，稳定但需付费")
        );

        solutionBox.getChildren().addAll(solutionTitle, solutionContent);

        descriptionBox.getChildren().addAll(descTitle, descContent, solutionBox);

        contentBox.getChildren().addAll(localBox, lanBox, publicBox, descriptionBox);
        scroll.setContent(contentBox);

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        Button refreshBtn = createIconBtn("刷新地址", "#2196F3", "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z");
        refreshBtn.setPrefWidth(110);
        refreshBtn.setPrefHeight(42);
        refreshBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        refreshBtn.setOnAction(e -> {
            if (!isRefreshingPublicIp) {
                publicIpAddress = null;
                publicIpWarningAccepted = false;
                showServerAddress();
            }
        });

        btnBox.getChildren().addAll(refreshBtn);

        addressView.getChildren().addAll(title, statusLabel, scroll, btnBox);

        String transitionType = determineTransition("address");
        switchView(addressView, transitionType);
        currentPage = "address";

        if (sidebar != null) {
            sidebar.showSubPageNav(
                currentServer.getName(),
                () -> {
                    publicIpWarningAccepted = false;
                    publicIpAddress = null;
                    isRefreshingPublicIp = false;
                    mainApp.setCurrentPage("list");
                    showServerList();
                },
                "address",
                () -> {
                    publicIpWarningAccepted = false;
                    publicIpAddress = null;
                    isRefreshingPublicIp = false;
                    showConsole();
                },
                () -> {
                    publicIpWarningAccepted = false;
                    publicIpAddress = null;
                    isRefreshingPublicIp = false;
                    showConfig();
                },
                () -> {
                    publicIpWarningAccepted = false;
                    publicIpAddress = null;
                    isRefreshingPublicIp = false;
                    showLogSettingsPage();
                },
                () -> {
                    publicIpWarningAccepted = false;
                    publicIpAddress = null;
                    isRefreshingPublicIp = false;
                    showGameRulesPage();
                },
                () -> {
                    publicIpWarningAccepted = false;
                    publicIpAddress = null;
                    isRefreshingPublicIp = false;
                    showJavaSettingsPage();
                },
                () -> showServerAddress()
            );
        }
    }

    private HBox createAddressBox(String title, String address, String description, Color titleColor) {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12));
        box.setStyle(
            "-fx-background-color: rgba(200,255,200,0.08);" +
            "-fx-border-color: rgba(100,255,100,0.2);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        );

        VBox info = new VBox(5);
        info.setMaxWidth(420);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        titleLabel.setTextFill(titleColor);

        Label addressLabel = new Label(address);
        addressLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        addressLabel.setTextFill(Color.WHITE);
        addressLabel.setWrapText(true);
        addressLabel.setMaxWidth(400);

        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("Microsoft YaHei", 11));
        descLabel.setTextFill(Color.rgb(180, 180, 180));
        descLabel.setWrapText(true);

        info.getChildren().addAll(titleLabel, addressLabel, descLabel);

        Button copyBtn = createSmallIconBtn("复制", "#4CAF50", "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z");
        copyBtn.setOnAction(e -> {
            copyToClipboard(address);
            showSuccess(title + "已复制");
        });

        box.getChildren().addAll(info, copyBtn);

        box.setOnMouseEntered(e -> box.setStyle(
            "-fx-background-color: rgba(200,255,200,0.12);" +
            "-fx-border-color: rgba(100,255,100,0.3);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        ));
        box.setOnMouseExited(e -> box.setStyle(
            "-fx-background-color: rgba(200,255,200,0.08);" +
            "-fx-border-color: rgba(100,255,100,0.2);" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        ));

        return box;
    }

    private VBox createDescItem(String title, String desc) {
        VBox item = new VBox(3);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.rgb(200, 200, 200));

        Label descLabel = new Label(desc);
        descLabel.setFont(Font.font("Microsoft YaHei", 11));
        descLabel.setTextFill(Color.rgb(150, 150, 150));
        descLabel.setWrapText(true);
        descLabel.setPadding(new Insets(0, 0, 0, 0));

        item.getChildren().addAll(titleLabel, descLabel);
        return item;
    }

    private int getServerPort() {
        try {
            if (currentServer == null) return 25565;
            File configFile = GameRules.getConfigFile(currentServer.getWorkingDir());
            if (configFile != null && configFile.exists()) {
                Map<String, String> props = GameRules.loadProperties(configFile);
                if (props != null) {
                    String portStr = props.get("server-port");
                    if (portStr != null && !portStr.trim().isEmpty()) {
                        try {
                            int port = Integer.parseInt(portStr.trim());
                            if (port > 0 && port <= 65535) {
                                return port;
                            }
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return 25565;
    }

    private String getLanAddress() {
        try {
            String bestIp = null;
            int bestPriority = Integer.MAX_VALUE;

            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();

                if (iface.isLoopback() || !iface.isUp()) continue;

                String name = iface.getName().toLowerCase();
                String displayName = iface.getDisplayName().toLowerCase();

                int priority = getInterfacePriority(name, displayName);
                if (priority == -1) continue;

                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (isValidLanAddress(ip) && priority < bestPriority) {
                            bestIp = ip;
                            bestPriority = priority;
                        }
                    }
                }
            }

            return bestIp != null ? bestIp : "127.0.0.1";
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private int getInterfacePriority(String name, String displayName) {
        if (name.contains("vmware") || name.contains("virtual") || name.contains("vbox") ||
            name.contains("hyper-v") || name.contains("docker") || name.contains("tap") ||
            name.contains("tun") || name.contains("ppp") || name.contains("vpn") ||
            name.contains("zero") || name.contains("hamachi") || name.contains("radmin")) {
            return -1;
        }

        if (name.startsWith("eth") || name.startsWith("en")) return 1;
        if (name.startsWith("wlan") || name.startsWith("wl")) return 2;
        if (name.startsWith("wifi")) return 3;
        if (name.contains("ethernet")) return 1;
        if (name.contains("wireless")) return 2;

        return 10;
    }

    private boolean isValidLanAddress(String ip) {
        if (ip == null || ip.isEmpty()) return false;

        if (ip.startsWith("127.")) return false;
        if (ip.startsWith("169.254.")) return false;
        if (ip.startsWith("0.")) return false;
        if (ip.startsWith("255.")) return false;
        if (ip.startsWith("224.")) return false;
        if (ip.startsWith("239.")) return false;
        if (ip.startsWith("240.")) return false;

        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;

        try {
            int[] nums = new int[4];
            for (int i = 0; i < 4; i++) {
                nums[i] = Integer.parseInt(parts[i]);
                if (nums[i] < 0 || nums[i] > 255) return false;
            }

            if (nums[0] == 10) return true;
            if (nums[0] == 172 && nums[1] >= 16 && nums[1] <= 31) return true;
            if (nums[0] == 192 && nums[1] == 168) return true;

            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void refreshPublicIp(int port) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        isRefreshingPublicIp = true;
        executor.submit(() -> {
            String[] ipApis = {
                "https://api.ipify.org",
                "https://ip.3322.net",
                "https://4.ipw.cn",
                "https://api-ipv4.ip.sb/ip",
                "https://checkip.amazonaws.com",
                "https://icanhazip.com",
                "https://ifconfig.me/ip"
            };

            String ip = null;
            String lastError = "";

            for (String apiUrl : ipApis) {
                try {
                    java.net.URL url = new java.net.URL(apiUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(conn.getInputStream()))) {
                            String response = reader.readLine();
                            if (response != null && !response.trim().isEmpty()) {
                                response = response.trim();
                                if (isValidIpAddress(response)) {
                                    ip = response;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                }
            }

            if (ip != null) {
                publicIpAddress = ip;
            } else {
                publicIpAddress = "获取失败";
            }
            isRefreshingPublicIp = false;

            Platform.runLater(() -> {
                showServerAddress();
            });

            executor.shutdown();
        });
    }

    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) return false;

        if (ip.startsWith("127.")) return false;
        if (ip.startsWith("10.")) return false;
        if (ip.startsWith("192.168.")) return false;
        if (ip.startsWith("169.254.")) return false;
        if (ip.startsWith("0.")) return false;
        if (ip.startsWith("255.")) return false;

        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            if (first == 172 && second >= 16 && second <= 31) return false;

            if (first < 1 || first > 223) return false;

            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void showPublicIpWarning(Runnable onAccept) {
        if (rootContainer == null) return;

        Platform.runLater(() -> {
            StackPane overlay = new StackPane();
            overlay.setStyle("-fx-background-color: rgba(0,0,0,0.6);");
            overlay.setPrefSize(720, 568);
            overlay.setAlignment(Pos.CENTER);

            VBox dialog = new VBox(15);
            dialog.setAlignment(Pos.TOP_CENTER);
            dialog.setPadding(new Insets(25));
            dialog.setMaxWidth(480);
            dialog.setMaxHeight(Region.USE_PREF_SIZE);
            dialog.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #fff5f5, #ffffff);" +
                "-fx-background-radius: 12;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 25, 0, 0, 8);"
            );

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);

            javafx.scene.shape.Circle warningIcon = new javafx.scene.shape.Circle(12);
            warningIcon.setFill(Color.web("#ff4757"));

            Label headerTitle = new Label("公网地址安全警告");
            headerTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
            headerTitle.setTextFill(Color.web("#c0392b"));

            header.getChildren().addAll(warningIcon, headerTitle);

            Label dangerTitle = new Label("⚠ 极危险警告");
            dangerTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
            dangerTitle.setTextFill(Color.web("#e74c3c"));

            VBox riskBox = new VBox(12);
            riskBox.setPadding(new Insets(15));
            riskBox.setStyle(
                "-fx-background-color: rgba(231, 76, 60, 0.08);" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: rgba(231, 76, 60, 0.3);" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;"
            );

            Label riskIntro = new Label("在显示公网地址之前，请务必了解以下风险:");
            riskIntro.setFont(Font.font("Microsoft YaHei", 12));
            riskIntro.setTextFill(Color.web("#555555"));
            riskIntro.setWrapText(true);

            VBox securityRisks = createRiskSection("安全风险:", new String[]{
                "服务器将暴露在互联网上，任何人都可能访问",
                "可能遭受黑客攻击、恶意入侵或数据窃取",
                "你的个人电脑面临严重安全威胁",
                "可能导致个人隐私泄露或电脑被远程控制"
            });

            VBox networkRisks = createRiskSection("网络风险:", new String[]{
                "可能遭受DDoS攻击，导致网络瘫痪",
                "流量激增可能产生高额网络费用",
                "路由器可能被恶意配置或攻击"
            });

            riskBox.getChildren().addAll(riskIntro, securityRisks, networkRisks);

            javafx.scene.control.CheckBox agreeBox = new javafx.scene.control.CheckBox("我已阅读并完全理解上述所有风险和警告，自愿承担所有责任");
            agreeBox.setFont(Font.font("Microsoft YaHei", 12));
            agreeBox.setTextFill(Color.web("#333333"));
            agreeBox.setWrapText(true);

            HBox btnBox = new HBox(15);
            btnBox.setAlignment(Pos.CENTER);
            btnBox.setPadding(new Insets(10, 0, 0, 0));

            Button cancelBtn = new Button("取消");
            cancelBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            cancelBtn.setTextFill(Color.WHITE);
            cancelBtn.setPrefWidth(100);
            cancelBtn.setStyle(
                "-fx-background-color: #95a5a6;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 10 25;"
            );
            cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(
                "-fx-background-color: #7f8c8d;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 10 25;"
            ));
            cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(
                "-fx-background-color: #95a5a6;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 10 25;"
            ));
            cancelBtn.setOnAction(e -> rootContainer.getChildren().remove(overlay));

            Button acceptBtn = new Button("我同意");
            acceptBtn.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            acceptBtn.setTextFill(Color.WHITE);
            acceptBtn.setPrefWidth(100);
            acceptBtn.setDisable(true);
            acceptBtn.setStyle(
                "-fx-background-color: #e74c3c;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 10 25;"
            );
            acceptBtn.setOnMouseEntered(e -> {
                if (!acceptBtn.isDisable()) {
                    acceptBtn.setStyle(
                        "-fx-background-color: #c0392b;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 10 25;"
                    );
                }
            });
            acceptBtn.setOnMouseExited(e -> acceptBtn.setStyle(
                "-fx-background-color: #e74c3c;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 10 25;"
            ));
            acceptBtn.setOnAction(e -> {
                rootContainer.getChildren().remove(overlay);
                if (onAccept != null) {
                    onAccept.run();
                }
            });

            agreeBox.selectedProperty().addListener((obs, old, val) -> {
                acceptBtn.setDisable(!val);
            });

            btnBox.getChildren().addAll(acceptBtn, cancelBtn);

            dialog.getChildren().addAll(header, dangerTitle, riskBox, agreeBox, btnBox);

            overlay.getChildren().add(dialog);
            overlay.setOnMouseClicked(e -> {
                if (e.getTarget() == overlay) {
                    rootContainer.getChildren().remove(overlay);
                }
            });

            rootContainer.getChildren().add(overlay);

            dialog.setScaleX(0.8);
            dialog.setScaleY(0.8);
            dialog.setOpacity(0);

            javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition();

            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(300), dialog);
            st.setToX(1);
            st.setToY(1);

            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), dialog);
            ft.setToValue(1);

            pt.getChildren().addAll(st, ft);
            pt.play();
        });
    }

    private VBox createRiskSection(String title, String[] risks) {
        VBox section = new VBox(6);

        Label titleLabel = new Label("⚠ " + title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        titleLabel.setTextFill(Color.web("#e67e22"));

        VBox riskList = new VBox(4);
        riskList.setPadding(new Insets(0, 0, 0, 15));

        for (String risk : risks) {
            Label riskLabel = new Label("• " + risk);
            riskLabel.setFont(Font.font("Microsoft YaHei", 11));
            riskLabel.setTextFill(Color.web("#666666"));
            riskLabel.setWrapText(true);
            riskList.getChildren().add(riskLabel);
        }

        section.getChildren().addAll(titleLabel, riskList);
        return section;
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

}
