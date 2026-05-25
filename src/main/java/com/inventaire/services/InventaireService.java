package com.inventaire.services;

import com.inventaire.dao.DatabaseConnection;
import com.inventaire.dao.EntrepotDAO;
import com.inventaire.dao.ProduitDAO;
import com.inventaire.dao.TransactionDAO;
import com.inventaire.models.Entrepot;
import com.inventaire.models.Produit;
import com.inventaire.models.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service métier pour la gestion de l'inventaire.
 *
 * <p>Agrège les opérations sur les produits, entrepôts et stock.
 * Les contrôleurs passent TOUJOURS par ce service, jamais directement
 * par les DAO.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class InventaireService {

    private static final Logger LOG = LoggerFactory.getLogger(InventaireService.class);

    private final ProduitDAO produitDAO;
    private final EntrepotDAO entrepotDAO;
    private final TransactionDAO transactionDAO;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param db Instance de connexion PostgreSQL
     */
    public InventaireService(DatabaseConnection db) {
        this.produitDAO = new ProduitDAO(db);
        this.entrepotDAO = new EntrepotDAO(db);
        this.transactionDAO = new TransactionDAO(db);
    }

    // ================================================================
    // Métriques Dashboard
    // ================================================================

    /**
     * Retourne le nombre de produits actifs.
     *
     * @return Nombre de produits actifs
     */
    public int getNombreProduitsActifs() {
        return produitDAO.compterProduitsActifs();
    }

    /**
     * Retourne le nombre de transactions enregistrées aujourd'hui.
     *
     * @return Nombre de transactions du jour
     */
    public int getTransactionsAujourdhui() {
        return transactionDAO.compterAujourdhui();
    }

    // ================================================================
    // Gestion des produits
    // ================================================================

    /**
     * Retourne tous les produits actifs.
     *
     * @return Liste des produits triés par nom
     */
    public List<Produit> getProduits() {
        return produitDAO.trouverTousActifs();
    }

    /**
     * Recherche des produits avec filtres.
     *
     * @param recherche   Texte de recherche
     * @param categorieId UUID de catégorie (peut être null)
     * @param entrepotId  UUID d'entrepôt (peut être null)
     * @param page        Numéro de page (base 0)
     * @param taillePage  Éléments par page
     * @return Liste filtrée
     */
    public List<Produit> rechercherProduits(String recherche, UUID categorieId,
                                             UUID entrepotId, int page, int taillePage) {
        return produitDAO.rechercherAvecFiltres(recherche, categorieId, entrepotId, page, taillePage);
    }

    /**
     * Récupère un produit par son UUID.
     *
     * @param id UUID du produit
     * @return Optional contenant le produit
     */
    public Optional<Produit> getProduit(UUID id) {
        return produitDAO.trouverParId(id);
    }

    /**
     * Crée ou met à jour un produit.
     *
     * @param produit Le produit à sauvegarder
     * @return {@code true} si l'opération a réussi
     */
    public boolean sauvegarderProduit(Produit produit) {
        if (produit.getId() == null) {
            // Création
            return produitDAO.creer(produit);
        } else {
            // Mise à jour
            return produitDAO.mettreAJour(produit);
        }
    }

    /**
     * Désactive un produit (suppression logique).
     *
     * @param produitId UUID du produit
     * @return {@code true} si la désactivation a réussi
     */
    public boolean desactiverProduit(UUID produitId) {
        return produitDAO.desactiver(produitId);
    }

    // ================================================================
    // Gestion des entrepôts
    // ================================================================

    /**
     * Retourne tous les entrepôts actifs.
     *
     * @return Liste des entrepôts
     */
    public List<Entrepot> getEntrepots() {
        return entrepotDAO.trouverTousActifs();
    }

    /**
     * Récupère un entrepôt par son UUID.
     *
     * @param id UUID de l'entrepôt
     * @return Optional contenant l'entrepôt
     */
    public Optional<Entrepot> getEntrepot(UUID id) {
        return entrepotDAO.trouverParId(id);
    }

    /**
     * Crée ou met à jour un entrepôt.
     *
     * @param entrepot L'entrepôt à sauvegarder
     * @return {@code true} si l'opération a réussi
     */
    public boolean sauvegarderEntrepot(Entrepot entrepot) {
        if (entrepot.getId() == null) {
            return entrepotDAO.creer(entrepot);
        } else {
            return entrepotDAO.mettreAJour(entrepot);
        }
    }

    // ================================================================
    // Historique des transactions
    // ================================================================

    /**
     * Retourne les N dernières transactions.
     *
     * @param limite Nombre de transactions à retourner
     * @return Liste des transactions récentes
     */
    public List<Transaction> getDernieresTransactions(int limite) {
        return transactionDAO.getDernieres(limite);
    }

    /**
     * Retourne les transactions d'un produit.
     *
     * @param produitId UUID du produit
     * @return Liste des transactions
     */
    public List<Transaction> getTransactionsProduit(UUID produitId) {
        return transactionDAO.getParProduit(produitId);
    }

    /**
     * Recherche des transactions avec filtres multiples.
     *
     * @param dateDebut   Date de début
     * @param dateFin     Date de fin
     * @param produitId   UUID produit (null = tous)
     * @param type        Type de transaction (null = tous)
     * @param operateurId UUID opérateur (null = tous)
     * @param statut      Statut (null = tous)
     * @return Liste filtrée
     */
    public List<Transaction> rechercherTransactions(
            java.time.LocalDate dateDebut, java.time.LocalDate dateFin,
            UUID produitId, String type, UUID operateurId, String statut) {

        return transactionDAO.rechercherAvecFiltres(
            dateDebut, dateFin, produitId, type, operateurId, statut);
    }
}
