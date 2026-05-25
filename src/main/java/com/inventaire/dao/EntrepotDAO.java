package com.inventaire.dao;

import com.inventaire.models.Entrepot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO pour la gestion des entrepôts dans PostgreSQL.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class EntrepotDAO {

    private static final Logger LOG = LoggerFactory.getLogger(EntrepotDAO.class);

    private final DatabaseConnection db;

    private static final String SELECT_BASE = """
        SELECT e.id, e.nom, e.adresse, e.responsable_id,
               CONCAT(u.prenom, ' ', u.nom) AS responsable_nom,
               e.actif, e.metadata,
               COALESCE(SUM(sa.quantite), 0) AS stock_total,
               COUNT(DISTINCT sa.produit_id) AS nombre_references
        FROM entrepots e
        LEFT JOIN utilisateurs u ON e.responsable_id = u.id
        LEFT JOIN stock_actuel sa ON e.id = sa.entrepot_id
        """;

    private static final String GROUP_BY = """
        GROUP BY e.id, e.nom, e.adresse, e.responsable_id,
                 u.prenom, u.nom, e.actif, e.metadata
        """;

    public EntrepotDAO(DatabaseConnection db) {
        this.db = db;
    }

    // ================================================================
    // Lecture
    // ================================================================

    /**
     * Retourne tous les entrepôts actifs.
     *
     * @return Liste des entrepôts actifs triés par nom
     */
    public List<Entrepot> trouverTousActifs() {
        String sql = SELECT_BASE + " WHERE e.actif = true " + GROUP_BY + " ORDER BY e.nom";

        List<Entrepot> entrepots = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                entrepots.add(mapperResultat(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur récupération entrepôts : {}", e.getMessage());
        }

        return entrepots;
    }

    /**
     * Recherche un entrepôt par son UUID.
     *
     * @param id UUID de l'entrepôt
     * @return {@link Optional} contenant l'entrepôt
     */
    public Optional<Entrepot> trouverParId(UUID id) {
        String sql = SELECT_BASE + " WHERE e.id = ? " + GROUP_BY;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche entrepôt {} : {}", id, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Retourne tous les entrepôts (actifs et inactifs).
     *
     * @return Liste complète
     */
    public List<Entrepot> trouverTous() {
        String sql = SELECT_BASE + GROUP_BY + " ORDER BY e.nom";
        List<Entrepot> entrepots = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                entrepots.add(mapperResultat(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur récupération tous entrepôts : {}", e.getMessage());
        }

        return entrepots;
    }

    // ================================================================
    // Écriture
    // ================================================================

    /**
     * Crée un nouvel entrepôt.
     *
     * @param entrepot L'entrepôt à créer
     * @return {@code true} si la création a réussi
     */
    public boolean creer(Entrepot entrepot) {
        String sql = """
            INSERT INTO entrepots (nom, adresse, responsable_id, actif, metadata)
            VALUES (?, ?, ?, ?, ?::jsonb)
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, entrepot.getNom());
            ps.setString(2, entrepot.getAdresse());
            ps.setObject(3, entrepot.getResponsableId());
            ps.setBoolean(4, entrepot.isActif());
            ps.setString(5, entrepot.getMetadata() != null ? entrepot.getMetadata() : "{}");

            int lignes = ps.executeUpdate();
            if (lignes > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        entrepot.setId((UUID) keys.getObject(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            LOG.error("Erreur création entrepôt {} : {}", entrepot.getNom(), e.getMessage());
        }

        return false;
    }

    /**
     * Met à jour un entrepôt existant.
     *
     * @param entrepot L'entrepôt avec les données mises à jour
     * @return {@code true} si la mise à jour a réussi
     */
    public boolean mettreAJour(Entrepot entrepot) {
        String sql = """
            UPDATE entrepots
            SET nom = ?, adresse = ?, responsable_id = ?, actif = ?
            WHERE id = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, entrepot.getNom());
            ps.setString(2, entrepot.getAdresse());
            ps.setObject(3, entrepot.getResponsableId());
            ps.setBoolean(4, entrepot.isActif());
            ps.setObject(5, entrepot.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Erreur mise à jour entrepôt {} : {}", entrepot.getId(), e.getMessage());
            return false;
        }
    }

    // ================================================================
    // Mapping
    // ================================================================

    private Entrepot mapperResultat(ResultSet rs) throws SQLException {
        Entrepot e = new Entrepot();

        e.setId((UUID) rs.getObject("id"));
        e.setNom(rs.getString("nom"));
        e.setAdresse(rs.getString("adresse"));
        e.setResponsableId((UUID) rs.getObject("responsable_id"));
        e.setResponsableNom(rs.getString("responsable_nom"));
        e.setActif(rs.getBoolean("actif"));
        e.setMetadata(rs.getString("metadata"));
        e.setStockTotal(rs.getInt("stock_total"));
        e.setNombreReferences(rs.getInt("nombre_references"));

        return e;
    }
}
