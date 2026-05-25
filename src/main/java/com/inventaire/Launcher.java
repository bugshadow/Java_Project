package com.inventaire;

/**
 * Classe de lancement pour contourner l'erreur :
 * "JavaFX runtime components are missing, and are required to run this application"
 * dans Java 11+.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
