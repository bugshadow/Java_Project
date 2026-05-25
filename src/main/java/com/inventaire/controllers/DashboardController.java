package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Produit;
import com.inventaire.models.Transaction;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.AlerteService;
import com.inventaire.services.InventaireService;
import com.inventaire.session.SessionManager;
import com.inventaire.utils.DateUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {

    @FXML private Label labelUtilisateur;
    @FXML private Label labelHeure;
    @FXML private Label lblTotalProduits;
    @FXML private Label lblValeurStock;
    @FXML private Label lblTransactionsAuj;
    @FXML private Label lblAlertes;
    @FXML private Button btnMenuAlertes;

    // Table Transactions
    @FXML private TableView<Transaction> tableTransactions;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colProduit;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, String> colQte;
    @FXML private TableColumn<Transaction, String> colStatut;

    // Table Alertes
    @FXML private TableView<Produit> tableAlertes;
    @FXML private TableColumn<Produit, String> colAlerteRef;
    @FXML private TableColumn<Produit, String> colAlerteNom;
    @FXML private TableColumn<Produit, String> colAlerteStock;
    @FXML private TableColumn<Produit, String> colAlerteSeuil;

    private InventaireService inventaireService;
    private AlerteService alerteService;

    @FXML
    public void initialize() {
        DatabaseConnection db = DatabaseConnection.getInstance();
        inventaireService = new InventaireService(db);
        alerteService = new AlerteService(db);

        // Affichage infos utilisateur
        Utilisateur user = SessionManager.getInstance().getUtilisateur();
        if (user != null) {
            labelUtilisateur.setText(user.getNomComplet() + "\n" + user.getRole());
        }

        demarrerHorloge();
        configurerTables();
        chargerDonnees();

        // Callback pour les alertes
        alerteService.setCallbackChangementAlertes(nbAlertes -> {
            Platform.runLater(() -> mettreAJourBadgesAlertes(nbAlertes));
        });
    }

    private void demarrerHorloge() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                Platform.runLater(() -> {
                    labelHeure.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void configurerTables() {
        // Table Transactions
        colDate.setCellValueFactory(cellData -> new SimpleStringProperty(DateUtil.formaterDateHeure(cellData.getValue().getEnregistreLe())));
        colProduit.setCellValueFactory(new PropertyValueFactory<>("produitNom"));
        colType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getIconeType() + " " + cellData.getValue().getType()));
        colQte.setCellValueFactory(new PropertyValueFactory<>("quantite"));
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));

        // Table Alertes
        colAlerteRef.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colAlerteNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colAlerteStock.setCellValueFactory(new PropertyValueFactory<>("stockTotal"));
        colAlerteSeuil.setCellValueFactory(new PropertyValueFactory<>("seuilCritique"));
    }

    private void chargerDonnees() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                int totalProduits = inventaireService.getNombreProduitsActifs();
                int transactionsAuj = inventaireService.getTransactionsAujourdhui();
                // Simulation du calcul de la valeur du stock pour l'affichage (à optimiser via un DAO spécifique si besoin)
                double valeurStock = new com.inventaire.dao.StockDAO(DatabaseConnection.getInstance()).getValeurTotaleStock();
                
                List<Transaction> dernieresTransactions = inventaireService.getDernieresTransactions(5);
                List<Produit> alertesCritiques = alerteService.getAlertesCritiques();

                Platform.runLater(() -> {
                    lblTotalProduits.setText(String.valueOf(totalProduits));
                    lblTransactionsAuj.setText(String.valueOf(transactionsAuj));
                    lblValeurStock.setText(String.format("%.2f €", valeurStock));
                    mettreAJourBadgesAlertes(alertesCritiques.size());

                    tableTransactions.setItems(FXCollections.observableArrayList(dernieresTransactions));
                    tableAlertes.setItems(FXCollections.observableArrayList(alertesCritiques));
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    private void mettreAJourBadgesAlertes(int nbAlertes) {
        lblAlertes.setText(String.valueOf(nbAlertes));
        if (nbAlertes > 0) {
            btnMenuAlertes.setText("⚠️ Alertes (" + nbAlertes + ")");
            btnMenuAlertes.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
        } else {
            btnMenuAlertes.setText("⚠️ Alertes");
            btnMenuAlertes.setStyle("-fx-text-fill: #c5cae9;");
        }
    }

    @FXML private void allerVersProduits() { MainApp.changerEcran("produits.fxml"); }
    @FXML private void allerVersTransactions() { MainApp.changerEcran("transaction.fxml"); }
    @FXML private void allerVersHistorique() { MainApp.changerEcran("historique.fxml"); }
    @FXML private void allerVersAlertes() { MainApp.changerEcran("alertes.fxml"); }
    @FXML private void allerVersRapports() { MainApp.changerEcran("rapports.fxml"); }
    
    @FXML
    private void deconnecter() {
        SessionManager.getInstance().deconnecter();
        MainApp.changerEcran("login.fxml");
    }
}
