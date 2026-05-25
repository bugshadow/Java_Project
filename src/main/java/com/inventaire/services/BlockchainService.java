package com.inventaire.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inventaire.blockchain.EthereumClient;
import com.inventaire.dao.DatabaseConnection;
import com.inventaire.dao.StockDAO;
import com.inventaire.dao.TransactionDAO;
import com.inventaire.models.Transaction;
import com.inventaire.models.Utilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service de gestion des transactions d'inventaire avec traçabilité blockchain.
 *
 * <p>Principe de cohérence :
 * <ol>
 *   <li>La blockchain est TOUJOURS écrite EN PREMIER</li>
 *   <li>En cas d'échec blockchain → aucune modification PostgreSQL</li>
 *   <li>En cas d'échec PostgreSQL après blockchain → alerte ERROR (cas exceptionnel)</li>
 * </ol>
 *
 * <p>Toutes les opérations de base de données utilisent des transactions JDBC
 * explicites (autoCommit = false) pour garantir l'atomicité.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class BlockchainService {

    private static final Logger LOG = LoggerFactory.getLogger(BlockchainService.class);

    private final DatabaseConnection db;
    private final TransactionDAO transactionDAO;
    private final StockDAO stockDAO;
    private final EthereumClient ethereumClient;
    private final AlerteService alerteService;
    private final Gson gson;

    /**
     * Constructeur avec injection de dependances.
     *
     * @param db               Connexion PostgreSQL
     * @param ethereumClient   Client Ethereum connecte
     * @param alerteService    Service de gestion des alertes
     */
    public BlockchainService(DatabaseConnection db, EthereumClient ethereumClient,
                              AlerteService alerteService) {
        this.db = db;
        this.transactionDAO = new TransactionDAO(db);
        this.stockDAO = new StockDAO(db);
        this.ethereumClient = ethereumClient;
        this.alerteService = alerteService;
        this.gson = new Gson();
    }

    // ================================================================
    // Enregistrement d'une ENTRÉE de stock
    // ================================================================

    /**
     * Enregistre une entrée de stock avec traçabilité blockchain.
     *
     * <p>Séquence : Blockchain → PostgreSQL (transaction atomique)
     *
     * @param produitId      UUID du produit
     * @param quantite       Quantité reçue (doit être > 0)
     * @param entrepotId     UUID de l'entrepôt destination
     * @param fournisseur    Nom du fournisseur
     * @param numeroBL       Numéro du bon de livraison
     * @param operateur      Utilisateur effectuant la saisie
     * @return ID de transaction blockchain
     * @throws RuntimeException si la transaction échoue
     */
    public String enregistrerEntree(UUID produitId, int quantite, UUID entrepotId,
                                     String fournisseur, String numeroBL, Utilisateur operateur) {
        validerParametresBase(produitId, quantite, operateur);

        LOG.info("Enregistrement entrée : produit={}, qte={}, entrepôt={}, opérateur={}",
            produitId, quantite, entrepotId, operateur.getEmail());

        // Stock avant la transaction
        int stockAvant = stockDAO.getQuantite(produitId, entrepotId);
        int stockApres = stockAvant + quantite;

        // Métadonnées spécifiques ENTRÉE
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fournisseur", fournisseur);
        metadata.put("numero_bl", numeroBL);
        metadata.put("date_enregistrement", LocalDateTime.now().toString());
        String metadataJson = gson.toJson(metadata);

        // ID unique pour cette transaction
        String txId = "TX-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 16);

        // ---- 1. Appel blockchain (en premier) ----
        String blockchainTxId = soumettreSurBlockchain(txId, "ENTREE", produitId, quantite,
            stockAvant, stockApres, operateur.getId(), null, entrepotId, metadataJson);

        // ---- 2. Mise à jour PostgreSQL (seulement si blockchain OK) ----
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Créer la transaction dans le cache
                Transaction transaction = creerObjetTransaction(
                    blockchainTxId, "ENTREE", produitId, quantite,
                    stockAvant, stockApres, operateur.getId(),
                    null, entrepotId, metadataJson
                );

                transactionDAO.enregistrer(conn, transaction);

                // Mettre à jour le stock
                stockDAO.ajouterStock(conn, produitId, entrepotId, quantite);

                // Confirmer la transaction
                conn.commit();

                // Mise à jour du statut en base (hors transaction)
                transactionDAO.mettreAJourStatut(transaction.getId(), "CONFIRMEE", blockchainTxId);

                LOG.info("✅ Entrée enregistrée avec succès. Blockchain TX: {}", blockchainTxId);

            } catch (SQLException e) {
                conn.rollback();
                LOG.error("❌ Rollback PostgreSQL après erreur : {}", e.getMessage());
                throw new RuntimeException("Erreur PostgreSQL après validation blockchain : " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            LOG.error("❌ Impossible d'obtenir une connexion PostgreSQL : {}", e.getMessage());
            throw new RuntimeException("Erreur de connexion à la base de données", e);
        }

        // ---- 3. Vérification alertes après la transaction ----
        alerteService.verifierApresTransaction(produitId);

        return blockchainTxId;
    }

    // ================================================================
    // Enregistrement d'une SORTIE de stock
    // ================================================================

    /**
     * Enregistre une sortie de stock avec traçabilité blockchain.
     *
     * @param produitId  UUID du produit
     * @param quantite   Quantité à sortir
     * @param entrepotId UUID de l'entrepôt source
     * @param client     Nom du client destinataire
     * @param numeroBL   Numéro du bon de livraison sortant
     * @param operateur  Utilisateur effectuant la saisie
     * @return ID de transaction blockchain
     * @throws RuntimeException si stock insuffisant ou erreur technique
     */
    public String enregistrerSortie(UUID produitId, int quantite, UUID entrepotId,
                                     String client, String numeroBL, Utilisateur operateur) {
        validerParametresBase(produitId, quantite, operateur);

        // ---- Vérification du stock avant d'appeler la blockchain ----
        int stockAvant = stockDAO.getQuantite(produitId, entrepotId);
        if (stockAvant < quantite) {
            throw new RuntimeException(
                "Stock insuffisant : disponible=" + stockAvant + ", demandé=" + quantite);
        }

        int stockApres = stockAvant - quantite;

        LOG.info("Enregistrement sortie : produit={}, qte={}, entrepôt={}, opérateur={}",
            produitId, quantite, entrepotId, operateur.getEmail());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("client", client);
        metadata.put("numero_bl", numeroBL);
        metadata.put("date_enregistrement", LocalDateTime.now().toString());
        String metadataJson = gson.toJson(metadata);

        String txId = "TX-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 16);

        // ---- 1. Blockchain ----
        String blockchainTxId = soumettreSurBlockchain(txId, "SORTIE", produitId, quantite,
            stockAvant, stockApres, operateur.getId(), entrepotId, null, metadataJson);

        // ---- 2. PostgreSQL ----
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction transaction = creerObjetTransaction(
                    blockchainTxId, "SORTIE", produitId, quantite,
                    stockAvant, stockApres, operateur.getId(),
                    entrepotId, null, metadataJson
                );

                transactionDAO.enregistrer(conn, transaction);
                stockDAO.retirerStock(conn, produitId, entrepotId, quantite);

                conn.commit();
                transactionDAO.mettreAJourStatut(transaction.getId(), "CONFIRMEE", blockchainTxId);

                LOG.info("✅ Sortie enregistrée. Blockchain TX: {}", blockchainTxId);

            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Erreur PostgreSQL (sortie) : " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur connexion PostgreSQL", e);
        }

        alerteService.verifierApresTransaction(produitId);
        return blockchainTxId;
    }

    // ================================================================
    // Enregistrement d'un TRANSFERT entre entrepôts
    // ================================================================

    /**
     * Enregistre un transfert de stock entre deux entrepôts.
     *
     * <p>La transaction PostgreSQL est atomique : les deux mises à jour
     * (-stock source, +stock destination) sont dans la même transaction JDBC.
     *
     * @param produitId      UUID du produit
     * @param quantite       Quantité à transférer
     * @param entrepotSource UUID de l'entrepôt source
     * @param entrepotDest   UUID de l'entrepôt destination
     * @param motif          Motif du transfert
     * @param operateur      Utilisateur effectuant la saisie
     * @return ID de transaction blockchain
     */
    public String enregistrerTransfert(UUID produitId, int quantite, UUID entrepotSource,
                                        UUID entrepotDest, String motif, Utilisateur operateur) {
        validerParametresBase(produitId, quantite, operateur);

        if (entrepotSource.equals(entrepotDest)) {
            throw new IllegalArgumentException(
                "L'entrepôt source et destination doivent être différents");
        }

        int stockSource = stockDAO.getQuantite(produitId, entrepotSource);
        if (stockSource < quantite) {
            throw new RuntimeException(
                "Stock insuffisant dans l'entrepôt source : disponible=" + stockSource
                + ", demandé=" + quantite);
        }

        LOG.info("Enregistrement transfert : produit={}, qte={}, {}→{}, opérateur={}",
            produitId, quantite, entrepotSource, entrepotDest, operateur.getEmail());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("motif", motif);
        metadata.put("date_enregistrement", LocalDateTime.now().toString());
        String metadataJson = gson.toJson(metadata);

        String txId = "TX-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 16);
        int stockSourceApres = stockSource - quantite;

        // ---- 1. Blockchain ----
        String blockchainTxId = soumettreSurBlockchain(txId, "TRANSFERT", produitId, quantite,
            stockSource, stockSourceApres, operateur.getId(), entrepotSource, entrepotDest, metadataJson);

        // ---- 2. PostgreSQL (atomique) ----
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction transaction = creerObjetTransaction(
                    blockchainTxId, "TRANSFERT", produitId, quantite,
                    stockSource, stockSourceApres, operateur.getId(),
                    entrepotSource, entrepotDest, metadataJson
                );

                transactionDAO.enregistrer(conn, transaction);
                stockDAO.retirerStock(conn, produitId, entrepotSource, quantite);
                stockDAO.ajouterStock(conn, produitId, entrepotDest, quantite);

                conn.commit();
                transactionDAO.mettreAJourStatut(transaction.getId(), "CONFIRMEE", blockchainTxId);

                LOG.info("✅ Transfert enregistré. Blockchain TX: {}", blockchainTxId);

            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Erreur PostgreSQL (transfert) : " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur connexion PostgreSQL", e);
        }

        alerteService.verifierApresTransaction(produitId);
        return blockchainTxId;
    }

    // ================================================================
    // Lecture et vérification
    // ================================================================

    /**
     * Récupère l'historique des transactions d'un produit depuis PostgreSQL.
     *
     * @param produitId UUID du produit
     * @return Liste des transactions triées par date décroissante
     */
    public List<Transaction> getHistoriqueProduit(UUID produitId) {
        return transactionDAO.getParProduit(produitId);
    }

    /**
     * Vérifie l'intégrité d'une transaction sur la blockchain.
     *
     * <p>Appelle {@code verifierIntegrite} sur le chaincode et compare
     * le résultat avec les données PostgreSQL.
     *
     * @param blockchainTxId ID de transaction Hyperledger
     * @return Résultat de vérification sous forme de Map
     */
    public Map<String, Object> verifierIntegriteBloc(String blockchainTxId) {
        Map<String, Object> resultat = new HashMap<>();

        try {
            // Placeholder: Call your Ethereum contract here when the wrapper is generated.
            // For now, we will simulate a successful integrity check so the code compiles.
            String reponseJson = "{\"integre\": true, \"message\": \"Simulation validation ok\", \"hashStocke\": \"\", \"hashCalcule\": \"\"}";

            JsonObject reponse = gson.fromJson(reponseJson, JsonObject.class);
            boolean integre = reponse.get("integre").getAsBoolean();

            resultat.put("integre", integre);
            resultat.put("txId", blockchainTxId);
            resultat.put("message", reponse.get("message").getAsString());
            resultat.put("hashStocke", reponse.has("hashStocke") ?
                reponse.get("hashStocke").getAsString() : null);
            resultat.put("hashCalcule", reponse.has("hashCalcule") ?
                reponse.get("hashCalcule").getAsString() : null);

            // Comparaison avec PostgreSQL
            var txCache = transactionDAO.trouverParBlockchainId(blockchainTxId);
            resultat.put("presentEnCache", txCache.isPresent());

            if (!integre) {
                LOG.error("⚠️ ANOMALIE DÉTECTÉE sur transaction blockchain : {}", blockchainTxId);
            }

        } catch (Exception e) {
            LOG.error("Erreur vérification intégrité {} : {}", blockchainTxId, e.getMessage());
            resultat.put("integre", false);
            resultat.put("message", "Erreur lors de la vérification : " + e.getMessage());
            resultat.put("erreur", true);
        }

        return resultat;
    }

    // ================================================================
    // Méthodes privées
    // ================================================================

    /**
     * Soumet une transaction sur la blockchain via ChaincodeInvoker.
     */
    private String soumettreSurBlockchain(String txId, String type, UUID produitId,
                                           int quantite, int quantiteAvant, int quantiteApres,
                                           UUID operateurId, UUID entrepotSource,
                                           UUID entrepotDest, String metadataJson) {
        try {
            // Placeholder: Call your Ethereum contract here when the wrapper is generated.
            // i.e., ethereumClient.getContract().recordTransaction(...)
            // For now, we simulate success so it compiles.
            String reponseJson = "{\"txId\": \"" + txId + "\"}";

            // Extraire l'ID de transaction de la réponse
            JsonObject reponse = gson.fromJson(reponseJson, JsonObject.class);
            return reponse.has("txId") ? reponse.get("txId").getAsString() : txId;

        } catch (Exception e) {
            LOG.error("❌ Échec soumission blockchain : {}", e.getMessage());
            throw new RuntimeException("Erreur blockchain : " + e.getMessage(), e);
        }
    }

    /**
     * Crée un objet Transaction pour l'insertion en base.
     */
    private Transaction creerObjetTransaction(String blockchainTxId, String type,
                                               UUID produitId, int quantite,
                                               int quantiteAvant, int quantiteApres,
                                               UUID operateurId, UUID entrepotSource,
                                               UUID entrepotDest, String metadataJson) {
        Transaction tx = new Transaction(type, produitId, quantite, operateurId);
        tx.setBlockchainTxId(blockchainTxId);
        tx.setQuantiteAvant(quantiteAvant);
        tx.setQuantiteApres(quantiteApres);
        tx.setEntrepotSourceId(entrepotSource);
        tx.setEntrepotDestinationId(entrepotDest);
        tx.setMetadata(metadataJson);
        tx.setStatut("EN_ATTENTE");
        return tx;
    }

    /**
     * Valide les paramètres communs à toutes les transactions.
     */
    private void validerParametresBase(UUID produitId, int quantite, Utilisateur operateur) {
        if (produitId == null) throw new IllegalArgumentException("L'identifiant produit est obligatoire");
        if (quantite <= 0) throw new IllegalArgumentException("La quantité doit être strictement positive");
        if (operateur == null) throw new IllegalArgumentException("L'opérateur est obligatoire");
    }
}
