package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Transaction;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.InventaireService;
import com.inventaire.services.RapportService;
import com.inventaire.session.SessionManager;
import com.inventaire.utils.DateUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class RapportsController {

    @FXML private Label labelUtilisateur;
    @FXML private RadioButton rbStockPdf;
    @FXML private RadioButton rbStockExcel;
    @FXML private DatePicker dateDebut;
    @FXML private DatePicker dateFin;
    @FXML private RadioButton rbMouvPdf;
    @FXML private RadioButton rbMouvExcel;
    @FXML private Label labelMessage;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button btnGenererStock;
    @FXML private Button btnGenererMouvements;

    private RapportService rapportService;
    private InventaireService inventaireService;

    @FXML
    public void initialize() {
        DatabaseConnection db = DatabaseConnection.getInstance();
        inventaireService = new InventaireService(db);
        rapportService = new RapportService(db, inventaireService);

        Utilisateur user = SessionManager.getInstance().getUtilisateur();
        if (user != null) {
            labelUtilisateur.setText(user.getNomComplet() + "\n" + user.getRole());
        }

        dateDebut.setValue(LocalDate.now().minusDays(30));
        dateFin.setValue(LocalDate.now());

        // S'assurer que le dossier exports existe
        new File("exports").mkdirs();
    }

    @FXML
    private void genererRapportStock() {
        boolean isPdf = rbStockPdf.isSelected();
        String extension = isPdf ? ".pdf" : ".xlsx";
        String chemin = "exports/Etat_Stocks_" + DateUtil.formaterPourFichier(LocalDateTime.now()) + extension;

        setEnChargement(true);
        labelMessage.setText("Génération en cours...");
        labelMessage.setStyle("-fx-text-fill: #1a237e;");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (isPdf) {
                    rapportService.genererPdfEtatStocks(chemin, null);
                } else {
                    rapportService.genererExcelEtatStocks(chemin);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setEnChargement(false);
            labelMessage.setText("✅ Rapport généré avec succès : " + new File(chemin).getAbsolutePath());
            labelMessage.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        });

        task.setOnFailed(e -> {
            setEnChargement(false);
            labelMessage.setText("❌ Erreur lors de la génération : " + task.getException().getMessage());
            labelMessage.setStyle("-fx-text-fill: #d32f2f;");
            task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    @FXML
    private void genererRapportMouvements() {
        LocalDate debut = dateDebut.getValue();
        LocalDate fin = dateFin.getValue();
        
        if (debut == null || fin == null) {
            labelMessage.setText("Veuillez sélectionner les dates de début et de fin.");
            labelMessage.setStyle("-fx-text-fill: #d32f2f;");
            return;
        }

        boolean isPdf = rbMouvPdf.isSelected();
        String extension = isPdf ? ".pdf" : ".xlsx";
        String chemin = "exports/Mouvements_" + DateUtil.formaterPourFichier(LocalDateTime.now()) + extension;

        setEnChargement(true);
        labelMessage.setText("Récupération des données et génération en cours...");
        labelMessage.setStyle("-fx-text-fill: #1a237e;");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<Transaction> transactions = inventaireService.rechercherTransactions(debut, fin, null, null, null, null);
                if (isPdf) {
                    rapportService.genererPdfMouvements(chemin, transactions, debut, fin);
                } else {
                    rapportService.genererExcelMouvements(chemin, transactions);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setEnChargement(false);
            labelMessage.setText("✅ Rapport généré avec succès : " + new File(chemin).getAbsolutePath());
            labelMessage.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        });

        task.setOnFailed(e -> {
            setEnChargement(false);
            labelMessage.setText("❌ Erreur : " + task.getException().getMessage());
            labelMessage.setStyle("-fx-text-fill: #d32f2f;");
        });

        new Thread(task).start();
    }

    private void setEnChargement(boolean enChargement) {
        btnGenererStock.setDisable(enChargement);
        btnGenererMouvements.setDisable(enChargement);
        progressIndicator.setVisible(enChargement);
    }

    // Navigation
    @FXML private void allerVersDashboard() { MainApp.changerEcran("dashboard.fxml"); }
    @FXML private void allerVersProduits() { MainApp.changerEcran("produits.fxml"); }
    @FXML private void allerVersTransactions() { MainApp.changerEcran("transaction.fxml"); }
    @FXML private void allerVersHistorique() { MainApp.changerEcran("historique.fxml"); }
    @FXML private void allerVersAlertes() { MainApp.changerEcran("alertes.fxml"); }
    @FXML private void deconnecter() {
        SessionManager.getInstance().deconnecter();
        MainApp.changerEcran("login.fxml");
    }
}
