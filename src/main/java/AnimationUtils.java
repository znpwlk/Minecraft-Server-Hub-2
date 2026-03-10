import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import java.util.Map;
import java.util.WeakHashMap;

public class AnimationUtils {
    
    private static final Duration ANIMATION_DURATION = Duration.millis(300);
    private static final double FORWARD_OFFSET_RATIO = 0.5;
    
    private static final Map<Node, Animation> activeAnimations = new WeakHashMap<>();
    private static final Map<Node, javafx.animation.AnimationTimer> activeTimers = new WeakHashMap<>();
    
    public interface TransitionEffect {
        Animation create(StackPane container, Node outgoing, Node incoming);
    }
    
    public static void stopAnimation(Node node) {
        Animation anim = activeAnimations.get(node);
        if (anim != null) {
            anim.stop();
            activeAnimations.remove(node);
        }
        javafx.animation.AnimationTimer timer = activeTimers.get(node);
        if (timer != null) {
            timer.stop();
            activeTimers.remove(node);
        }
    }
    
    public static void registerAnimation(Node node, Animation animation) {
        stopAnimation(node);
        activeAnimations.put(node, animation);
        animation.setOnFinished(e -> activeAnimations.remove(node));
    }
    
    public static void resetNodeState(Node node) {
        if (node == null) return;
        node.setTranslateX(0);
        node.setTranslateY(0);
        node.setOpacity(1.0);
        node.setScaleX(1.0);
        node.setScaleY(1.0);
    }
    
