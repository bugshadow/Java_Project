package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.AuthService;
import com.inventaire.session.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

import java.util.Optional;

public class LoginController {

    @FXML private StackPane rootPane;
    @FXML private TextField champEmail;
    @FXML private PasswordField champMotDePasse;
    @FXML private TextField champMotDePasseVisible;
    @FXML private ToggleButton btnAfficherMdp;
    @FXML private Label labelErreur;
    @FXML private Button btnConnexion;
    @FXML private ProgressIndicator progressIndicator;

    private AuthService authService;

    @FXML
    public void initialize() {
        // Initialisation du service avec la connexion DB
        authService = new AuthService(DatabaseConnection.getInstance());

        // Focus initial
        Platform.runLater(() -> champEmail.requestFocus());

        // Gestion de l'affichage du mot de passe
        champMotDePasseVisible.managedProperty().bind(btnAfficherMdp.selectedProperty());
        champMotDePasseVisible.visibleProperty().bind(btnAfficherMdp.selectedProperty());
        champMotDePasse.managedProperty().bind(btnAfficherMdp.selectedProperty().not());
        champMotDePasse.visibleProperty().bind(btnAfficherMdp.selectedProperty().not());

        // Synchronisation des champs mot de passe
        champMotDePasseVisible.textProperty().bindBidirectional(champMotDePasse.textProperty());

        // Soumission avec la touche Entrée
        champMotDePasse.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleConnexion();
            }
        });
        champEmail.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                champMotDePasse.requestFocus();
            }
        });
    }

    @FXML
    private void handleConnexion() {
        String email = champEmail.getText();
        String mdp = champMotDePasse.getText();

        if (email == null || email.trim().isEmpty() || mdp == null || mdp.isEmpty()) {
            afficherErreur("Veuillez saisir votre email et votre mot de passe.");
            return;
        }

        labelErreur.setText("");
        setEnChargement(true);

        // Appel asynchrone pour ne pas bloquer l'interface
        Task<Optional<Utilisateur>> task = new Task<>() {
            @Override
            protected Optional<Utilisateur> call() {
                return authService.authentifier(email, mdp);
            }
        };

        task.setOnSucceeded(e -> {
            setEnChargement(false);
            Optional<Utilisateur> utilisateur = task.getValue();

            if (utilisateur.isPresent()) {
                // Ouverture de session avec callback de déconnexion
                SessionManager.getInstance().ouvrirSession(utilisateur.get(), () -> {
                    MainApp.changerEcran("login.fxml");
                });
                
                // Navigation vers le dashboard
                MainApp.changerEcran("dashboard.fxml");
            } else {
                afficherErreur(authService.getDerniereErreur());
                champMotDePasse.clear();
                champMotDePasse.requestFocus();
            }
        });

        task.setOnFailed(e -> {
            setEnChargement(false);
            afficherErreur("Erreur de connexion a la base de donnees.");
            task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    private void afficherErreur(String message) {
        labelErreur.setText(message);
    }

    private void setEnChargement(boolean enChargement) {
        btnConnexion.setDisable(enChargement);
        champEmail.setDisable(enChargement);
        champMotDePasse.setDisable(enChargement);
        champMotDePasseVisible.setDisable(enChargement);
        btnAfficherMdp.setDisable(enChargement);
        progressIndicator.setVisible(enChargement);
    }
}
