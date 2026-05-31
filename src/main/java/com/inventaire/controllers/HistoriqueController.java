package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.blockchain.EthereumClient;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Transaction;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.AlerteService;
import com.inventaire.services.BlockchainService;
import com.inventaire.services.InventaireService;
import com.inventaire.session.SessionManager;
import com.inventaire.utils.DateUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class HistoriqueController {

    @FXML private Label labelUtilisateur;
    @FXML private DatePicker dateDebut;
    @FXML private DatePicker dateFin;
    @FXML private ComboBox<String> comboType;
    @FXML private TextField champRecherche;
    
    @FXML private TableView<Transaction> tableHistorique;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colTxId;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, String> colProduit;
    @FXML private TableColumn<Transaction, Integer> colQte;
    @FXML private TableColumn<Transaction, String> colOperateur;
    @FXML private TableColumn<Transaction, String> colStatut;

    @FXML private VBox boxVerification;
    @FXML private Label lblResultatVerification;

    private InventaireService inventaireService;
    private BlockchainService blockchainService;

    @FXML
    public void initialize() {
        DatabaseConnection db = DatabaseConnection.getInstance();
        inventaireService = new InventaireService(db);
        blockchainService = new BlockchainService(db, EthereumClient.getInstance(), new AlerteService(db));

        Utilisateur user = SessionManager.getInstance().getUtilisateur();
        if (user != null) {
            labelUtilisateur.setText(user.getNomComplet() + "\n" + user.getRole());
        }

        comboType.setItems(FXCollections.observableArrayList("Tous", "ENTREE", "SORTIE", "TRANSFERT"));
        comboType.setValue("Tous");

        dateDebut.setValue(LocalDate.now().minusDays(30));
        dateFin.setValue(LocalDate.now());

        configurerTable();
        filtrerHistorique();
    }

    private void configurerTable() {
        colDate.setCellValueFactory(cellData -> new SimpleStringProperty(DateUtil.formaterDateHeure(cellData.getValue().getEnregistreLe())));
        colTxId.setCellValueFactory(cellData -> {
            String txId = cellData.getValue().getBlockchainTxId();
            return new SimpleStringProperty(txId != null && txId.length() > 20 ? txId.substring(0, 20) + "..." : txId);
        });
        colType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getIconeType() + " " + cellData.getValue().getType()));
        colProduit.setCellValueFactory(new PropertyValueFactory<>("produitNom"));
        colQte.setCellValueFactory(new PropertyValueFactory<>("quantite"));
        colOperateur.setCellValueFactory(new PropertyValueFactory<>("operateurNom"));
        
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colStatut.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label lbl = new Label(item);
                    if ("CONFIRMEE".equals(item)) {
                        lbl.getStyleClass().add("badge-ok");
                    } else if ("ECHOUEE".equals(item)) {
                        lbl.getStyleClass().add("badge-critique");
                    } else {
                        lbl.getStyleClass().add("badge-faible"); // En attente
                    }
                    setGraphic(lbl);
                }
            }
        });
    }

    @FXML
    private void filtrerHistorique() {
        LocalDate debut = dateDebut.getValue();
        LocalDate fin = dateFin.getValue();
        String type = comboType.getValue();
        if ("Tous".equals(type)) type = null;
        String typeFiltre = type;
        
        // Pour une implémentation complète, il faudrait un service qui gère la recherche textuelle
        // Ici on simplifie en appelant la méthode existante de l'inventaireService

        Task<List<Transaction>> task = new Task<>() {
            @Override
            protected List<Transaction> call() {
                return inventaireService.rechercherTransactions(debut, fin, null, typeFiltre, null, null);
            }
        };

        task.setOnSucceeded(e -> tableHistorique.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    @FXML
    private void reinitialiserFiltres() {
        dateDebut.setValue(LocalDate.now().minusDays(30));
        dateFin.setValue(LocalDate.now());
        comboType.setValue("Tous");
        champRecherche.clear();
        filtrerHistorique();
    }

    @FXML
    private void verifierIntegriteBloc() {
        Transaction tx = tableHistorique.getSelectionModel().getSelectedItem();
        if (tx == null || tx.getBlockchainTxId() == null) {
            afficherErreurVerification("Veuillez selectionner une transaction confirmee sur la blockchain.");
            return;
        }

        boxVerification.setVisible(true);
        boxVerification.setManaged(true);
        lblResultatVerification.setText("Verification sur le reseau blockchain en cours...\nID: " + tx.getBlockchainTxId());
        lblResultatVerification.setStyle("-fx-text-fill: #1a237e;");

        Task<Map<String, Object>> task = new Task<>() {
            @Override
            protected Map<String, Object> call() {
                return blockchainService.verifierIntegriteBloc(tx.getBlockchainTxId());
            }
        };

        task.setOnSucceeded(e -> {
            Map<String, Object> result = task.getValue();
            boolean integre = (Boolean) result.getOrDefault("integre", false);
            
            StringBuilder sb = new StringBuilder();
            sb.append("Verification terminee.\n");
            sb.append("Message : ").append(result.get("message")).append("\n");
            
            if (result.containsKey("hashCalcule") && result.get("hashCalcule") != null) {
                sb.append("Hash (SHA-256) : ").append(result.get("hashCalcule")).append("\n");
            }

            if (integre) {
                lblResultatVerification.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                sb.insert(0, "INTEGRITE GARANTIE\nLa transaction n'a pas ete alteree depuis son enregistrement sur le ledger.\n\n");
            } else {
                lblResultatVerification.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                sb.insert(0, "ANOMALIE DETECTEE\nLes donnees ont potentiellement ete alterees.\n\n");
            }
            lblResultatVerification.setText(sb.toString());
        });

        task.setOnFailed(e -> afficherErreurVerification("Erreur lors de la verification : " + task.getException().getMessage()));

        new Thread(task).start();
    }

    private void afficherErreurVerification(String message) {
        boxVerification.setVisible(true);
        boxVerification.setManaged(true);
        lblResultatVerification.setText(message);
        lblResultatVerification.setStyle("-fx-text-fill: #d32f2f;");
    }

    // Navigation
    @FXML private void allerVersDashboard() { MainApp.changerEcran("dashboard.fxml"); }
    @FXML private void allerVersProduits() { MainApp.changerEcran("produits.fxml"); }
    @FXML private void allerVersTransactions() { MainApp.changerEcran("transaction.fxml"); }
    @FXML private void allerVersAlertes() { MainApp.changerEcran("alertes.fxml"); }
    @FXML private void allerVersRapports() { MainApp.changerEcran("rapports.fxml"); }
    @FXML private void deconnecter() {
        SessionManager.getInstance().deconnecter();
        MainApp.changerEcran("login.fxml");
    }
}
