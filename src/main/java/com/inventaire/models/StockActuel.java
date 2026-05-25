package com.inventaire.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modèle métier représentant le stock actuel d'un produit dans un entrepôt.
 *
 * <p>Correspond à la table {@code stock_actuel} de PostgreSQL.
 * Cette table est le snapshot rapide du stock, mis à jour après chaque
 * transaction confirmée sur la blockchain.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class StockActuel {

    /** UUID du produit. */
    private UUID produitId;

    /** Référence du produit (jointure). */
    private String produitReference;

    /** Nom du produit (jointure). */
    private String produitNom;

    /** Seuil critique du produit (jointure). */
    private int seuilCritique;

    /** Seuil de réapprovisionnement du produit (jointure). */
    private int seuilReapprovisionnement;

    /** UUID de l'entrepôt. */
    private UUID entrepotId;

    /** Nom de l'entrepôt (jointure). */
    private String entrepotNom;

    /** Quantité actuellement disponible. */
    private int quantite;

    /** Date et heure de la dernière mise à jour. */
    private LocalDateTime derniereMaj;

    // ================================================================
    // Constructeurs
    // ================================================================

    /** Constructeur vide. */
    public StockActuel() {}

    /**
     * Constructeur complet.
     *
     * @param produitId   UUID du produit
     * @param entrepotId  UUID de l'entrepôt
     * @param quantite    Quantité en stock
     */
    public StockActuel(UUID produitId, UUID entrepotId, int quantite) {
        this.produitId = produitId;
        this.entrepotId = entrepotId;
        this.quantite = quantite;
        this.derniereMaj = LocalDateTime.now();
    }

    // ================================================================
    // Méthodes métier
    // ================================================================

    /**
     * Détermine le statut du stock.
     *
     * @return "CRITIQUE", "FAIBLE" ou "OK"
     */
    public String getStatut() {
        if (quantite < seuilCritique)             return "CRITIQUE";
        if (quantite < seuilReapprovisionnement)  return "FAIBLE";
        return "OK";
    }

    /**
     * Calcule le déficit par rapport au seuil critique.
     *
     * @return Quantité manquante pour atteindre le seuil critique (0 si OK)
     */
    public int getDeficit() {
        return Math.max(0, seuilCritique - quantite);
    }

    /**
     * Vérifie si le stock est suffisant pour une sortie donnée.
     *
     * @param quantiteDemandee Quantité à sortir
     * @return {@code true} si le stock >= quantité demandée
     */
    public boolean estSuffisantPour(int quantiteDemandee) {
        return quantite >= quantiteDemandee;
    }

    // ================================================================
    // Getters et Setters
    // ================================================================

    public UUID getProduitId()                      { return produitId; }
    public void setProduitId(UUID id)               { this.produitId = id; }

    public String getProduitReference()             { return produitReference; }
    public void setProduitReference(String ref)     { this.produitReference = ref; }

    public String getProduitNom()                   { return produitNom; }
    public void setProduitNom(String nom)           { this.produitNom = nom; }

    public int getSeuilCritique()                   { return seuilCritique; }
    public void setSeuilCritique(int s)             { this.seuilCritique = s; }

    public int getSeuilReapprovisionnement()        { return seuilReapprovisionnement; }
    public void setSeuilReapprovisionnement(int s)  { this.seuilReapprovisionnement = s; }

    public UUID getEntrepotId()                     { return entrepotId; }
    public void setEntrepotId(UUID id)              { this.entrepotId = id; }

    public String getEntrepotNom()                  { return entrepotNom; }
    public void setEntrepotNom(String nom)          { this.entrepotNom = nom; }

    public int getQuantite()                        { return quantite; }
    public void setQuantite(int quantite)           { this.quantite = quantite; }

    public LocalDateTime getDerniereMaj()           { return derniereMaj; }
    public void setDerniereMaj(LocalDateTime d)     { this.derniereMaj = d; }

    @Override
    public String toString() {
        return "Stock{produit='" + produitReference + "', entrepot='"
                + entrepotNom + "', quantite=" + quantite + "}";
    }
}
