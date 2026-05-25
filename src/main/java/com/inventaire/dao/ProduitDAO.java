package com.inventaire.dao;

import com.inventaire.models.Produit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO pour la gestion des produits dans PostgreSQL.
 *
 * <p>Toutes les requêtes SQL utilisent des {@link PreparedStatement}.
 * Les jointures avec {@code categories} et {@code stock_actuel} sont
 * effectuées directement pour enrichir les objets {@link Produit}.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class ProduitDAO {

    private static final Logger LOG = LoggerFactory.getLogger(ProduitDAO.class);

    private final DatabaseConnection db;

    /**
     * Requête SQL de base pour la récupération d'un produit avec jointures.
     * Le stock total est la somme sur tous les entrepôts.
     */
    private static final String SELECT_BASE = """
        SELECT p.id, p.reference, p.nom, p.description,
               p.categorie_id, c.nom AS categorie_nom,
               p.unite_mesure, p.seuil_critique, p.seuil_reapprovisionnement,
               p.prix_unitaire, p.image_path, p.actif, p.metadata,
               p.cree_le, p.modifie_le,
               COALESCE(SUM(sa.quantite), 0) AS stock_total
        FROM produits p
        LEFT JOIN categories c ON p.categorie_id = c.id
        LEFT JOIN stock_actuel sa ON p.id = sa.produit_id
        """;

    private static final String GROUP_BY = """
        GROUP BY p.id, p.reference, p.nom, p.description,
                 p.categorie_id, c.nom, p.unite_mesure,
                 p.seuil_critique, p.seuil_reapprovisionnement,
                 p.prix_unitaire, p.image_path, p.actif, p.metadata,
                 p.cree_le, p.modifie_le
        """;

    public ProduitDAO(DatabaseConnection db) {
        this.db = db;
    }

    // ================================================================
    // Lecture
    // ================================================================

    /**
     * Recherche un produit par son UUID.
     *
     * @param id UUID du produit
     * @return {@link Optional} contenant le produit, ou vide si non trouvé
     */
    public Optional<Produit> trouverParId(UUID id) {
        String sql = SELECT_BASE + " WHERE p.id = ? " + GROUP_BY;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche produit par ID {} : {}", id, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Recherche un produit par sa référence unique.
     *
     * @param reference Référence du produit (ex: PROD-001)
     * @return {@link Optional} contenant le produit
     */
    public Optional<Produit> trouverParReference(String reference) {
        String sql = SELECT_BASE + " WHERE p.reference = ? " + GROUP_BY;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, reference);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche produit par référence {} : {}", reference, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Retourne tous les produits actifs avec leur stock total.
     *
     * @return Liste triée par nom
     */
    public List<Produit> trouverTousActifs() {
        String sql = SELECT_BASE + " WHERE p.actif = true " + GROUP_BY + " ORDER BY p.nom";

        return executerRequeteListe(sql);
    }

    /**
     * Retourne tous les produits (actifs et inactifs).
     *
     * @return Liste complète triée par nom
     */
    public List<Produit> trouverTous() {
        String sql = SELECT_BASE + GROUP_BY + " ORDER BY p.nom";

        return executerRequeteListe(sql);
    }

    /**
     * Recherche des produits avec filtres combinés.
     *
     * @param recherche   Texte libre (référence ou nom) — peut être null
     * @param categorieId UUID de la catégorie — peut être null
     * @param entrepotId  UUID de l'entrepôt — peut être null
     * @param page        Numéro de page (base 0)
     * @param taillePage  Nombre de résultats par page
     * @return Liste des produits correspondants
     */
    public List<Produit> rechercherAvecFiltres(String recherche, UUID categorieId,
                                                UUID entrepotId, int page, int taillePage) {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();

        sql.append(" WHERE p.actif = true ");

        if (recherche != null && !recherche.isBlank()) {
            sql.append(" AND (LOWER(p.nom) LIKE ? OR LOWER(p.reference) LIKE ?) ");
            String pattern = "%" + recherche.toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
        }

        if (categorieId != null) {
            sql.append(" AND p.categorie_id = ? ");
            params.add(categorieId);
        }

        if (entrepotId != null) {
            sql.append(" AND sa.entrepot_id = ? ");
            params.add(entrepotId);
        }

        sql.append(GROUP_BY);
        sql.append(" ORDER BY p.nom ");
        sql.append(" LIMIT ? OFFSET ? ");
        params.add(taillePage);
        params.add(page * taillePage);

        List<Produit> resultats = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultats.add(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche produits avec filtres : {}", e.getMessage());
        }

        return resultats;
    }

    /**
     * Retourne les produits dont le stock est en dessous du seuil critique.
     *
     * @return Liste des produits en alerte critique
     */
    public List<Produit> trouverProduitsEnAlerteCritique() {
        String sql = SELECT_BASE + GROUP_BY
            + " HAVING COALESCE(SUM(sa.quantite), 0) < p.seuil_critique"
            + " AND p.actif = true ORDER BY p.nom";

        return executerRequeteListe(sql);
    }

    /**
     * Retourne les produits dont le stock est en dessous du seuil de réapprovisionnement.
     *
     * @return Liste des produits à réapprovisionner
     */
    public List<Produit> trouverProduitsAReapprovisionner() {
        String sql = SELECT_BASE + GROUP_BY
            + " HAVING COALESCE(SUM(sa.quantite), 0) < p.seuil_reapprovisionnement"
            + " AND p.actif = true ORDER BY p.nom";

        return executerRequeteListe(sql);
    }

    /**
     * Compte le nombre total de produits actifs.
     *
     * @return Nombre de produits actifs
     */
    public int compterProduitsActifs() {
        String sql = "SELECT COUNT(*) FROM produits WHERE actif = true";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOG.error("Erreur comptage produits : {}", e.getMessage());
        }

        return 0;
    }

    // ================================================================
    // Écriture
    // ================================================================

    /**
     * Crée un nouveau produit dans la base de données.
     *
     * @param produit Le produit à créer
     * @return {@code true} si la création a réussi
     */
    public boolean creer(Produit produit) {
        String sql = """
            INSERT INTO produits
                (reference, nom, description, categorie_id, unite_mesure,
                 seuil_critique, seuil_reapprovisionnement, prix_unitaire,
                 image_path, actif, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, produit.getReference());
            ps.setString(2, produit.getNom());
            ps.setString(3, produit.getDescription());
            ps.setObject(4, produit.getCategorieId());
            ps.setString(5, produit.getUniteMesure());
            ps.setInt(6, produit.getSeuilCritique());
            ps.setInt(7, produit.getSeuilReapprovisionnement());
            ps.setBigDecimal(8, produit.getPrixUnitaire());
            ps.setString(9, produit.getImagePath());
            ps.setBoolean(10, produit.isActif());
            ps.setString(11, produit.getMetadata() != null ? produit.getMetadata() : "{}");

            int lignes = ps.executeUpdate();
            if (lignes > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        produit.setId((UUID) keys.getObject(1));
                    }
                }
                LOG.info("Produit créé : {}", produit.getReference());
                return true;
            }
        } catch (SQLException e) {
            LOG.error("Erreur création produit {} : {}", produit.getReference(), e.getMessage());
        }

        return false;
    }

    /**
     * Met à jour un produit existant.
     *
     * @param produit Le produit avec les données mises à jour
     * @return {@code true} si la mise à jour a réussi
     */
    public boolean mettreAJour(Produit produit) {
        String sql = """
            UPDATE produits
            SET reference = ?, nom = ?, description = ?, categorie_id = ?,
                unite_mesure = ?, seuil_critique = ?, seuil_reapprovisionnement = ?,
                prix_unitaire = ?, image_path = ?, actif = ?, modifie_le = NOW()
            WHERE id = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, produit.getReference());
            ps.setString(2, produit.getNom());
            ps.setString(3, produit.getDescription());
            ps.setObject(4, produit.getCategorieId());
            ps.setString(5, produit.getUniteMesure());
            ps.setInt(6, produit.getSeuilCritique());
            ps.setInt(7, produit.getSeuilReapprovisionnement());
            ps.setBigDecimal(8, produit.getPrixUnitaire());
            ps.setString(9, produit.getImagePath());
            ps.setBoolean(10, produit.isActif());
            ps.setObject(11, produit.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Erreur mise à jour produit {} : {}", produit.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Suppression logique d'un produit (actif = false).
     *
     * @param id UUID du produit
     * @return {@code true} si la désactivation a réussi
     */
    public boolean desactiver(UUID id) {
        String sql = "UPDATE produits SET actif = false, modifie_le = NOW() WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Erreur désactivation produit {} : {}", id, e.getMessage());
            return false;
        }
    }

    // ================================================================
    // Méthodes utilitaires privées
    // ================================================================

    /**
     * Exécute une requête SQL et retourne une liste de produits.
     */
    private List<Produit> executerRequeteListe(String sql) {
        List<Produit> produits = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                produits.add(mapperResultat(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur exécution requête produits : {}", e.getMessage());
        }

        return produits;
    }

    /**
     * Mappe un {@link ResultSet} vers un objet {@link Produit}.
     */
    private Produit mapperResultat(ResultSet rs) throws SQLException {
        Produit p = new Produit();

        p.setId((UUID) rs.getObject("id"));
        p.setReference(rs.getString("reference"));
        p.setNom(rs.getString("nom"));
        p.setDescription(rs.getString("description"));
        p.setCategorieId((UUID) rs.getObject("categorie_id"));
        p.setCategorieNom(rs.getString("categorie_nom"));
        p.setUniteMesure(rs.getString("unite_mesure"));
        p.setSeuilCritique(rs.getInt("seuil_critique"));
        p.setSeuilReapprovisionnement(rs.getInt("seuil_reapprovisionnement"));

        BigDecimal prix = rs.getBigDecimal("prix_unitaire");
        p.setPrixUnitaire(prix);

        p.setImagePath(rs.getString("image_path"));
        p.setActif(rs.getBoolean("actif"));
        p.setMetadata(rs.getString("metadata"));
        p.setStockTotal(rs.getInt("stock_total"));

        Timestamp creeLe = rs.getTimestamp("cree_le");
        if (creeLe != null) p.setCreeLe(creeLe.toLocalDateTime());

        Timestamp modifieLe = rs.getTimestamp("modifie_le");
        if (modifieLe != null) p.setModifieLe(modifieLe.toLocalDateTime());

        return p;
    }
}
