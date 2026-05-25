package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Produit;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.AlerteService;
import com.inventaire.session.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class AlertesController {

    @FXML private Label labelUtilisateur;
    @FXML private Label lblNbCritiques;
    @FXML private Label lblNbFaibles;

    @FXML private TableView<Produit> tableCritiques;
    @FXML private TableColumn<Produit, String> colCritRef;
    @FXML private TableColumn<Produit, String> colCritNom;
    @FXML private TableColumn<Produit, Integer> colCritStock;
    @FXML private TableColumn<Produit, Integer> colCritSeuil;
    @FXML private TableColumn<Produit, String> colCritEcart;

    @FXML private TableView<Produit> tableFaibles;
    @FXML private TableColumn<Produit, String> colFaiRef;
    @FXML private TableColumn<Produit, String> colFaiNom;
    @FXML private TableColumn<Produit, Integer> colFaiStock;
    @FXML private TableColumn<Produit, Integer> colFaiSeuil;
    @FXML private TableColumn<Produit, String> colFaiEcart;

    private AlerteService alerteService;

    @FXML
    public void initialize() {
        alerteService = new AlerteService(DatabaseConnection.getInstance());

        Utilisateur user = SessionManager.getInstance().getUtilisateur();
        if (user != null) {
            labelUtilisateur.setText(user.getNomComplet() + "\n" + user.getRole());
        }

        configurerTables();
        chargerAlertes();
    }

    private void configurerTables() {
        // Table Critiques
        colCritRef.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colCritNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colCritStock.setCellValueFactory(new PropertyValueFactory<>("stockTotal"));
        colCritSeuil.setCellValueFactory(new PropertyValueFactory<>("seuilCritique"));
        colCritEcart.setCellValueFactory(cellData -> {
            Produit p = cellData.getValue();
            int ecart = p.getSeuilCritique() - p.getStockTotal();
            return new SimpleStringProperty("-" + ecart);
        });

        // Table Faibles
        colFaiRef.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colFaiNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colFaiStock.setCellValueFactory(new PropertyValueFactory<>("stockTotal"));
        // Simplification : on utilise le seuil critique pour l'affichage, idéalement on aurait un seuilAlerte
        colFaiSeuil.setCellValueFactory(new PropertyValueFactory<>("seuilCritique"));
        colFaiEcart.setCellValueFactory(cellData -> {
            Produit p = cellData.getValue();
            int ecart = p.getSeuilCritique() - p.getStockTotal();
            return new SimpleStringProperty(ecart > 0 ? "-" + ecart : "+" + Math.abs(ecart));
        });
    }

    @FXML
    private void chargerAlertes() {
        Task<Void> task = new Task<>() {
            List<Produit> critiques;
            List<Produit> faibles;

            @Override
            protected Void call() {
                critiques = alerteService.getAlertesCritiques();
                faibles = alerteService.getAlertesFaibles();
                return null;
            }

            @Override
            protected void succeeded() {
                tableCritiques.setItems(FXCollections.observableArrayList(critiques));
                tableFaibles.setItems(FXCollections.observableArrayList(faibles));
                lblNbCritiques.setText(String.valueOf(critiques.size()));
                lblNbFaibles.setText(String.valueOf(faibles.size()));
            }
        };
        new Thread(task).start();
    }

    // Navigation
    @FXML private void allerVersDashboard() { MainApp.changerEcran("dashboard.fxml"); }
    @FXML private void allerVersProduits() { MainApp.changerEcran("produits.fxml"); }
    @FXML private void allerVersTransactions() { MainApp.changerEcran("transaction.fxml"); }
    @FXML private void allerVersHistorique() { MainApp.changerEcran("historique.fxml"); }
    @FXML private void allerVersRapports() { MainApp.changerEcran("rapports.fxml"); }
    @FXML private void deconnecter() {
        SessionManager.getInstance().deconnecter();
        MainApp.changerEcran("login.fxml");
    }
}
