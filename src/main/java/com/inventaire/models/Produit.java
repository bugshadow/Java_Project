package com.inventaire.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modèle métier représentant un produit dans le système d'inventaire.
 *
 * <p>Correspond à la table {@code produits} de PostgreSQL.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class Produit {

    /** Identifiant unique UUID généré par PostgreSQL. */
    private UUID id;

    /** Code produit unique (ex: PROD-001). */
    private String reference;

    /** Nom commercial du produit. */
    private String nom;

    /** Description détaillée. */
    private String description;

    /** Identifiant de la catégorie. */
    private UUID categorieId;

    /** Nom de la catégorie (jointure — non persisté directement). */
    private String categorieNom;

    /** Unité de mesure (unité, kg, litre…). */
    private String uniteMesure;

    /** Seuil en dessous duquel une alerte CRITIQUE est émise. */
    private int seuilCritique;

    /** Seuil en dessous duquel une alerte de réapprovisionnement est émise. */
    private int seuilReapprovisionnement;

    /** Prix unitaire HT. */
    private BigDecimal prixUnitaire;

    /** Chemin vers l'image du produit (optionnel). */
    private String imagePath;

    /** Indique si le produit est actif dans le système. */
    private boolean actif;

    /** Quantité totale en stock (calculée sur tous les entrepôts). */
    private int stockTotal;

    /** Métadonnées JSON flexibles. */
    private String metadata;

    /** Date de création. */
    private LocalDateTime creeLe;

    /** Date de dernière modification. */
    private LocalDateTime modifieLe;

    // ================================================================
    // Constructeurs
    // ================================================================

    /** Constructeur vide requis pour JDBC. */
    public Produit() {}

    /**
     * Constructeur pour la création d'un nouveau produit.
     *
     * @param reference                 Code unique du produit
     * @param nom                       Nom du produit
     * @param categorieId               UUID de la catégorie
     * @param uniteMesure               Unité de mesure
     * @param seuilCritique             Seuil d'alerte critique
     * @param seuilReapprovisionnement  Seuil de réapprovisionnement
     */
    public Produit(String reference, String nom, UUID categorieId,
                   String uniteMesure, int seuilCritique, int seuilReapprovisionnement) {
        this.reference = reference;
        this.nom = nom;
        this.categorieId = categorieId;
        this.uniteMesure = uniteMesure;
        this.seuilCritique = seuilCritique;
        this.seuilReapprovisionnement = seuilReapprovisionnement;
        this.actif = true;
    }

    // ================================================================
    // Méthodes métier
    // ================================================================

    /**
     * Détermine le statut de stock du produit.
     *
     * @return "CRITIQUE" si stock < seuilCritique,
     *         "FAIBLE" si stock < seuilReapprovisionnement,
     *         "OK" sinon
     */
    public String getStatutStock() {
        if (stockTotal < seuilCritique) {
            return "CRITIQUE";
        } else if (stockTotal < seuilReapprovisionnement) {
            return "FAIBLE";
        }
        return "OK";
    }

    /**
     * Calcule la valeur totale du stock pour ce produit.
     *
     * @return prixUnitaire × stockTotal, ou BigDecimal.ZERO si pas de prix
     */
    public BigDecimal getValeurStock() {
        if (prixUnitaire == null) return BigDecimal.ZERO;
        return prixUnitaire.multiply(BigDecimal.valueOf(stockTotal));
    }

    /**
     * Vérifie si une alerte est nécessaire.
     *
     * @return {@code true} si le stock est en dessous du seuil de réapprovisionnement
     */
    public boolean necessiteAlerte() {
        return stockTotal < seuilReapprovisionnement;
    }

    // ================================================================
    // Getters et Setters
    // ================================================================

    public UUID getId()                         { return id; }
    public void setId(UUID id)                  { this.id = id; }

    public String getReference()                { return reference; }
    public void setReference(String reference)  { this.reference = reference; }

    public String getNom()                      { return nom; }
    public void setNom(String nom)              { this.nom = nom; }

    public String getDescription()              { return description; }
    public void setDescription(String desc)     { this.description = desc; }

    public UUID getCategorieId()                { return categorieId; }
    public void setCategorieId(UUID catId)      { this.categorieId = catId; }

    public String getCategorieNom()             { return categorieNom; }
    public void setCategorieNom(String nom)     { this.categorieNom = nom; }

    public String getUniteMesure()              { return uniteMesure; }
    public void setUniteMesure(String um)       { this.uniteMesure = um; }

    public int getSeuilCritique()               { return seuilCritique; }
    public void setSeuilCritique(int s)         { this.seuilCritique = s; }

    public int getSeuilReapprovisionnement()    { return seuilReapprovisionnement; }
    public void setSeuilReapprovisionnement(int s) { this.seuilReapprovisionnement = s; }

    public BigDecimal getPrixUnitaire()         { return prixUnitaire; }
    public void setPrixUnitaire(BigDecimal p)   { this.prixUnitaire = p; }

    public String getImagePath()                { return imagePath; }
    public void setImagePath(String path)       { this.imagePath = path; }

    public boolean isActif()                    { return actif; }
    public void setActif(boolean actif)         { this.actif = actif; }

    public int getStockTotal()                  { return stockTotal; }
    public void setStockTotal(int stock)        { this.stockTotal = stock; }

    public String getMetadata()                 { return metadata; }
    public void setMetadata(String metadata)    { this.metadata = metadata; }

    public LocalDateTime getCreeLe()            { return creeLe; }
    public void setCreeLe(LocalDateTime d)      { this.creeLe = d; }

    public LocalDateTime getModifieLe()         { return modifieLe; }
    public void setModifieLe(LocalDateTime d)   { this.modifieLe = d; }

    @Override
    public String toString() {
        return "Produit{ref='" + reference + "', nom='" + nom + "', stock=" + stockTotal + "}";
    }
}
