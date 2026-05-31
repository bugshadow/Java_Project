package com.inventaire.controllers;

import com.inventaire.MainApp;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.models.Produit;
import com.inventaire.models.Utilisateur;
import com.inventaire.services.InventaireService;
import com.inventaire.session.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProduitsController {

    private static final class CategoryItem {
        private final UUID id;
        private final String nom;

        private CategoryItem(UUID id, String nom) {
            this.id = id;
            this.nom = nom;
        }

        @Override
        public String toString() {
            return nom;
        }
    }

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
            private final Button btnModifier = new Button("Edit");
            private final HBox container = new HBox(btnModifier);

            {
                btnModifier.setStyle("-fx-background-color: transparent; -fx-text-fill: #1a237e; -fx-cursor: hand;");
                btnModifier.setOnAction(event -> {
                    Produit produit = getTableRow() == null ? null : getTableRow().getItem();
                    if (produit != null) {
                        ouvrirDialogEditionProduit(produit);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(container);
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
        ouvrirDialogProduit(null);
    }

    private void ouvrirDialogEditionProduit(Produit produit) {
        ouvrirDialogProduit(produit);
    }

    private void ouvrirDialogProduit(Produit produitExistante) {
        Dialog<Produit> dialog = new Dialog<>();
        boolean modeEdition = produitExistante != null;
        dialog.setTitle(modeEdition ? "Modifier produit" : "Nouveau produit");
        dialog.setHeaderText(modeEdition ? "Modifier le produit" : "Créer un nouveau produit");

        ButtonType boutonCreer = new ButtonType("Creer", ButtonBar.ButtonData.OK_DONE);
        ButtonType boutonValider = new ButtonType(modeEdition ? "Enregistrer" : "Creer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(boutonValider, ButtonType.CANCEL);

        TextField champReference = new TextField(produitExistante == null ? "" : produitExistante.getReference());
        TextField champNom = new TextField(produitExistante == null ? "" : produitExistante.getNom());
        TextArea champDescription = new TextArea();
        champDescription.setText(produitExistante == null ? "" : produitExistante.getDescription());
        champDescription.setPrefRowCount(3);
        ComboBox<CategoryItem> champCategorie = new ComboBox<>();
        ObservableList<CategoryItem> categories = chargerCategories();
        champCategorie.setItems(categories);
        champCategorie.setPromptText("Choisir une categorie");
        TextField champUnite = new TextField(produitExistante == null || produitExistante.getUniteMesure() == null ? "unite" : produitExistante.getUniteMesure());
        TextField champSeuilCritique = new TextField(String.valueOf(produitExistante == null ? 10 : produitExistante.getSeuilCritique()));
        TextField champSeuilReapprovisionnement = new TextField(String.valueOf(produitExistante == null ? 20 : produitExistante.getSeuilReapprovisionnement()));
        TextField champPrix = new TextField(produitExistante == null || produitExistante.getPrixUnitaire() == null ? "" : produitExistante.getPrixUnitaire().toPlainString());

        if (produitExistante != null && produitExistante.getCategorieId() != null) {
            for (CategoryItem categoryItem : categories) {
                if (produitExistante.getCategorieId().equals(categoryItem.id)) {
                    champCategorie.getSelectionModel().select(categoryItem);
                    break;
                }
            }
        }

        champReference.setDisable(modeEdition);

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        grille.add(new Label("Reference *"), 0, 0);
        grille.add(champReference, 1, 0);
        grille.add(new Label("Nom *"), 0, 1);
        grille.add(champNom, 1, 1);
        grille.add(new Label("Description"), 0, 2);
        grille.add(champDescription, 1, 2);
        grille.add(new Label("Categorie *"), 0, 3);
        grille.add(champCategorie, 1, 3);
        grille.add(new Label("Unite"), 0, 4);
        grille.add(champUnite, 1, 4);
        grille.add(new Label("Seuil critique"), 0, 5);
        grille.add(champSeuilCritique, 1, 5);
        grille.add(new Label("Seuil reapprovisionnement"), 0, 6);
        grille.add(champSeuilReapprovisionnement, 1, 6);
        grille.add(new Label("Prix unitaire"), 0, 7);
        grille.add(champPrix, 1, 7);

        dialog.getDialogPane().setContent(grille);

        dialog.setResultConverter(button -> {
            if (button != boutonValider) {
                return null;
            }

            String reference = champReference.getText() == null ? "" : champReference.getText().trim();
            String nom = champNom.getText() == null ? "" : champNom.getText().trim();
            CategoryItem categorie = champCategorie.getValue();

            if (reference.isEmpty() || nom.isEmpty() || categorie == null) {
                return null;
            }

            int seuilCritique = parseEntier(champSeuilCritique.getText(), 10);
            int seuilReapprovisionnement = parseEntier(champSeuilReapprovisionnement.getText(), 20);
            BigDecimal prixUnitaire = parseBigDecimal(champPrix.getText());

            Produit produit = produitExistante == null
                ? new Produit(
                    reference,
                    nom,
                    categorie.id,
                    champUnite.getText() == null || champUnite.getText().isBlank() ? "unite" : champUnite.getText().trim(),
                    seuilCritique,
                    seuilReapprovisionnement
                )
                : produitExistante;

            produit.setReference(reference);
            produit.setNom(nom);
            produit.setCategorieId(categorie.id);
            produit.setUniteMesure(champUnite.getText() == null || champUnite.getText().isBlank() ? "unite" : champUnite.getText().trim());
            produit.setSeuilCritique(seuilCritique);
            produit.setSeuilReapprovisionnement(seuilReapprovisionnement);
            produit.setDescription(champDescription.getText());
            produit.setPrixUnitaire(prixUnitaire);

            return produit;
        });

        dialog.showAndWait().ifPresent(produit -> {
            boolean cree = inventaireService.sauvegarderProduit(produit);
            if (cree) {
                chargerProduits(champRecherche.getText());
                new Alert(Alert.AlertType.INFORMATION, "Produit cree avec succes.").showAndWait();
            } else {
                new Alert(Alert.AlertType.ERROR, "Impossible de creer le produit.").showAndWait();
            }
        });
    }

    private ObservableList<CategoryItem> chargerCategories() {
        List<CategoryItem> categories = new ArrayList<>();
        String sql = "SELECT id, nom FROM categories ORDER BY nom";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                categories.add(new CategoryItem(
                    (UUID) rs.getObject("id"),
                    corrigerMojibake(rs.getString("nom"))
                ));
            }
        } catch (SQLException e) {
            System.out.println("Impossible de charger les categories : " + e.getMessage());
        }

        return FXCollections.observableArrayList(categories);
    }

    private int parseEntier(String valeur, int defaut) {
        try {
            if (valeur == null || valeur.isBlank()) {
                return defaut;
            }
            return Integer.parseInt(valeur.trim());
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    private BigDecimal parseBigDecimal(String valeur) {
        try {
            if (valeur == null || valeur.isBlank()) {
                return null;
            }
            return new BigDecimal(valeur.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String corrigerMojibake(String texte) {
        if (texte == null || texte.isBlank()) {
            return texte;
        }

        if (!texte.contains("Ã") && !texte.contains("Â") && !texte.contains("�")) {
            return texte;
        }

        try {
            return new String(texte.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return texte;
        }
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
