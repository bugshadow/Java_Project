package com.inventaire.models;

import java.util.UUID;

/**
 * Modèle métier représentant un entrepôt de stockage.
 *
 * <p>Correspond à la table {@code entrepots} de PostgreSQL.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class Entrepot {

    /** Identifiant unique UUID. */
    private UUID id;

    /** Nom de l'entrepôt. */
    private String nom;

    /** Adresse physique complète. */
    private String adresse;

    /** UUID du responsable (lien vers utilisateurs). */
    private UUID responsableId;

    /** Nom complet du responsable (jointure). */
    private String responsableNom;

    /** Indique si l'entrepôt est actif. */
    private boolean actif;

    /** Métadonnées JSON flexibles. */
    private String metadata;

    /** Stock total dans cet entrepôt (calculé). */
    private int stockTotal;

    /** Nombre de références distinctes dans cet entrepôt (calculé). */
    private int nombreReferences;

    // ================================================================
    // Constructeurs
    // ================================================================

    /** Constructeur vide requis pour JDBC. */
    public Entrepot() {}

    /**
     * Constructeur pour la création d'un nouvel entrepôt.
     *
     * @param nom     Nom de l'entrepôt
     * @param adresse Adresse physique
     */
    public Entrepot(String nom, String adresse) {
        this.nom = nom;
        this.adresse = adresse;
        this.actif = true;
    }

    // ================================================================
    // Méthodes métier
    // ================================================================

    /**
     * Retourne une représentation courte pour les ComboBox.
     *
     * @return "Nom — Adresse" ou juste "Nom" si pas d'adresse
     */
    public String getLibelleCourt() {
        if (adresse != null && !adresse.isBlank()) {
            return nom + " — " + adresse.substring(0, Math.min(adresse.length(), 40));
        }
        return nom;
    }

    // ================================================================
    // Getters et Setters
    // ================================================================

    public UUID getId()                             { return id; }
    public void setId(UUID id)                      { this.id = id; }

    public String getNom()                          { return nom; }
    public void setNom(String nom)                  { this.nom = nom; }

    public String getAdresse()                      { return adresse; }
    public void setAdresse(String adresse)          { this.adresse = adresse; }

    public UUID getResponsableId()                  { return responsableId; }
    public void setResponsableId(UUID id)           { this.responsableId = id; }

    public String getResponsableNom()               { return responsableNom; }
    public void setResponsableNom(String nom)       { this.responsableNom = nom; }

    public boolean isActif()                        { return actif; }
    public void setActif(boolean actif)             { this.actif = actif; }

    public String getMetadata()                     { return metadata; }
    public void setMetadata(String metadata)        { this.metadata = metadata; }

    public int getStockTotal()                      { return stockTotal; }
    public void setStockTotal(int stock)            { this.stockTotal = stock; }

    public int getNombreReferences()                { return nombreReferences; }
    public void setNombreReferences(int n)          { this.nombreReferences = n; }

    @Override
    public String toString() {
        return nom;
    }
}
