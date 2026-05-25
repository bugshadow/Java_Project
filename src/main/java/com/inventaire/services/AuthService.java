package com.inventaire.services;

import com.inventaire.dao.DatabaseConnection;
import com.inventaire.dao.UtilisateurDAO;
import com.inventaire.models.Utilisateur;
import com.inventaire.utils.BCryptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Service d'authentification des utilisateurs.
 *
 * <p>Implémente la logique d'authentification avec :
 * <ul>
 *   <li>Vérification BCrypt du mot de passe</li>
 *   <li>Gestion du verrouillage de compte (3 tentatives → 30 min)</li>
 *   <li>Journalisation de toutes les tentatives dans {@code logs_connexion}</li>
 * </ul>
 *
 * <p>Les contrôleurs ne doivent JAMAIS accéder directement aux DAO.
 * Toute la logique passe par ce service.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private final UtilisateurDAO utilisateurDAO;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param db Instance du gestionnaire de connexions PostgreSQL
     */
    public AuthService(DatabaseConnection db) {
        this.utilisateurDAO = new UtilisateurDAO(db);
    }

    // ================================================================
    // Authentification
    // ================================================================

    /**
     * Tente d'authentifier un utilisateur avec son email et mot de passe.
     *
     * <p>Processus :
     * <ol>
     *   <li>Vérification existence de l'email en base</li>
     *   <li>Vérification compte actif</li>
     *   <li>Vérification compte non verrouillé</li>
     *   <li>Vérification mot de passe BCrypt</li>
     *   <li>Mise à jour des compteurs (succès ou échec)</li>
     *   <li>Journalisation dans logs_connexion</li>
     * </ol>
     *
     * @param email        Email de l'utilisateur
     * @param motDePasse   Mot de passe en clair (JAMAIS loggué)
     * @return {@link Optional} contenant l'utilisateur si authentification réussie,
     *         ou vide avec le message d'erreur disponible via {@link #getDerniereErreur()}
     */
    public Optional<Utilisateur> authentifier(String email, String motDePasse) {
        dernierMessageErreur = null;

        // ---- Validation basique des paramètres ----
        if (email == null || email.isBlank() || motDePasse == null || motDePasse.isEmpty()) {
            dernierMessageErreur = "Veuillez saisir votre email et votre mot de passe";
            enregistrerLog(null, email, false, dernierMessageErreur);
            return Optional.empty();
        }

        LOG.info("Tentative de connexion pour : {}", email);

        // ---- Recherche de l'utilisateur ----
        Optional<Utilisateur> optUtilisateur = utilisateurDAO.trouverParEmail(email.trim().toLowerCase());

        if (optUtilisateur.isEmpty()) {
            // Ne pas révéler que l'email n'existe pas (sécurité)
            dernierMessageErreur = "Email ou mot de passe incorrect";
            enregistrerLog(null, email, false, "Email introuvable : " + email);
            LOG.warn("Tentative de connexion avec email inconnu : {}", email);
            return Optional.empty();
        }

        Utilisateur utilisateur = optUtilisateur.get();

        // ---- Vérification compte actif ----
        if (!utilisateur.isActif()) {
            dernierMessageErreur = "Votre compte a été désactivé. Contactez un administrateur.";
            enregistrerLog(utilisateur.getId(), email, false, "Compte inactif");
            LOG.warn("Tentative connexion sur compte inactif : {}", email);
            return Optional.empty();
        }

        // ---- Vérification verrouillage ----
        if (utilisateur.estVerrouille()) {
            String minutesRestantes = calculerMinutesVerrouillage(utilisateur);
            dernierMessageErreur = "Compte temporairement verrouillé. "
                + "Réessayez dans " + minutesRestantes + " minutes.";
            enregistrerLog(utilisateur.getId(), email, false, "Compte verrouillé");
            LOG.warn("Tentative connexion sur compte verrouillé : {}", email);
            return Optional.empty();
        }

        // ---- Vérification mot de passe BCrypt ----
        boolean motDePasseValide = BCryptUtil.verifier(motDePasse, utilisateur.getPasswordHash());

        if (!motDePasseValide) {
            utilisateurDAO.incrementerTentativesEchec(email);

            // Recharger pour obtenir le nombre de tentatives mis à jour
            optUtilisateur = utilisateurDAO.trouverParEmail(email);
            int tentatives = optUtilisateur.map(Utilisateur::getTentativesEchec).orElse(0);

            if (tentatives >= 3) {
                dernierMessageErreur = "Compte verrouillé pour 30 minutes "
                    + "suite à trop de tentatives échouées.";
            } else {
                int restantes = 3 - tentatives;
                dernierMessageErreur = "Mot de passe incorrect. "
                    + restantes + " tentative(s) restante(s) avant verrouillage.";
            }

            enregistrerLog(utilisateur.getId(), email, false,
                "Mot de passe incorrect (tentative " + tentatives + ")");
            LOG.warn("Mot de passe incorrect pour {} (tentative {})", email, tentatives);
            return Optional.empty();
        }

        // ---- Authentification réussie ----
        utilisateurDAO.enregistrerConnexionReussie(utilisateur.getId());
        enregistrerLog(utilisateur.getId(), email, true, "Connexion réussie");

        // Recharger l'utilisateur avec les données mises à jour
        optUtilisateur = utilisateurDAO.trouverParEmail(email);
        LOG.info("Connexion réussie : {} (rôle: {})", email, utilisateur.getRole());

        return optUtilisateur;
    }

    // ================================================================
    // Changement de mot de passe
    // ================================================================

    /**
     * Change le mot de passe d'un utilisateur après vérification de l'ancien.
     *
     * @param utilisateur     Utilisateur dont on change le mot de passe
     * @param ancienMdp       Ancien mot de passe en clair
     * @param nouveauMdp      Nouveau mot de passe en clair
     * @param confirmationMdp Confirmation du nouveau mot de passe
     * @return Message d'erreur ou null si le changement a réussi
     */
    public String changerMotDePasse(Utilisateur utilisateur, String ancienMdp,
                                     String nouveauMdp, String confirmationMdp) {
        // Vérification de l'ancien mot de passe
        if (!BCryptUtil.verifier(ancienMdp, utilisateur.getPasswordHash())) {
            return "L'ancien mot de passe est incorrect";
        }

        // Vérification que les deux nouveaux sont identiques
        if (!nouveauMdp.equals(confirmationMdp)) {
            return "La confirmation du nouveau mot de passe ne correspond pas";
        }

        // Vérification robustesse du nouveau mot de passe
        String erreurRobustesse = BCryptUtil.validerRobustesse(nouveauMdp);
        if (erreurRobustesse != null) {
            return erreurRobustesse;
        }

        // Hachage et enregistrement du nouveau mot de passe
        String nouveauHash = BCryptUtil.hasher(nouveauMdp);
        boolean succes = utilisateurDAO.mettreAJourMotDePasse(utilisateur.getId(), nouveauHash);

        if (succes) {
            LOG.info("Mot de passe changé avec succès pour : {}", utilisateur.getEmail());
            return null;
        }

        return "Erreur lors de la mise à jour du mot de passe. Réessayez.";
    }

    /**
     * Réinitialise le mot de passe d'un utilisateur (action admin).
     * Génère un mot de passe temporaire et l'envoie à l'administrateur.
     *
     * @param utilisateurId UUID de l'utilisateur à réinitialiser
     * @return Le mot de passe temporaire généré, ou null si erreur
     */
    public String reinitialiserMotDePasse(java.util.UUID utilisateurId) {
        String motDePasseTemp = BCryptUtil.genererMotDePasseTemporaire();
        String hash = BCryptUtil.hasher(motDePasseTemp);

        boolean succes = utilisateurDAO.mettreAJourMotDePasse(utilisateurId, hash);
        // Forcer premier_login = true (géré dans mettreAJourMotDePasse si nécessaire)

        if (succes) {
            LOG.info("Mot de passe réinitialisé pour utilisateur : {}", utilisateurId);
            return motDePasseTemp;
        }

        return null;
    }

    // ================================================================
    // Gestion des messages d'erreur
    // ================================================================

    /** Dernier message d'erreur d'authentification. */
    private String dernierMessageErreur;

    /**
     * Retourne le message d'erreur de la dernière tentative d'authentification.
     *
     * @return Message d'erreur, ou null si pas d'erreur
     */
    public String getDerniereErreur() {
        return dernierMessageErreur;
    }

    // ================================================================
    // Méthodes privées
    // ================================================================

    /**
     * Enregistre une tentative de connexion dans les logs.
     */
    private void enregistrerLog(java.util.UUID utilisateurId, String email,
                                  boolean succes, String message) {
        try {
            utilisateurDAO.enregistrerLogConnexion(utilisateurId, email, succes, message);
        } catch (Exception e) {
            LOG.error("Erreur enregistrement log connexion : {}", e.getMessage());
        }
    }

    /**
     * Calcule le nombre de minutes restantes avant déverrouillage.
     */
    private String calculerMinutesVerrouillage(Utilisateur utilisateur) {
        if (utilisateur.getVerrouilleJusquAu() == null) return "0";

        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(
            java.time.LocalDateTime.now(),
            utilisateur.getVerrouilleJusquAu()
        );

        return String.valueOf(Math.max(0, minutes));
    }
}
