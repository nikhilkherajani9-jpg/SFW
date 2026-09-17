package com.sfw.wholesale;

/**
 * A wrapper Main class that does not extend javafx.application.Application.
 * This is required to launch JavaFX 11+ applications from a standard classpath 
 * (e.g., when bundled with jpackage using standard JARs instead of modules).
 */
public class Main {
    public static void main(String[] args) {
        App.main(args);
    }
}