    public static final TransitionEffect SWIPE_LEFT = (container, outgoing, incoming) -> {
        double width = 680;
        
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setTranslateX(width);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), width, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.translateXProperty(), -width, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), 0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect SWIPE_RIGHT = (container, outgoing, incoming) -> {
        double width = 680;
        
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setTranslateX(-width);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), -width, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.translateXProperty(), width, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), 0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect FORWARD = (container, outgoing, incoming) -> {
        double width = 680;
        double offset = width * FORWARD_OFFSET_RATIO;
        
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setTranslateX(offset);
        incoming.setOpacity(0.0);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), offset, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.translateXProperty(), -offset, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect BACKWARD = (container, outgoing, incoming) -> {
        double width = 680;
        double offset = width * FORWARD_OFFSET_RATIO;
        
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setTranslateX(-offset);
        incoming.setOpacity(0.0);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), -offset, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.translateXProperty(), offset, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect FADE = (container, outgoing, incoming) -> {
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setOpacity(0.0);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static void applyEntryAnimation(Node node, double delayMillis) {
        stopAnimation(node);
        resetNodeState(node);
        node.setOpacity(0);
        node.setTranslateY(15);
        
        FadeTransition fade = new FadeTransition(Duration.millis(300), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMillis));
        
        TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
        slide.setFromY(15);
        slide.setToY(0);
        slide.setDelay(Duration.millis(delayMillis));
        
        ParallelTransition parallel = new ParallelTransition(fade, slide);
        registerAnimation(node, parallel);
        parallel.play();
    }
    
    public static void applyStaggeredEntry(java.util.List<? extends Node> nodes, double baseDelayMillis) {
        for (int i = 0; i < nodes.size(); i++) {
            applyEntryAnimation(nodes.get(i), i * baseDelayMillis);
        }
    }
    
    public static void applyHoverScale(Node node, double targetScale) {
        stopAnimation(node);
        
        ScaleTransition scale = new ScaleTransition(Duration.millis(200), node);
        scale.setToX(targetScale);
        scale.setToY(targetScale);
        registerAnimation(node, scale);
        scale.play();
    }
    
    public static void applyPulse(Node node) {
        stopAnimation(node);
        resetNodeState(node);
        
        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), node);
        pulse.setFromX(1);
        pulse.setFromY(1);
        pulse.setToX(1.05);
        pulse.setToY(1.05);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        registerAnimation(node, pulse);
        pulse.play();
    }
    
    public static void applySlideInFromLeft(Node node, double millis) {
        stopAnimation(node);
        resetNodeState(node);
        node.setTranslateX(-30);
        node.setOpacity(0);
        
        TranslateTransition tt = new TranslateTransition(Duration.millis(millis), node);
        tt.setFromX(-30);
        tt.setToX(0);
        FadeTransition ft = new FadeTransition(Duration.millis(millis), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ParallelTransition pt = new ParallelTransition(tt, ft);
        registerAnimation(node, pt);
        pt.play();
    }
    
    public static void applyBreathingFloat(Node node) {
        stopAnimation(node);
        
        TranslateTransition floatUp = new TranslateTransition(Duration.millis(3500), node);
        floatUp.setFromY(0);
        floatUp.setToY(-3);
        floatUp.setInterpolator(Interpolator.EASE_BOTH);
        
        TranslateTransition floatDown = new TranslateTransition(Duration.millis(3500), node);
        floatDown.setFromY(-3);
        floatDown.setToY(0);
        floatDown.setInterpolator(Interpolator.EASE_BOTH);
        
        SequentialTransition breathing = new SequentialTransition(floatUp, floatDown);
        breathing.setCycleCount(Animation.INDEFINITE);
        registerAnimation(node, breathing);
        breathing.play();
    }
    
    public static void applyGlowPulse(Node node, javafx.scene.effect.DropShadow glowEffect) {
        stopAnimation(node);
        
        java.util.Random random = new java.util.Random();
        
        double minRadius = 15, maxRadius = 28;
        double minSpread = 0.25, maxSpread = 0.45;
        double minAlpha = 0.5, maxAlpha = 0.85;
        int minBlue = 160, maxBlue = 255;
        
        javafx.animation.AnimationTimer breathingTimer = new javafx.animation.AnimationTimer() {
            private long cycleStart = 0;
            private long cycleDuration = 0;
            private long pauseDuration = 0;
            private boolean inPause = false;
            
            private void startNewCycle() {
                cycleDuration = (long)(3500 + random.nextDouble() * 1500);
                pauseDuration = 0;
                cycleStart = System.nanoTime();
                inPause = false;
            }
            
            private double easeInOut(double t) {
                return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            }
            
            @Override
            public void handle(long now) {
                if (cycleStart == 0) {
                    startNewCycle();
                    return;
                }
                
                long elapsed = now - cycleStart;
                
                if (inPause) {
                    if (elapsed >= pauseDuration * 1000000L) {
                        startNewCycle();
                    }
                    return;
                }
                
                double rawProgress = (double)elapsed / (cycleDuration * 1000000L);
                
                if (rawProgress >= 1.0) {
                    inPause = true;
                    cycleStart = now;
                    glowEffect.setRadius(minRadius);
                    glowEffect.setSpread(minSpread);
                    glowEffect.setColor(Color.rgb(80, 160, minBlue, minAlpha));
                    return;
                }
                
                double progress;
                if (rawProgress < 0.5) {
                    progress = easeInOut(rawProgress * 2);
                } else {
                    progress = easeInOut(2 - rawProgress * 2);
                }
                
                double currentRadius = minRadius + (maxRadius - minRadius) * progress;
                double currentSpread = minSpread + (maxSpread - minSpread) * progress;
                double currentAlpha = minAlpha + (maxAlpha - minAlpha) * progress;
                int currentBlue = (int)(minBlue + (maxBlue - minBlue) * progress);
                int currentGreen = (int)(160 + (40 * progress));
                
                glowEffect.setRadius(currentRadius);
                glowEffect.setSpread(currentSpread);
                glowEffect.setColor(Color.rgb(80, currentGreen, currentBlue, currentAlpha));
            }
        };
        
        breathingTimer.start();
        activeTimers.put(node, breathingTimer);
    }
    
    public static void applyStaggeredFadeUp(java.util.List<? extends Node> nodes, double baseDelayMillis) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            stopAnimation(node);
            resetNodeState(node);
            node.setOpacity(0);
            node.setTranslateY(20);
            
            FadeTransition fade = new FadeTransition(Duration.millis(500), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setDelay(Duration.millis(baseDelayMillis * i + 200));
            
            TranslateTransition slide = new TranslateTransition(Duration.millis(500), node);
            slide.setFromY(20);
            slide.setToY(0);
            slide.setDelay(Duration.millis(baseDelayMillis * i + 200));
            
            ParallelTransition parallel = new ParallelTransition(fade, slide);
            registerAnimation(node, parallel);
            parallel.play();
        }
    }
    
    public static final TransitionEffect SCALE = (container, outgoing, incoming) -> {
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setScaleX(0.9);
        incoming.setScaleY(0.9);
        incoming.setOpacity(0.0);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleXProperty(), 0.9, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleYProperty(), 0.9, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.scaleXProperty(), 1.05, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.scaleYProperty(), 1.05, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect FLIP = (container, outgoing, incoming) -> {
        resetNodeState(outgoing);
        resetNodeState(incoming);
        
        incoming.setRotationAxis(javafx.scene.transform.Rotate.Y_AXIS);
        incoming.setRotate(90);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.rotateProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.rotateProperty(), 90, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(150),
                new KeyValue(outgoing.rotateProperty(), -90, Interpolator.EASE_BOTH),
                new KeyValue(incoming.rotateProperty(), 90, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.rotateProperty(), -90, Interpolator.EASE_BOTH),
                new KeyValue(incoming.rotateProperty(), 0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect REVEAL = (container, outgoing, incoming) -> {
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setClip(new javafx.scene.shape.Rectangle(0, 528));
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(((javafx.scene.shape.Rectangle)incoming.getClip()).widthProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(((javafx.scene.shape.Rectangle)incoming.getClip()).widthProperty(), 680.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            incoming.setClip(null);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect BLUR = (container, outgoing, incoming) -> {
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setOpacity(0.0);
        
        javafx.scene.effect.GaussianBlur outBlur = new javafx.scene.effect.GaussianBlur(0);
        javafx.scene.effect.GaussianBlur inBlur = new javafx.scene.effect.GaussianBlur(10);
        outgoing.setEffect(outBlur);
        incoming.setEffect(inBlur);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outBlur.radiusProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(inBlur.radiusProperty(), 10, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outBlur.radiusProperty(), 10, Interpolator.EASE_BOTH),
                new KeyValue(inBlur.radiusProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            outgoing.setEffect(null);
            incoming.setEffect(null);
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect BOUNCE = (container, outgoing, incoming) -> {
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setScaleX(0.8);
        incoming.setScaleY(0.8);
        incoming.setOpacity(0.0);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleXProperty(), 0.8, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleYProperty(), 0.8, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(200),
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleXProperty(), 1.1, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleYProperty(), 1.1, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(incoming.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect FADE_ZOOM = (container, outgoing, incoming) -> {
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setScaleX(1.1);
        incoming.setScaleY(1.1);
        incoming.setOpacity(0.0);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleXProperty(), 1.1, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleYProperty(), 1.1, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
    
    public static final TransitionEffect SLIDE_UP = (container, outgoing, incoming) -> {
        double height = 528;
        
        resetNodeState(outgoing);
        resetNodeState(incoming);
        incoming.setTranslateY(height);
        
        Timeline timeline = new Timeline();
        timeline.setCycleCount(1);
        
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(outgoing.translateYProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateYProperty(), height, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(ANIMATION_DURATION,
                new KeyValue(outgoing.translateYProperty(), -height * 0.3, Interpolator.EASE_BOTH),
                new KeyValue(incoming.translateYProperty(), 0, Interpolator.EASE_BOTH)
            )
        );
        
        timeline.setOnFinished(e -> {
            resetNodeState(outgoing);
            if (container.getChildren().contains(outgoing)) {
                container.getChildren().remove(outgoing);
            }
        });
        
        return timeline;
    };
}
