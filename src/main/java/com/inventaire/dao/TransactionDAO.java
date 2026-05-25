package com.inventaire.dao;

import com.inventaire.models.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO pour la gestion des transactions dans la table {@code transactions_cache}.
 *
 * <p>Cette table est le miroir PostgreSQL du ledger Hyperledger Fabric.
 * Elle permet des requêtes rapides sans interroger la blockchain à chaque fois.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class TransactionDAO {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionDAO.class);

    private final DatabaseConnection db;

    private static final String SELECT_BASE = """
        SELECT tc.id, tc.blockchain_tx_id, tc.bloc_numero, tc.bloc_hash,
               tc.type, tc.statut, tc.produit_id,
               p.nom AS produit_nom, p.reference AS produit_reference,
               tc.quantite, tc.quantite_avant, tc.quantite_apres,
               tc.operateur_id,
               CONCAT(u.prenom, ' ', u.nom) AS operateur_nom,
               tc.entrepot_source_id, es.nom AS entrepot_source_nom,
               tc.entrepot_destination_id, ed.nom AS entrepot_destination_nom,
               tc.metadata, tc.commentaire, tc.enregistre_le, tc.confirme_le
        FROM transactions_cache tc
        JOIN produits p ON tc.produit_id = p.id
        JOIN utilisateurs u ON tc.operateur_id = u.id
        LEFT JOIN entrepots es ON tc.entrepot_source_id = es.id
        LEFT JOIN entrepots ed ON tc.entrepot_destination_id = ed.id
        """;

    public TransactionDAO(DatabaseConnection db) {
        this.db = db;
    }

    // ================================================================
    // Lecture
    // ================================================================

    /**
     * Retourne les N dernières transactions.
     *
     * @param limite Nombre maximum de transactions à retourner
     * @return Liste des transactions les plus récentes
     */
    public List<Transaction> getDernieres(int limite) {
        String sql = SELECT_BASE + " ORDER BY tc.enregistre_le DESC LIMIT ?";

        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limite);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur récupération dernières transactions : {}", e.getMessage());
        }

        return transactions;
    }

    /**
     * Retourne toutes les transactions d'un produit spécifique.
     *
     * @param produitId UUID du produit
     * @return Liste des transactions triées par date décroissante
     */
    public List<Transaction> getParProduit(UUID produitId) {
        String sql = SELECT_BASE + " WHERE tc.produit_id = ? ORDER BY tc.enregistre_le DESC";

        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, produitId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur récupération transactions produit {} : {}", produitId, e.getMessage());
        }

        return transactions;
    }

    /**
     * Recherche avec filtres combinés pour l'écran Historique.
     *
     * @param dateDebut   Date de début (inclusif) — peut être null
     * @param dateFin     Date de fin (inclusif) — peut être null
     * @param produitId   UUID du produit — peut être null
     * @param type        Type de transaction — peut être null
     * @param operateurId UUID de l'opérateur — peut être null
     * @param statut      Statut de la transaction — peut être null
     * @return Liste des transactions correspondantes
     */
    public List<Transaction> rechercherAvecFiltres(
            LocalDate dateDebut, LocalDate dateFin, UUID produitId,
            String type, UUID operateurId, String statut) {

        StringBuilder sql = new StringBuilder(SELECT_BASE + " WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (dateDebut != null) {
            sql.append(" AND tc.enregistre_le >= ? ");
            params.add(Timestamp.valueOf(dateDebut.atStartOfDay()));
        }
        if (dateFin != null) {
            sql.append(" AND tc.enregistre_le < ? ");
            params.add(Timestamp.valueOf(dateFin.plusDays(1).atStartOfDay()));
        }
        if (produitId != null) {
            sql.append(" AND tc.produit_id = ? ");
            params.add(produitId);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND tc.type = ?::type_transaction ");
            params.add(type);
        }
        if (operateurId != null) {
            sql.append(" AND tc.operateur_id = ? ");
            params.add(operateurId);
        }
        if (statut != null && !statut.isBlank()) {
            sql.append(" AND tc.statut = ?::statut_transaction ");
            params.add(statut);
        }

        sql.append(" ORDER BY tc.enregistre_le DESC");

        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche transactions avec filtres : {}", e.getMessage());
        }

        return transactions;
    }

    /**
     * Compte le nombre de transactions enregistrées aujourd'hui.
     *
     * @return Nombre de transactions du jour
     */
    public int compterAujourdhui() {
        String sql = """
            SELECT COUNT(*) FROM transactions_cache
            WHERE DATE(enregistre_le) = CURRENT_DATE
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOG.error("Erreur comptage transactions du jour : {}", e.getMessage());
        }

        return 0;
    }

    /**
     * Recherche une transaction par son ID blockchain.
     *
     * @param blockchainTxId ID de transaction Hyperledger Fabric
     * @return {@link Optional} contenant la transaction
     */
    public Optional<Transaction> trouverParBlockchainId(String blockchainTxId) {
        String sql = SELECT_BASE + " WHERE tc.blockchain_tx_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, blockchainTxId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche par blockchain ID {} : {}", blockchainTxId, e.getMessage());
        }

        return Optional.empty();
    }

    // ================================================================
    // Écriture
    // ================================================================

    /**
     * Enregistre une nouvelle transaction dans le cache PostgreSQL.
     * Doit être appelé dans une transaction JDBC (autoCommit = false).
     *
     * @param conn        Connexion JDBC avec transaction ouverte
     * @param transaction La transaction à enregistrer
     * @return L'UUID généré pour la transaction
     * @throws SQLException en cas d'erreur SQL
     */
    public UUID enregistrer(Connection conn, Transaction transaction) throws SQLException {
        String sql = """
            INSERT INTO transactions_cache
                (blockchain_tx_id, type, statut, produit_id, quantite,
                 quantite_avant, quantite_apres, operateur_id,
                 entrepot_source_id, entrepot_destination_id, metadata, commentaire)
            VALUES (?::varchar, ?::type_transaction, ?::statut_transaction,
                    ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            RETURNING id
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transaction.getBlockchainTxId());
            ps.setString(2, transaction.getType());
            ps.setString(3, transaction.getStatut());
            ps.setObject(4, transaction.getProduitId());
            ps.setInt(5, transaction.getQuantite());
            ps.setObject(6, transaction.getQuantiteAvant());
            ps.setObject(7, transaction.getQuantiteApres());
            ps.setObject(8, transaction.getOperateurId());
            ps.setObject(9, transaction.getEntrepotSourceId());
            ps.setObject(10, transaction.getEntrepotDestinationId());
            ps.setString(11, transaction.getMetadata() != null ? transaction.getMetadata() : "{}");
            ps.setString(12, transaction.getCommentaire());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID id = (UUID) rs.getObject("id");
                    transaction.setId(id);
                    LOG.debug("Transaction enregistrée dans cache PostgreSQL : {}", id);
                    return id;
                }
            }
        }

        throw new SQLException("Impossible d'enregistrer la transaction dans le cache");
    }

    /**
     * Met à jour le statut d'une transaction après confirmation ou échec blockchain.
     *
     * @param transactionId  UUID interne de la transaction
     * @param nouveauStatut  "CONFIRMEE" ou "ECHOUEE"
     * @param blockchainTxId ID blockchain reçu après confirmation (peut être null)
     */
    public void mettreAJourStatut(UUID transactionId, String nouveauStatut,
                                   String blockchainTxId) {
        String sql = """
            UPDATE transactions_cache
            SET statut = ?::statut_transaction,
                blockchain_tx_id = COALESCE(?, blockchain_tx_id),
                confirme_le = CASE WHEN ? = 'CONFIRMEE' THEN NOW() ELSE confirme_le END
            WHERE id = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nouveauStatut);
            ps.setString(2, blockchainTxId);
            ps.setString(3, nouveauStatut);
            ps.setObject(4, transactionId);

            ps.executeUpdate();
            LOG.debug("Statut transaction {} → {}", transactionId, nouveauStatut);
        } catch (SQLException e) {
            LOG.error("Erreur mise à jour statut transaction {} : {}", transactionId, e.getMessage());
        }
    }

    // ================================================================
    // Mapping
    // ================================================================

    private Transaction mapperResultat(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();

        t.setId((UUID) rs.getObject("id"));
        t.setBlockchainTxId(rs.getString("blockchain_tx_id"));
        t.setBlocNumero(rs.getObject("bloc_numero") != null ? rs.getLong("bloc_numero") : null);
        t.setBlocHash(rs.getString("bloc_hash"));
        t.setType(rs.getString("type"));
        t.setStatut(rs.getString("statut"));
        t.setProduitId((UUID) rs.getObject("produit_id"));
        t.setProduitNom(rs.getString("produit_nom"));
        t.setProduitReference(rs.getString("produit_reference"));
        t.setQuantite(rs.getInt("quantite"));
        t.setQuantiteAvant(rs.getObject("quantite_avant") != null ? rs.getInt("quantite_avant") : null);
        t.setQuantiteApres(rs.getObject("quantite_apres") != null ? rs.getInt("quantite_apres") : null);
        t.setOperateurId((UUID) rs.getObject("operateur_id"));
        t.setOperateurNom(rs.getString("operateur_nom"));
        t.setEntrepotSourceId((UUID) rs.getObject("entrepot_source_id"));
        t.setEntrepotSourceNom(rs.getString("entrepot_source_nom"));
        t.setEntrepotDestinationId((UUID) rs.getObject("entrepot_destination_id"));
        t.setEntrepotDestinationNom(rs.getString("entrepot_destination_nom"));
        t.setMetadata(rs.getString("metadata"));
        t.setCommentaire(rs.getString("commentaire"));

        Timestamp enregistreLe = rs.getTimestamp("enregistre_le");
        if (enregistreLe != null) t.setEnregistreLe(enregistreLe.toLocalDateTime());

        Timestamp confirmeLe = rs.getTimestamp("confirme_le");
        if (confirmeLe != null) t.setConfirmeLe(confirmeLe.toLocalDateTime());

        return t;
    }
}
