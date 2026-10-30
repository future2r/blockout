/// Blockout, a remake of the 1989 DOS game.
module name.ulbricht.blockout {
    requires javafx.controls;
    requires java.desktop;

    // Only the JavaFX launcher needs access, to create the application class
    exports name.ulbricht.blockout to javafx.graphics;
}
