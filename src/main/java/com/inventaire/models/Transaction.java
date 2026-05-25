package com.inventaire.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modèle métier représentant une transaction d'inventaire.
 *
 * <p>Miroir PostgreSQL du ledger Hyperledger Fabric.
 * Correspond à la table {@code transactions_cache}.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class Transaction {

    /** Identifiant unique UUID interne PostgreSQL. */
    private UUID id;

    /** Identifiant de transaction retourné par Hyperledger Fabric. */
    private String blockchainTxId;

    /** Numéro du bloc sur la blockchain. */
    private Long blocNumero;

    /** Hash du bloc sur la blockchain. */
    private String blocHash;

    /** Type de transaction : ENTREE | SORTIE | TRANSFERT. */
    private String type;

    /** Statut : EN_ATTENTE | CONFIRMEE | ECHOUEE. */
    private String statut;

    /** UUID du produit concerné. */
    private UUID produitId;

    /** Nom du produit (jointure — non persisté directement). */
    private String produitNom;

    /** Référence du produit (jointure). */
    private String produitReference;

    /** Quantité de la transaction. */
    private int quantite;

    /** Stock avant la transaction. */
    private Integer quantiteAvant;

    /** Stock après la transaction. */
    private Integer quantiteApres;

    /** UUID de l'opérateur ayant effectué la transaction. */
    private UUID operateurId;

    /** Nom complet de l'opérateur (jointure). */
    private String operateurNom;

    /** UUID de l'entrepôt source (pour SORTIE et TRANSFERT). */
    private UUID entrepotSourceId;

    /** Nom de l'entrepôt source (jointure). */
    private String entrepotSourceNom;

    /** UUID de l'entrepôt destination (pour ENTREE et TRANSFERT). */
    private UUID entrepotDestinationId;

    /** Nom de l'entrepôt destination (jointure). */
    private String entrepotDestinationNom;

    /** Métadonnées JSON (fournisseur, client, numéro BL, etc.). */
    private String metadata;

    /** Commentaire libre. */
    private String commentaire;

    /** Date et heure d'enregistrement. */
    private LocalDateTime enregistreLe;

    /** Date et heure de confirmation blockchain. */
    private LocalDateTime confirmeLe;

    // ================================================================
    // Constructeurs
    // ================================================================

    /** Constructeur vide. */
    public Transaction() {}

    /**
     * Constructeur pour une nouvelle transaction.
     *
     * @param type          Type de transaction
     * @param produitId     UUID du produit
     * @param quantite      Quantité
     * @param operateurId   UUID de l'opérateur
     */
    public Transaction(String type, UUID produitId, int quantite, UUID operateurId) {
        this.type = type;
        this.produitId = produitId;
        this.quantite = quantite;
        this.operateurId = operateurId;
        this.statut = "EN_ATTENTE";
    }

    // ================================================================
    // Méthodes métier
    // ================================================================

    /**
     * Retourne l'icône emoji associée au type de transaction.
     *
     * @return "📥" pour ENTREE, "📤" pour SORTIE, "🔄" pour TRANSFERT
     */
    public String getIconeType() {
        return switch (type) {
            case "ENTREE"   -> "📥";
            case "SORTIE"   -> "📤";
            case "TRANSFERT"-> "🔄";
            default         -> "❓";
        };
    }

    /**
     * Vérifie si la transaction est confirmée sur la blockchain.
     *
     * @return {@code true} si le statut est CONFIRMEE
     */
    public boolean estConfirmee() {
        return "CONFIRMEE".equals(statut);
    }

    /**
     * Vérifie si la transaction a échoué.
     *
     * @return {@code true} si le statut est ECHOUEE
     */
    public boolean estEchouee() {
        return "ECHOUEE".equals(statut);
    }

    /**
     * Retourne un résumé court pour l'affichage dans les tableaux.
     *
     * @return Description courte de la transaction
     */
    public String getResumeCourt() {
        return getIconeType() + " " + type + " | " + produitReference + " | Qté: " + quantite;
    }

    // ================================================================
    // Getters et Setters
    // ================================================================

    public UUID getId()                             { return id; }
    public void setId(UUID id)                      { this.id = id; }

    public String getBlockchainTxId()               { return blockchainTxId; }
    public void setBlockchainTxId(String txId)      { this.blockchainTxId = txId; }

    public Long getBlocNumero()                     { return blocNumero; }
    public void setBlocNumero(Long n)               { this.blocNumero = n; }

    public String getBlocHash()                     { return blocHash; }
    public void setBlocHash(String hash)            { this.blocHash = hash; }

    public String getType()                         { return type; }
    public void setType(String type)                { this.type = type; }

    public String getStatut()                       { return statut; }
    public void setStatut(String statut)            { this.statut = statut; }

    public UUID getProduitId()                      { return produitId; }
    public void setProduitId(UUID id)               { this.produitId = id; }

    public String getProduitNom()                   { return produitNom; }
    public void setProduitNom(String nom)           { this.produitNom = nom; }

    public String getProduitReference()             { return produitReference; }
    public void setProduitReference(String ref)     { this.produitReference = ref; }

    public int getQuantite()                        { return quantite; }
    public void setQuantite(int quantite)           { this.quantite = quantite; }

    public Integer getQuantiteAvant()               { return quantiteAvant; }
    public void setQuantiteAvant(Integer q)         { this.quantiteAvant = q; }

    public Integer getQuantiteApres()               { return quantiteApres; }
    public void setQuantiteApres(Integer q)         { this.quantiteApres = q; }

    public UUID getOperateurId()                    { return operateurId; }
    public void setOperateurId(UUID id)             { this.operateurId = id; }

    public String getOperateurNom()                 { return operateurNom; }
    public void setOperateurNom(String nom)         { this.operateurNom = nom; }

    public UUID getEntrepotSourceId()               { return entrepotSourceId; }
    public void setEntrepotSourceId(UUID id)        { this.entrepotSourceId = id; }

    public String getEntrepotSourceNom()            { return entrepotSourceNom; }
    public void setEntrepotSourceNom(String nom)    { this.entrepotSourceNom = nom; }

    public UUID getEntrepotDestinationId()          { return entrepotDestinationId; }
    public void setEntrepotDestinationId(UUID id)   { this.entrepotDestinationId = id; }

    public String getEntrepotDestinationNom()       { return entrepotDestinationNom; }
    public void setEntrepotDestinationNom(String n) { this.entrepotDestinationNom = n; }

    public String getMetadata()                     { return metadata; }
    public void setMetadata(String metadata)        { this.metadata = metadata; }

    public String getCommentaire()                  { return commentaire; }
    public void setCommentaire(String commentaire)  { this.commentaire = commentaire; }

    public LocalDateTime getEnregistreLe()          { return enregistreLe; }
    public void setEnregistreLe(LocalDateTime d)    { this.enregistreLe = d; }

    public LocalDateTime getConfirmeLe()            { return confirmeLe; }
    public void setConfirmeLe(LocalDateTime d)      { this.confirmeLe = d; }

    @Override
    public String toString() {
        return "Transaction{type='" + type + "', produit='" + produitReference
                + "', quantite=" + quantite + ", statut='" + statut + "'}";
    }
}
