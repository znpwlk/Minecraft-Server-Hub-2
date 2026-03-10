import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.Cursor;
import javafx.animation.*;
import javafx.util.Duration;

public class Sidebar extends VBox {
    private ContentPanel contentPanel;
    private VBox menuContainer;
    private Button selectedBtn;
    private VBox serverSection, sysSection;
    private Label serverLabel, sysLabel;
    private Button homeBtn, listBtn, addBtn, settingsBtn, aboutBtn;
    private VBox subPageNav;
    private boolean isSubPageMode = false;
    private boolean isNavAnimating = false;

    public Sidebar(Main mainApp) {
        setPrefWidth(180);
        setPrefHeight(568);
        setMinHeight(568);
        setMaxHeight(568);
        setPadding(new Insets(20, 14, 20, 14));
        setSpacing(4);
        setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.08);" +
            "-fx-background-radius: 0 16 16 0;" +
            "-fx-border-color: rgba(255, 255, 255, 0.08);" +
            "-fx-border-width: 0 1 0 0;"
        );

        setupHeader();
        setupMenu();
        setupFooter();

        AnimationUtils.applySlideInFromLeft(this, 400);
    }
    
    private void setupHeader() {
        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 15, 0));
        
        Label title = new Label("MSH2");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 26));
        title.setTextFill(Color.WHITE);
        title.setEffect(new DropShadow(8, Color.rgb(100, 180, 255, 0.6)));
        
        Label subtitle = new Label("Minecraft Server Hub");
        subtitle.setFont(Font.font("Microsoft YaHei", 11));
        subtitle.setTextFill(Color.rgb(180, 180, 180));
        
        header.getChildren().addAll(title, subtitle);
        getChildren().add(header);
        
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: rgba(255,255,255,0.15);");
        sep.setPadding(new Insets(8, 0, 8, 0));
        getChildren().add(sep);
    }
    
    private void setupMenu() {
        menuContainer = new VBox(6);
        menuContainer.setPadding(new Insets(8, 0, 8, 0));
        
        serverLabel = createSectionLabel("服务器管理");
        serverSection = new VBox(6);
        homeBtn = createMenuBtn("首页", getIcon("home"), true);
        listBtn = createMenuBtn("服务器列表", getIcon("list"), false);
        addBtn = createMenuBtn("添加服务器", getIcon("add"), false);
        serverSection.getChildren().addAll(homeBtn, listBtn, addBtn);
        
        sysLabel = createSectionLabel("系统");
        sysSection = new VBox(6);
        settingsBtn = createMenuBtn("设置", getIcon("settings"), false);
        aboutBtn = createMenuBtn("关于", getIcon("about"), false);
        sysSection.getChildren().addAll(settingsBtn, aboutBtn);
        
        homeBtn.setOnAction(e -> { selectBtn(homeBtn, "home"); contentPanel.showHome(); });
        listBtn.setOnAction(e -> { selectBtn(listBtn, "list"); contentPanel.showServerList(); });
        addBtn.setOnAction(e -> { selectBtn(addBtn, "add"); contentPanel.showAddServer(); });
        settingsBtn.setOnAction(e -> { selectBtn(settingsBtn, "settings"); contentPanel.showSettings(); });
        aboutBtn.setOnAction(e -> { selectBtn(aboutBtn, "about"); contentPanel.showAbout(); });
        
        menuContainer.getChildren().addAll(
            serverLabel, serverSection,
            sysLabel, sysSection
        );
        
        getChildren().add(menuContainer);
        VBox.setVgrow(menuContainer, Priority.ALWAYS);
        
        selectedBtn = homeBtn;
        highlightSection("home");
    }
    
    public void setCurrentPage(String page) {
        Button targetBtn = switch (page) {
            case "home" -> homeBtn;
            case "list" -> listBtn;
            case "add" -> addBtn;
            case "settings" -> settingsBtn;
            case "about" -> aboutBtn;
            default -> null;
        };
        
        if (targetBtn != null) {
            selectBtn(targetBtn, page);
        } else {
            highlightSection(page);
        }
    }
    
    private void highlightSection(String page) {
        resetSectionLabels();

        String section = switch (page) {
            case "home", "list", "add" -> "server";
            case "settings", "about" -> "sys";
            default -> "";
        };

        switch (section) {
            case "server" -> serverLabel.setTextFill(Color.rgb(100, 181, 246));
            case "sys" -> sysLabel.setTextFill(Color.rgb(100, 181, 246));
        }

        animateSection(section);
    }
    
    private void resetSectionLabels() {
        serverLabel.setTextFill(Color.rgb(130, 130, 130));
        sysLabel.setTextFill(Color.rgb(130, 130, 130));
    }
    
    private void animateSection(String section) {
        VBox targetSection = switch (section) {
            case "server" -> serverSection;
            case "sys" -> sysSection;
            default -> null;
        };

        if (targetSection != null) {
            AnimationUtils.stopAnimation(targetSection);
            targetSection.setScaleX(0.95);
            targetSection.setScaleY(0.95);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), targetSection);
            st.setToX(1);
            st.setToY(1);
            AnimationUtils.registerAnimation(targetSection, st);
            st.play();
        }
    }
    
    private void setupFooter() {
        VBox footer = new VBox(8);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(10, 0, 0, 0));
        
        Label version = new Label("v" + Main.VERSION);
        version.setFont(Font.font("Microsoft YaHei", 10));
        version.setTextFill(Color.rgb(140, 140, 140));
        
        footer.getChildren().add(version);
        getChildren().add(footer);
    }
    
    private Label createSectionLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 10));
        label.setTextFill(Color.rgb(130, 130, 130));
        label.setPadding(new Insets(12, 0, 4, 8));
        return label;
    }
    
    private Button createMenuBtn(String text, SVGPath icon, boolean selected) {
        Button btn = new Button(text);
        btn.setPrefWidth(152);
        btn.setPrefHeight(38);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 10, 0, 10));
        btn.setFont(Font.font("Microsoft YaHei", 12));
        btn.setCursor(Cursor.HAND);
        btn.setGraphic(icon);
        btn.setGraphicTextGap(8);
        
        if (selected) {
            btn.setStyle(
                "-fx-background-color: rgba(100, 181, 246, 0.45);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 8;"
            );
        } else {
            btn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: rgba(255,255,255,0.75);" +
                "-fx-background-radius: 8;"
            );
        }
        
        btn.setOnMouseEntered(e -> {
            if (btn != selectedBtn) {
                btn.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.1);" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 8;"
                );
                AnimationUtils.applyHoverScale(btn, 1.02);
            }
        });
        
        btn.setOnMouseExited(e -> {
            if (btn != selectedBtn) {
                btn.setStyle(
                    "-fx-background-color: transparent;" +
                    "-fx-text-fill: rgba(255,255,255,0.75);" +
                    "-fx-background-radius: 8;"
                );
                AnimationUtils.applyHoverScale(btn, 1.0);
            }
        });
        
        return btn;
    }
    
    private void selectBtn(Button btn, String page) {
        if (selectedBtn != null) {
            selectedBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: rgba(255,255,255,0.75);" +
                "-fx-background-radius: 8;"
            );
        }
        
        btn.setStyle(
            "-fx-background-color: rgba(100, 181, 246, 0.45);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;"
        );
        
        selectedBtn = btn;
        highlightSection(page);
        AnimationUtils.applyPulse(btn);
    }
    
    private SVGPath getIcon(String name) {
        SVGPath path = new SVGPath();
        path.setContent(switch (name) {
            case "home" -> "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
            case "list" -> "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z";
            case "add" -> "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
            case "console" -> "M4 6h16v12H4zm2 2v8h12V8zm2 2l2 2-2 2h2l3-3-3-3zm5 3h5v2h-5z";
            case "config" -> "M3 17h2v-2H3zm0-4h2v-2H3zm0-4h2V7H3zm4 8h14v-2H7zm0-4h14v-2H7zm0-4h14V7H7z";
            case "settings" -> "M12 15.5A3.5 3.5 0 0 1 8.5 12 3.5 3.5 0 0 1 12 8.5a3.5 3.5 0 0 1 3.5 3.5 3.5 3.5 0 0 1-3.5 3.5m7.43-2.53c.04-.32.07-.64.07-.97 0-.33-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65A.488.488 0 0 0 14 2h-4c-.25 0-.46.18-.5.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98 0 .33.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1.01c.52.4 1.08.73 1.69.98l.38 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1.01c.22.08.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65z";
            case "about" -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z";
            case "log" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
            case "appearance" -> "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";
            case "java" -> "M18.5 3H6c-1.1 0-2 .9-2 2v5.71c0 3.83 2.95 7.18 6.78 7.29 3.96.12 7.22-3.06 7.22-7v-1h.5c1.93 0 3.5-1.57 3.5-3.5S20.43 3 18.5 3zM16 8.99c0 2.2-1.8 3.99-3.99 3.99-2.2 0-4-1.8-4-4V5h8v3.99zm2.5-1.99h-1v-2h1c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5z";
            case "gamerules" -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z";
            case "update" -> "M12 16.5l4-4h-3v-6h-2v6H8l4 4zm-6 2h12v2H6v-2z";
            case "save" -> "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z";
            case "folder" -> "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z";
            case "trash" -> "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
            case "open" -> "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z";
            default -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z";
        });
        path.setFill(Color.WHITE);
        path.setScaleX(0.7);
        path.setScaleY(0.7);
        return path;
    }
    

    
    public void setContentPanel(ContentPanel panel) {
        this.contentPanel = panel;
    }

    private Button consoleMenuBtn, configMenuBtn, logMenuBtn, gameRulesMenuBtn, javaMenuBtn;
    private Button appearanceMenuBtn, updateMenuBtn;

    public void showSubPageNav(String title, Runnable onBack, String currentPage, Runnable onConsole, Runnable onConfig, Runnable onLogSettings, Runnable onGameRules, Runnable onJava) {
        if (isNavAnimating) return;

        if (isSubPageMode && subPageNav != null) {
            updateSubPageNavHighlight(currentPage);
            return;
        }

        isSubPageMode = true;
        isNavAnimating = true;

        subPageNav = new VBox(10);
        subPageNav.setPadding(new Insets(20, 14, 20, 14));
        subPageNav.setAlignment(Pos.TOP_LEFT);

        Button backBtn = createBackBtn();
        backBtn.setOnAction(e -> onBack.run());

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(152);

        subPageNav.getChildren().addAll(backBtn, titleLabel);

        consoleMenuBtn = null;
        configMenuBtn = null;
        logMenuBtn = null;
        gameRulesMenuBtn = null;
        javaMenuBtn = null;

        if (onConsole != null) {
            consoleMenuBtn = createSubMenuBtn("终端控制台", getIcon("console"), currentPage.equals("console"));
            consoleMenuBtn.setOnAction(e -> onConsole.run());
            subPageNav.getChildren().add(consoleMenuBtn);
        }

        if (onConfig != null) {
            configMenuBtn = createSubMenuBtn("配置管理", getIcon("config"), currentPage.equals("config"));
            configMenuBtn.setOnAction(e -> onConfig.run());
            subPageNav.getChildren().add(configMenuBtn);
        }

        if (onJava != null) {
            javaMenuBtn = createSubMenuBtn("Java 设置", getIcon("java"), currentPage.equals("java"));
            javaMenuBtn.setOnAction(e -> onJava.run());
            subPageNav.getChildren().add(javaMenuBtn);
        }

        if (onGameRules != null) {
            gameRulesMenuBtn = createSubMenuBtn("游戏规则", getIcon("gamerules"), currentPage.equals("gamerules"));
            gameRulesMenuBtn.setOnAction(e -> onGameRules.run());
            subPageNav.getChildren().add(gameRulesMenuBtn);
        }

        if (onLogSettings != null) {
            logMenuBtn = createSubMenuBtn("日志设置", getIcon("log"), currentPage.equals("logsettings"));
            logMenuBtn.setOnAction(e -> onLogSettings.run());
            subPageNav.getChildren().add(logMenuBtn);
        }

        getChildren().remove(menuContainer);
        getChildren().add(1, subPageNav);
        VBox.setVgrow(subPageNav, Priority.ALWAYS);

        AnimationUtils.applySlideInFromLeft(subPageNav, 250);
        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        pt.setOnFinished(e -> isNavAnimating = false);
        pt.play();
    }

    private void updateSubPageNavHighlight(String currentPage) {
        if (consoleMenuBtn != null) {
            updateSubMenuBtnStyle(consoleMenuBtn, currentPage.equals("console"));
        }
        if (configMenuBtn != null) {
            updateSubMenuBtnStyle(configMenuBtn, currentPage.equals("config"));
        }
        if (javaMenuBtn != null) {
            updateSubMenuBtnStyle(javaMenuBtn, currentPage.equals("java"));
        }
        if (gameRulesMenuBtn != null) {
            updateSubMenuBtnStyle(gameRulesMenuBtn, currentPage.equals("gamerules"));
        }
        if (logMenuBtn != null) {
            updateSubMenuBtnStyle(logMenuBtn, currentPage.equals("logsettings"));
        }
    }

    private Button createSubMenuBtn(String text, SVGPath icon, boolean isSelected) {
        Button btn = new Button(text);
        btn.setPrefWidth(152);
        btn.setPrefHeight(38);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 10, 0, 10));
        btn.setFont(Font.font("Microsoft YaHei", 12));
        btn.setCursor(Cursor.HAND);
        btn.setGraphic(icon);
        btn.setGraphicTextGap(8);

        btn.getProperties().put("selected", isSelected);

        updateSubMenuBtnStyle(btn, isSelected);

        btn.setOnMouseEntered(e -> {
            boolean selected = (Boolean) btn.getProperties().get("selected");
            if (!selected) {
                btn.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.1);" +
                    "-fx-background-radius: 8;"
                );
                btn.setTextFill(Color.WHITE);
            }
        });

        btn.setOnMouseExited(e -> {
            boolean selected = (Boolean) btn.getProperties().get("selected");
            if (!selected) {
                btn.setStyle(
                    "-fx-background-color: transparent;" +
                    "-fx-background-radius: 8;"
                );
                btn.setTextFill(Color.rgb(200, 200, 200));
            }
        });

        return btn;
    }

    private void updateSubMenuBtnStyle(Button btn, boolean selected) {
        btn.getProperties().put("selected", selected);
        if (selected) {
            btn.setStyle(
                "-fx-background-color: rgba(100, 181, 246, 0.45);" +
                "-fx-background-radius: 8;"
            );
            btn.setTextFill(Color.WHITE);
        } else {
            btn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-background-radius: 8;"
            );
            btn.setTextFill(Color.rgb(200, 200, 200));
        }
    }

    public void showSettingsSubPageNav(Runnable onBack, String currentPage, Runnable onAppearance, Runnable onUpdate) {
        if (isNavAnimating) return;

        if (isSubPageMode && subPageNav != null) {
            updateSettingsSubPageNavHighlight(currentPage);
            return;
        }

        isSubPageMode = true;
        isNavAnimating = true;

        subPageNav = new VBox(10);
        subPageNav.setPadding(new Insets(20, 14, 20, 14));
        subPageNav.setAlignment(Pos.TOP_LEFT);

        Button backBtn = createBackBtn();
        backBtn.setOnAction(e -> onBack.run());

        Label titleLabel = new Label("设置");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(152);

        subPageNav.getChildren().addAll(backBtn, titleLabel);

        appearanceMenuBtn = null;
        updateMenuBtn = null;

        if (onAppearance != null) {
            appearanceMenuBtn = createSubMenuBtn("外观设置", getIcon("appearance"), currentPage.equals("appearance"));
            appearanceMenuBtn.setOnAction(e -> onAppearance.run());
            subPageNav.getChildren().add(appearanceMenuBtn);
        }

        if (onUpdate != null) {
            updateMenuBtn = createSubMenuBtn("更新", getIcon("update"), currentPage.equals("update"));
            updateMenuBtn.setOnAction(e -> onUpdate.run());
            subPageNav.getChildren().add(updateMenuBtn);
        }

        getChildren().remove(menuContainer);
        getChildren().add(1, subPageNav);
        VBox.setVgrow(subPageNav, Priority.ALWAYS);

        AnimationUtils.applySlideInFromLeft(subPageNav, 250);
        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        pt.setOnFinished(e -> isNavAnimating = false);
        pt.play();
    }

    private void updateSettingsSubPageNavHighlight(String currentPage) {
        if (appearanceMenuBtn != null) {
            updateSubMenuBtnStyle(appearanceMenuBtn, currentPage.equals("appearance"));
        }
        if (updateMenuBtn != null) {
            updateSubMenuBtnStyle(updateMenuBtn, currentPage.equals("update"));
        }
    }

    public void hideSubPageNav() {
        if (!isSubPageMode || isNavAnimating) return;
        isSubPageMode = false;
        isNavAnimating = true;

        getChildren().remove(subPageNav);
        getChildren().add(1, menuContainer);
        VBox.setVgrow(menuContainer, Priority.ALWAYS);

        menuContainer.setOpacity(0);
        menuContainer.setTranslateX(-20);
        AnimationUtils.applySlideInFromLeft(menuContainer, 200);
        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
        pt.setOnFinished(e -> isNavAnimating = false);
        pt.play();
    }

    private Button createBackBtn() {
        Button btn = new Button("返回");
        btn.setPrefWidth(152);
        btn.setPrefHeight(38);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 10, 0, 10));
        btn.setFont(Font.font("Microsoft YaHei", 12));
        btn.setCursor(Cursor.HAND);
        btn.setTextFill(Color.WHITE);
        btn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-background-radius: 8;"
        );

        SVGPath icon = new SVGPath();
        icon.setContent("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z");
        icon.setFill(Color.WHITE);
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);
        btn.setGraphic(icon);
        btn.setGraphicTextGap(8);

        btn.setOnMouseEntered(e -> {
            btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.2);" +
                "-fx-background-radius: 8;"
            );
            AnimationUtils.applyHoverScale(btn, 1.02);
        });

        btn.setOnMouseExited(e -> {
            btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.1);" +
                "-fx-background-radius: 8;"
            );
            AnimationUtils.applyHoverScale(btn, 1.0);
        });

        return btn;
    }
}
