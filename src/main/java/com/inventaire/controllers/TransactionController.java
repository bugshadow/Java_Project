package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.blockchain.EthereumClient;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Entrepot;
import com.inventaire.models.Produit;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.AlerteService;
import com.inventaire.services.BlockchainService;
import com.inventaire.services.InventaireService;
import com.inventaire.session.SessionManager;
import com.inventaire.utils.ValidationUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.List;

public class TransactionController {

    @FXML private Label labelUtilisateur;
    @FXML private ComboBox<String> comboType;
    @FXML private ComboBox<Produit> comboProduit;
    @FXML private TextField champQuantite;
    
    @FXML private VBox boxEntrepotSource;
    @FXML private ComboBox<Entrepot> comboEntrepotSource;
    @FXML private VBox boxEntrepotDest;
    @FXML private ComboBox<Entrepot> comboEntrepotDest;
    
    @FXML private Label labelTiers;
    @FXML private TextField champTiers;
    @FXML private TextField champDocument;
    
    @FXML private Label labelErreur;
    @FXML private Label labelStockDispo;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button btnValider;

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

        comboType.setItems(FXCollections.observableArrayList("ENTREE", "SORTIE", "TRANSFERT"));
        comboType.valueProperty().addListener((obs, oldVal, newVal) -> ajusterFormulaire(newVal));

        chargerDonneesCombo();

        comboProduit.valueProperty().addListener((obs, oldVal, newVal) -> calculerStockDispo());
        comboEntrepotSource.valueProperty().addListener((obs, oldVal, newVal) -> calculerStockDispo());
        
