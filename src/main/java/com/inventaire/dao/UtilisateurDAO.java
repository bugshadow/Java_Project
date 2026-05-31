package com.inventaire.dao;

import com.inventaire.models.Utilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO pour la gestion des utilisateurs dans PostgreSQL.
 *
 * <p>Toutes les requêtes utilisent des {@link PreparedStatement} pour prévenir
 * les injections SQL. Aucune concaténation de chaînes SQL n'est utilisée.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class UtilisateurDAO {

    private static final Logger LOG = LoggerFactory.getLogger(UtilisateurDAO.class);

    /** Connexion à la base de données. */
    private final DatabaseConnection db;

    /**
     * Constructeur.
     *
     * @param db Instance du gestionnaire de connexions
     */
    public UtilisateurDAO(DatabaseConnection db) {
        this.db = db;
    }

    // ================================================================
    // Lecture
    // ================================================================

    /**
     * Recherche un utilisateur par son email.
     *
     * @param email Adresse email à rechercher
     * @return {@link Optional} contenant l'utilisateur, ou vide si non trouvé
     */
    public Optional<Utilisateur> trouverParEmail(String email) {
        String sql = """
            SELECT id, nom, prenom, email, password_hash, role, actif,
                   premier_login, tentatives_echec, verrouille_jusqu_au,
                   derniere_connexion, cle_publique, cree_le, modifie_le
            FROM utilisateurs
            WHERE email = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche utilisateur par email : {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Recherche un utilisateur par son UUID.
     *
     * @param id UUID de l'utilisateur
     * @return {@link Optional} contenant l'utilisateur, ou vide si non trouvé
     */
    public Optional<Utilisateur> trouverParId(UUID id) {
        String sql = """
            SELECT id, nom, prenom, email, password_hash, role, actif,
                   premier_login, tentatives_echec, verrouille_jusqu_au,
                   derniere_connexion, cle_publique, cree_le, modifie_le
            FROM utilisateurs
            WHERE id = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapperResultat(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Erreur recherche utilisateur par ID : {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Retourne tous les utilisateurs du système.
     *
     * @return Liste de tous les utilisateurs triés par nom
     */
    public List<Utilisateur> trouverTous() {
        String sql = """
            SELECT id, nom, prenom, email, password_hash, role, actif,
                   premier_login, tentatives_echec, verrouille_jusqu_au,
                   derniere_connexion, cle_publique, cree_le, modifie_le
            FROM utilisateurs
            ORDER BY nom, prenom
            """;

        List<Utilisateur> utilisateurs = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                utilisateurs.add(mapperResultat(rs));
            }
        } catch (SQLException e) {
            LOG.error("Erreur récupération liste utilisateurs : {}", e.getMessage());
        }

        return utilisateurs;
    }

    // ================================================================
    // Écriture
    // ================================================================

    /**
     * Crée un nouvel utilisateur dans la base de données.
     *
     * @param utilisateur L'utilisateur à créer (id sera généré par PostgreSQL)
     * @return {@code true} si la création a réussi
     */
    public boolean creer(Utilisateur utilisateur) {
        String sql = """
            INSERT INTO utilisateurs
                (nom, prenom, email, password_hash, role, actif, premier_login)
            VALUES (?, ?, ?, ?, ?::role_utilisateur, ?, ?)
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, utilisateur.getNom());
            ps.setString(2, utilisateur.getPrenom());
            ps.setString(3, utilisateur.getEmail());
            ps.setString(4, utilisateur.getPasswordHash());
            ps.setString(5, utilisateur.getRole());
            ps.setBoolean(6, utilisateur.isActif());
            ps.setBoolean(7, utilisateur.isPremierLogin());

            int lignes = ps.executeUpdate();
            if (lignes > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        utilisateur.setId((UUID) keys.getObject(1));
                    }
                }
                LOG.info("Utilisateur créé : {}", utilisateur.getEmail());
                return true;
            }
        } catch (SQLException e) {
            LOG.error("Erreur création utilisateur {} : {}", utilisateur.getEmail(), e.getMessage());
        }

        return false;
    }

    /**
     * Met à jour un utilisateur existant.
     *
     * @param utilisateur L'utilisateur avec les données mises à jour
     * @return {@code true} si la mise à jour a réussi
     */
    public boolean mettreAJour(Utilisateur utilisateur) {
        String sql = """
            UPDATE utilisateurs
            SET nom = ?, prenom = ?, email = ?, role = ?::role_utilisateur,
                actif = ?, modifie_le = NOW()
            WHERE id = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, utilisateur.getNom());
            ps.setString(2, utilisateur.getPrenom());
            ps.setString(3, utilisateur.getEmail());
            ps.setString(4, utilisateur.getRole());
            ps.setBoolean(5, utilisateur.isActif());
            ps.setObject(6, utilisateur.getId());

            int lignes = ps.executeUpdate();
            return lignes > 0;
        } catch (SQLException e) {
            LOG.error("Erreur mise à jour utilisateur {} : {}", utilisateur.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Met à jour le hash du mot de passe d'un utilisateur.
     *
     * @param utilisateurId UUID de l'utilisateur
     * @param nouveauHash   Nouveau hash BCrypt
     * @return {@code true} si la mise à jour a réussi
     */
    public boolean mettreAJourMotDePasse(UUID utilisateurId, String nouveauHash) {
        String sql = """
            UPDATE utilisateurs
            SET password_hash = ?, premier_login = false, modifie_le = NOW()
            WHERE id = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nouveauHash);
            ps.setObject(2, utilisateurId);

            int lignes = ps.executeUpdate();
            if (lignes > 0) {
                LOG.info("Mot de passe mis à jour pour utilisateur : {}", utilisateurId);
                return true;
            }
        } catch (SQLException e) {
            LOG.error("Erreur mise à jour mot de passe : {}", e.getMessage());
        }

        return false;
    }

    /**
     * Enregistre une connexion réussie.
     *
     * @param utilisateurId UUID de l'utilisateur
     */
    public void enregistrerConnexionReussie(UUID utilisateurId) {
        String sql = """
            UPDATE utilisateurs
            SET derniere_connexion = NOW(),
                tentatives_echec = 0,
                verrouille_jusqu_au = NULL
            WHERE id = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, utilisateurId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Erreur mise à jour connexion réussie : {}", e.getMessage());
        }
    }

    /**
     * Incrémente le compteur de tentatives échouées.
     *
     * <p>Le verrouillage automatique est désactivé dans ce projet de test.
     *
     * @param email Email de l'utilisateur
     */
    public void incrementerTentativesEchec(String email) {
        String sql = """
            UPDATE utilisateurs
            SET tentatives_echec = tentatives_echec + 1,
                modifie_le = NOW()
            WHERE email = ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Erreur incrémentation tentatives échec pour {} : {}", email, e.getMessage());
        }
    }

    /**
     * Enregistre une tentative de connexion dans logs_connexion.
     *
     * @param utilisateurId UUID de l'utilisateur (null si email inconnu)
     * @param emailTente    Email utilisé lors de la tentative
     * @param succes        {@code true} si la connexion a réussi
     * @param message       Message décrivant le résultat
     */
    public void enregistrerLogConnexion(UUID utilisateurId, String emailTente,
                                        boolean succes, String message) {
        String sql = """
            INSERT INTO logs_connexion (utilisateur_id, email_tente, succes, message)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, utilisateurId);
            ps.setString(2, emailTente);
            ps.setBoolean(3, succes);
            ps.setString(4, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Erreur insertion log connexion : {}", e.getMessage());
        }
    }

    /**
     * Active ou désactive un compte utilisateur.
     *
     * @param utilisateurId UUID de l'utilisateur
     * @param actif         {@code true} pour activer, {@code false} pour désactiver
     * @return {@code true} si la mise à jour a réussi
     */
    public boolean activerDesactiver(UUID utilisateurId, boolean actif) {
        String sql = "UPDATE utilisateurs SET actif = ?, modifie_le = NOW() WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, actif);
            ps.setObject(2, utilisateurId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Erreur activation/désactivation utilisateur : {}", e.getMessage());
            return false;
        }
    }

    // ================================================================
    // Méthode de mapping ResultSet → Utilisateur
    // ================================================================

    /**
     * Construit un objet {@link Utilisateur} depuis un {@link ResultSet}.
     *
     * @param rs ResultSet positionné sur la ligne à lire
     * @return Objet Utilisateur hydraté
     * @throws SQLException en cas d'erreur de lecture
     */
    private Utilisateur mapperResultat(ResultSet rs) throws SQLException {
        Utilisateur u = new Utilisateur();

        u.setId((UUID) rs.getObject("id"));
        u.setNom(rs.getString("nom"));
        u.setPrenom(rs.getString("prenom"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setActif(rs.getBoolean("actif"));
        u.setPremierLogin(rs.getBoolean("premier_login"));
        u.setTentativesEchec(rs.getInt("tentatives_echec"));

        Timestamp verrou = rs.getTimestamp("verrouille_jusqu_au");
        if (verrou != null) {
            u.setVerrouilleJusquAu(verrou.toLocalDateTime());
        }

        Timestamp derniereConnexion = rs.getTimestamp("derniere_connexion");
        if (derniereConnexion != null) {
            u.setDerniereConnexion(derniereConnexion.toLocalDateTime());
        }

        u.setClePublique(rs.getString("cle_publique"));

        Timestamp creeLe = rs.getTimestamp("cree_le");
        if (creeLe != null) u.setCreeLe(creeLe.toLocalDateTime());

        Timestamp modifieLe = rs.getTimestamp("modifie_le");
        if (modifieLe != null) u.setModifieLe(modifieLe.toLocalDateTime());

        return u;
    }
}
