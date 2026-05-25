package com.inventaire.dao;

import com.inventaire.models.StockActuel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO pour la gestion du stock actuel dans PostgreSQL.
 *
 * <p>La table {@code stock_actuel} est une clé composée (produit_id, entrepot_id).
 * Les opérations UPSERT sont utilisées pour maintenir la cohérence.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class StockDAO {

    private static final Logger LOG = LoggerFactory.getLogger(StockDAO.class);

    private final DatabaseConnection db;

    private static final String SELECT_BASE = """
        SELECT sa.produit_id, p.reference AS produit_reference, p.nom AS produit_nom,
               p.seuil_critique, p.seuil_reapprovisionnement,
               sa.entrepot_id, e.nom AS entrepot_nom,
               sa.quantite, sa.derniere_maj
        FROM stock_actuel sa
        JOIN produits p ON sa.produit_id = p.id
        JOIN entrepots e ON sa.entrepot_id = e.id
        """;

    public StockDAO(DatabaseConnection db) {
        this.db = db;
    }

    // ================================================================
    // Lecture
    // ================================================================

    /**
     * Retourne le stock d'un produit dans un entrepôt spécifique.
     *
     * @param produitId  UUID du produit
     * @param entrepotId UUID de l'entrepôt
     * @return Stock actuel, ou 0 si non trouvé
     */
    public int getQuantite(UUID produitId, UUID entrepotId) {
        String sql = "SELECT quantite FROM stock_actuel WHERE produit_id = ? AND entrepot_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, produitId);
            ps.setObject(2, entrepotId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("quantite");
            }
        } catch (SQLException e) {
            LOG.error("Erreur lecture stock produit {} entrepôt {} : {}", produitId, entrepotId, e.getMessage());
        }

        return 0;
    }

    /**
     * Retourne le stock complet d'un produit dans tous les entrepôts.
     *
     * @param produitId UUID du produit
     * @return Liste des stocks par entrepôt
     */
    public List<StockActuel> getStockParProduit(UUID produitId) {
        String sql = SELECT_BASE + " WHERE sa.produit_id = ? ORDER BY e.nom";

        List<StockActuel> stocks = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, produitId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stocks.add(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur lecture stock produit {} : {}", produitId, e.getMessage());
        }

        return stocks;
    }

    /**
     * Retourne tout le stock d'un entrepôt spécifique.
     *
     * @param entrepotId UUID de l'entrepôt
     * @return Liste des stocks par produit dans cet entrepôt
     */
    public List<StockActuel> getStockParEntrepot(UUID entrepotId) {
        String sql = SELECT_BASE + " WHERE sa.entrepot_id = ? ORDER BY p.nom";

        List<StockActuel> stocks = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, entrepotId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stocks.add(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur lecture stock entrepôt {} : {}", entrepotId, e.getMessage());
        }

        return stocks;
    }

    /**
     * Retourne tout le stock de tous les entrepôts.
     *
     * @return Liste complète du stock
     */
    public List<StockActuel> getToutLeStock() {
        String sql = SELECT_BASE + " ORDER BY p.nom, e.nom";
        List<StockActuel> stocks = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                stocks.add(mapperResultat(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur lecture stock global : {}", e.getMessage());
        }

        return stocks;
    }

    /**
     * Calcule la valeur totale du stock (toutes références, tous entrepôts).
     *
     * @return Valeur totale en devise
     */
    public double getValeurTotaleStock() {
        String sql = """
            SELECT COALESCE(SUM(sa.quantite * p.prix_unitaire), 0) AS valeur_totale
            FROM stock_actuel sa
            JOIN produits p ON sa.produit_id = p.id
            WHERE p.prix_unitaire IS NOT NULL
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) return rs.getDouble("valeur_totale");
        } catch (SQLException e) {
            LOG.error("Erreur calcul valeur totale stock : {}", e.getMessage());
        }

        return 0.0;
    }

    // ================================================================
    // Écriture (UPSERT atomique)
    // ================================================================

    /**
     * Ajoute une quantité au stock d'un produit dans un entrepôt.
     * Utilise UPSERT PostgreSQL pour gérer le cas où le stock n'existe pas encore.
     *
     * @param conn       Connexion JDBC (pour transaction externe)
     * @param produitId  UUID du produit
     * @param entrepotId UUID de l'entrepôt
     * @param quantite   Quantité à ajouter (doit être positive)
     * @return Nouvelle quantité en stock
     * @throws SQLException en cas d'erreur SQL
     */
    public int ajouterStock(Connection conn, UUID produitId, UUID entrepotId,
                            int quantite) throws SQLException {
        String sql = """
            INSERT INTO stock_actuel (produit_id, entrepot_id, quantite, derniere_maj)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (produit_id, entrepot_id)
            DO UPDATE SET
                quantite = stock_actuel.quantite + EXCLUDED.quantite,
                derniere_maj = NOW()
            RETURNING quantite
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, produitId);
            ps.setObject(2, entrepotId);
            ps.setInt(3, quantite);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int nouvelleQuantite = rs.getInt("quantite");
                    LOG.debug("Stock ajouté : produit={}, entrepôt={}, +{} → {}",
                        produitId, entrepotId, quantite, nouvelleQuantite);
                    return nouvelleQuantite;
                }
            }
        }

        throw new SQLException("Impossible d'ajouter le stock pour produit=" + produitId);
    }

    /**
     * Retire une quantité du stock d'un produit dans un entrepôt.
     * Vérifie que le stock ne deviendra pas négatif (contrainte CHECK en DB).
     *
     * @param conn       Connexion JDBC (pour transaction externe)
     * @param produitId  UUID du produit
     * @param entrepotId UUID de l'entrepôt
     * @param quantite   Quantité à retirer (doit être positive)
     * @return Nouvelle quantité en stock
     * @throws SQLException si le stock est insuffisant ou erreur SQL
     */
    public int retirerStock(Connection conn, UUID produitId, UUID entrepotId,
                            int quantite) throws SQLException {
        String sql = """
            UPDATE stock_actuel
            SET quantite = quantite - ?, derniere_maj = NOW()
            WHERE produit_id = ? AND entrepot_id = ? AND quantite >= ?
            RETURNING quantite
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantite);
            ps.setObject(2, produitId);
            ps.setObject(3, entrepotId);
            ps.setInt(4, quantite);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int nouvelleQuantite = rs.getInt("quantite");
                    LOG.debug("Stock retiré : produit={}, entrepôt={}, -{} → {}",
                        produitId, entrepotId, quantite, nouvelleQuantite);
                    return nouvelleQuantite;
                }
            }
        }

        throw new SQLException("Stock insuffisant pour produit=" + produitId
            + " entrepôt=" + entrepotId + " quantité demandée=" + quantite);
    }

    // ================================================================
    // Mapping
    // ================================================================

    private StockActuel mapperResultat(ResultSet rs) throws SQLException {
        StockActuel s = new StockActuel();

        s.setProduitId((UUID) rs.getObject("produit_id"));
        s.setProduitReference(rs.getString("produit_reference"));
        s.setProduitNom(rs.getString("produit_nom"));
        s.setSeuilCritique(rs.getInt("seuil_critique"));
        s.setSeuilReapprovisionnement(rs.getInt("seuil_reapprovisionnement"));
        s.setEntrepotId((UUID) rs.getObject("entrepot_id"));
        s.setEntrepotNom(rs.getString("entrepot_nom"));
        s.setQuantite(rs.getInt("quantite"));

        Timestamp derniereMaj = rs.getTimestamp("derniere_maj");
        if (derniereMaj != null) s.setDerniereMaj(derniereMaj.toLocalDateTime());

        return s;
    }
}