        ajusterFormulaire(null);
    }

    private void chargerDonneesCombo() {
        Task<Void> task = new Task<>() {
            List<Produit> produits;
            List<Entrepot> entrepots;
            @Override
            protected Void call() {
                produits = inventaireService.getProduits();
                entrepots = inventaireService.getEntrepots();
                return null;
            }
            @Override
            protected void succeeded() {
                comboProduit.setItems(FXCollections.observableArrayList(produits));
                comboEntrepotSource.setItems(FXCollections.observableArrayList(entrepots));
                comboEntrepotDest.setItems(FXCollections.observableArrayList(entrepots));
            }
        };
        new Thread(task).start();
    }

    private void ajusterFormulaire(String type) {
        if (type == null) {
            boxEntrepotSource.setVisible(false);
            boxEntrepotSource.setManaged(false);
            boxEntrepotDest.setVisible(false);
            boxEntrepotDest.setManaged(false);
            return;
        }

        switch (type) {
            case "ENTREE":
                boxEntrepotSource.setVisible(false);
                boxEntrepotSource.setManaged(false);
                boxEntrepotDest.setVisible(true);
                boxEntrepotDest.setManaged(true);
                labelTiers.setText("Fournisseur");
                break;
            case "SORTIE":
                boxEntrepotSource.setVisible(true);
                boxEntrepotSource.setManaged(true);
                boxEntrepotDest.setVisible(false);
                boxEntrepotDest.setManaged(false);
                labelTiers.setText("Client");
                break;
            case "TRANSFERT":
                boxEntrepotSource.setVisible(true);
                boxEntrepotSource.setManaged(true);
                boxEntrepotDest.setVisible(true);
                boxEntrepotDest.setManaged(true);
                labelTiers.setText("Motif du transfert");
                break;
        }
    }

    private void calculerStockDispo() {
        Produit p = comboProduit.getValue();
        if (p == null) {
            labelStockDispo.setText("Sélectionnez un produit");
            return;
        }
        
        String type = comboType.getValue();
        if ("ENTREE".equals(type) || type == null) {
            labelStockDispo.setText(p.getStockTotal() + " (Total global)");
        } else {
            Entrepot e = comboEntrepotSource.getValue();
            if (e != null) {
                // Simplification pour l'affichage (nécessite idéalement un appel StockDAO)
                labelStockDispo.setText("Vérification en cours...");
                Task<Integer> t = new Task<>() {
                    @Override protected Integer call() {
                        return new com.inventaire.dao.StockDAO(DatabaseConnection.getInstance())
                                .getQuantite(p.getId(), e.getId());
                    }
                };
                t.setOnSucceeded(ev -> labelStockDispo.setText(t.getValue() + " dans " + e.getNom()));
                new Thread(t).start();
            } else {
                labelStockDispo.setText("Sélectionnez l'entrepôt source");
            }
        }
    }

    @FXML
    private void validerTransaction() {
        labelErreur.setText("");
        labelErreur.setStyle("-fx-text-fill: #d32f2f;");

        String type = comboType.getValue();
        Produit produit = comboProduit.getValue();
        String qteStr = champQuantite.getText();
        Entrepot source = comboEntrepotSource.getValue();
        Entrepot dest = comboEntrepotDest.getValue();
        String tiers = champTiers.getText();
        String doc = champDocument.getText();

        if (type == null) { labelErreur.setText("Sélectionnez un type d'opération"); return; }
        if (produit == null) { labelErreur.setText("Sélectionnez un produit"); return; }
        
        String errQte = ValidationUtil.validerQuantite(qteStr);
        if (errQte != null) { labelErreur.setText(errQte); return; }
        int quantite = Integer.parseInt(qteStr.trim());

        Utilisateur operateur = SessionManager.getInstance().getUtilisateur();

        setEnChargement(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return switch (type) {
                    case "ENTREE" -> {
                        if (dest == null) throw new IllegalArgumentException("Entrepôt destination requis");
                        yield blockchainService.enregistrerEntree(produit.getId(), quantite, dest.getId(), tiers, doc, operateur);
                    }
                    case "SORTIE" -> {
                        if (source == null) throw new IllegalArgumentException("Entrepôt source requis");
                        yield blockchainService.enregistrerSortie(produit.getId(), quantite, source.getId(), tiers, doc, operateur);
                    }
                    case "TRANSFERT" -> {
                        if (source == null || dest == null) throw new IllegalArgumentException("Entrepôts source et destination requis");
                        yield blockchainService.enregistrerTransfert(produit.getId(), quantite, source.getId(), dest.getId(), tiers, operateur);
                    }
                    default -> throw new IllegalArgumentException("Type inconnu");
                };
            }
        };

        task.setOnSucceeded(e -> {
            setEnChargement(false);
            labelErreur.setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");
            labelErreur.setText("Succès ! Transaction validée sur la blockchain.\nID : " + task.getValue());
            effacerFormulaireSansMessage();
        });

        task.setOnFailed(e -> {
            setEnChargement(false);
            labelErreur.setText("Erreur : " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void effacerFormulaire() {
        effacerFormulaireSansMessage();
        labelErreur.setText("");
    }
    
    private void effacerFormulaireSansMessage() {
        comboType.setValue(null);
        comboProduit.setValue(null);
        champQuantite.clear();
        comboEntrepotSource.setValue(null);
        comboEntrepotDest.setValue(null);
        champTiers.clear();
        champDocument.clear();
        labelStockDispo.setText("Sélectionnez un produit");
    }

    private void setEnChargement(boolean enChargement) {
        btnValider.setDisable(enChargement);
        progressIndicator.setVisible(enChargement);
    }

    // Navigation
    @FXML private void allerVersDashboard() { MainApp.changerEcran("dashboard.fxml"); }
    @FXML private void allerVersProduits() { MainApp.changerEcran("produits.fxml"); }
    @FXML private void allerVersHistorique() { MainApp.changerEcran("historique.fxml"); }
    @FXML private void allerVersAlertes() { MainApp.changerEcran("alertes.fxml"); }
    @FXML private void allerVersRapports() { MainApp.changerEcran("rapports.fxml"); }
    @FXML private void deconnecter() {
        SessionManager.getInstance().deconnecter();
        MainApp.changerEcran("login.fxml");
    }
}
