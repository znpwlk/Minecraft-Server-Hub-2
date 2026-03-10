import javafx.animation.FillTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class ToggleSwitch extends StackPane {
    private BooleanProperty selected = new SimpleBooleanProperty(false);
    private Rectangle background;
    private Circle thumb;
    private TranslateTransition slide;
    private FillTransition fillTransition;
    
    public ToggleSwitch() {
        setPrefSize(50, 26);
        setMaxSize(50, 26);
        setMinSize(50, 26);
        setCursor(Cursor.HAND);
        
        background = new Rectangle(50, 26);
        background.setArcWidth(26);
        background.setArcHeight(26);
        background.setFill(Color.rgb(100, 100, 100));
        
        thumb = new Circle(11);
        thumb.setFill(Color.WHITE);
        thumb.setTranslateX(-12);
        
        getChildren().addAll(background, thumb);
        
        slide = new TranslateTransition(Duration.millis(200), thumb);
        fillTransition = new FillTransition(Duration.millis(200), background);
        
        setOnMouseClicked(e -> {
            selected.set(!selected.get());
            updateState();
        });
        
        selected.addListener((obs, old, val) -> updateState());
    }
    
    private void updateState() {
        if (selected.get()) {
            slide.setToX(12);
            fillTransition.setToValue(Color.rgb(100, 181, 246));
        } else {
            slide.setToX(-12);
            fillTransition.setToValue(Color.rgb(100, 100, 100));
        }
        slide.play();
        fillTransition.play();
    }
    
    public boolean isSelected() {
        return selected.get();
    }
    
    public void setSelected(boolean value) {
        selected.set(value);
    }
    
    public BooleanProperty selectedProperty() {
        return selected;
    }
}
