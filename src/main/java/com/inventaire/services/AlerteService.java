package com.inventaire.services;

import com.inventaire.dao.DatabaseConnection;
import com.inventaire.dao.ProduitDAO;
import com.inventaire.dao.StockDAO;
import com.inventaire.models.Produit;
import com.inventaire.models.StockActuel;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service de gestion des alertes de stock.
 *
 * <p>Détecte les produits dont le stock passe sous les seuils configurés
 * et notifie l'interface utilisateur via des callbacks JavaFX thread-safe.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class AlerteService {

    private static final Logger LOG = LoggerFactory.getLogger(AlerteService.class);

    private final ProduitDAO produitDAO;
    private final StockDAO stockDAO;

    /** Nombre d'alertes critiques actives (mis à jour dynamiquement). */
    private final AtomicInteger nombreAlertesCritiques = new AtomicInteger(0);

    /** Callback appelé quand le nombre d'alertes change (pour mettre à jour la sidebar). */
    private Consumer<Integer> callbackChangementAlertes;

    /**
     * Constructeur.
     *
     * @param db Instance de connexion à PostgreSQL
     */
    public AlerteService(DatabaseConnection db) {
        this.produitDAO = new ProduitDAO(db);
        this.stockDAO = new StockDAO(db);
    }

    // ================================================================
    // Configuration des callbacks
    // ================================================================

    /**
     * Configure le callback appelé lors d'un changement du nombre d'alertes.
     *
     * @param callback Fonction recevant le nouveau nombre d'alertes critiques
     */
    public void setCallbackChangementAlertes(Consumer<Integer> callback) {
        this.callbackChangementAlertes = callback;
    }

    // ================================================================
    // Vérification des alertes
    // ================================================================

    /**
     * Vérifie les alertes de stock pour un produit spécifique.
     * Appelé systématiquement après chaque transaction.
     *
     * @param produitId UUID du produit à vérifier
     */
    public void verifierApresTransaction(UUID produitId) {
        try {
            List<StockActuel> stocks = stockDAO.getStockParProduit(produitId);
            for (StockActuel stock : stocks) {
                if (stock.getQuantite() < stock.getSeuilCritique()) {
                    LOG.warn("⚠️ ALERTE CRITIQUE : produit={}, entrepôt={}, stock={}, seuil={}",
                        stock.getProduitReference(), stock.getEntrepotNom(),
                        stock.getQuantite(), stock.getSeuilCritique());
                }
            }

            // Mettre à jour le compteur global
            actualiserCompteurAlertes();

        } catch (Exception e) {
            LOG.error("Erreur vérification alertes pour produit {} : {}", produitId, e.getMessage());
        }
    }

    /**
     * Retourne la liste complète des produits en alerte critique.
     *
     * @return Liste des produits dont le stock est inférieur au seuil critique
     */
    public List<Produit> getAlertesCritiques() {
        try {
            return produitDAO.trouverProduitsEnAlerteCritique();
        } catch (Exception e) {
            LOG.error("Erreur récupération alertes critiques : {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Retourne la liste des produits nécessitant un réapprovisionnement.
     *
     * @return Liste des produits dont le stock est inférieur au seuil de réapprovisionnement
     */
    public List<Produit> getAlertesFaibles() {
        try {
            return produitDAO.trouverProduitsAReapprovisionner();
        } catch (Exception e) {
            LOG.error("Erreur récupération alertes faibles : {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Retourne le nombre total d'alertes actives (critiques + faibles).
     *
     * @return Nombre d'alertes
     */
    public int getNombreAlertesCritiques() {
        return nombreAlertesCritiques.get();
    }

    /**
     * Actualise le compteur d'alertes et notifie l'interface utilisateur.
     */
    public void actualiserCompteurAlertes() {
        try {
            int nbAlertes = getAlertesCritiques().size();
            int ancienNombre = nombreAlertesCritiques.getAndSet(nbAlertes);

            if (nbAlertes != ancienNombre && callbackChangementAlertes != null) {
                // Mise à jour UI sur le thread JavaFX
                Platform.runLater(() -> callbackChangementAlertes.accept(nbAlertes));
            }
        } catch (Exception e) {
            LOG.error("Erreur actualisation compteur alertes : {}", e.getMessage());
        }
    }
}
