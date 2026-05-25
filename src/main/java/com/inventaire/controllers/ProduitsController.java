package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Produit;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.InventaireService;
import com.inventaire.session.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.util.List;

public class ProduitsController {

    @FXML private Label labelUtilisateur;
    @FXML private TextField champRecherche;
    @FXML private TableView<Produit> tableProduits;
    @FXML private TableColumn<Produit, String> colReference;
    @FXML private TableColumn<Produit, String> colNom;
    @FXML private TableColumn<Produit, String> colCategorie;
    @FXML private TableColumn<Produit, Double> colPrix;
    @FXML private TableColumn<Produit, Integer> colStock;
    @FXML private TableColumn<Produit, String> colStatut;
    @FXML private TableColumn<Produit, Void> colActions;

    private InventaireService inventaireService;

    @FXML
    public void initialize() {
        inventaireService = new InventaireService(DatabaseConnection.getInstance());

        Utilisateur user = SessionManager.getInstance().getUtilisateur();
        if (user != null) {
            labelUtilisateur.setText(user.getNomComplet() + "\n" + user.getRole());
        }

        configurerTable();
        chargerProduits(null);

        champRecherche.setOnAction(e -> filtrerProduits());
    }

    private void configurerTable() {
        colReference.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colCategorie.setCellValueFactory(new PropertyValueFactory<>("categorieNom"));
        colPrix.setCellValueFactory(new PropertyValueFactory<>("prixUnitaire"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stockTotal"));
        
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        // Rendu conditionnel pour le statut
        colStatut.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label lbl = new Label(item);
                    if ("CRITIQUE".equals(item)) {
                        lbl.getStyleClass().add("badge-critique");
                    } else if ("FAIBLE".equals(item)) {
                        lbl.getStyleClass().add("badge-faible");
                    } else {
                        lbl.getStyleClass().add("badge-ok");
                    }
                    setGraphic(lbl);
                }
            }
        });

        // Colonne Actions
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnModifier = new Button("✎");
            {
                btnModifier.setStyle("-fx-background-color: transparent; -fx-text-fill: #1a237e; -fx-cursor: hand;");
                btnModifier.setOnAction(event -> {
                    Produit p = getTableView().getItems().get(getIndex());
                    // Logique pour ouvrir un dialogue de modification (simplifiée ici)
                    System.out.println("Modifier produit : " + p.getNom());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox pane = new HBox(btnModifier);
                    setGraphic(pane);
                }
            }
        });
    }

    private void chargerProduits(String recherche) {
        Task<List<Produit>> task = new Task<>() {
            @Override
            protected List<Produit> call() {
                return inventaireService.rechercherProduits(recherche, null, null, 0, 100);
            }
        };
        task.setOnSucceeded(e -> tableProduits.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    @FXML private void filtrerProduits() { chargerProduits(champRecherche.getText()); }
    @FXML private void reinitialiserFiltres() {
        champRecherche.clear();
        chargerProduits(null);
    }
    
    @FXML private void ouvrirDialogNouveauProduit() {
        // Implémentation du dialogue de création (hors périmètre immédiat)
        System.out.println("Ouvrir dialogue création produit");
    }

    // Navigation
    @FXML private void allerVersDashboard() { MainApp.changerEcran("dashboard.fxml"); }
    @FXML private void allerVersTransactions() { MainApp.changerEcran("transaction.fxml"); }
    @FXML private void allerVersHistorique() { MainApp.changerEcran("historique.fxml"); }
    @FXML private void allerVersAlertes() { MainApp.changerEcran("alertes.fxml"); }
    @FXML private void allerVersRapports() { MainApp.changerEcran("rapports.fxml"); }
    @FXML private void deconnecter() {
        SessionManager.getInstance().deconnecter();
        MainApp.changerEcran("login.fxml");
    }
}
