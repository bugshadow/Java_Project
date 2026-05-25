package com.inventaire.session;

import com.inventaire.models.Utilisateur;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestionnaire de session utilisateur — Singleton.
 *
 * <p>Maintient l'état de l'utilisateur connecté et gère le timeout
 * d'inactivité automatique (30 minutes par défaut).
 *
 * <p>Le timer est réinitialisé à chaque action utilisateur via
 * {@link #resetTimer()}. Si aucune activité n'est détectée pendant
 * la durée configurée, l'utilisateur est déconnecté automatiquement.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public final class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    /** Durée d'inactivité en minutes avant déconnexion automatique. */
    private static final int TIMEOUT_INACTIVITE_MINUTES = 30;

    /** Instance unique du SessionManager. */
    private static volatile SessionManager instance;

    /** Utilisateur actuellement connecté. */
    private Utilisateur utilisateurCourant;

    /** Timer JavaFX pour surveiller l'inactivité. */
    private Timeline timerInactivite;

    /** Callback appelé lors d'une déconnexion par timeout (pour revenir au login). */
    private Runnable callbackDeconnexion;

    // ================================================================
    // Constructeur et Singleton
    // ================================================================

    /** Constructeur privé. */
    private SessionManager() {}

    /**
     * Retourne l'instance unique du SessionManager.
     *
     * @return Instance de {@code SessionManager}
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    // ================================================================
    // Gestion de la session
    // ================================================================

    /**
     * Ouvre une nouvelle session pour l'utilisateur connecté.
     * Démarre le timer d'inactivité.
     *
     * @param utilisateur        Utilisateur connecté
     * @param callbackDeconnexion Action à exécuter lors d'un timeout (navigation vers login)
     */
    public void ouvrirSession(Utilisateur utilisateur, Runnable callbackDeconnexion) {
        this.utilisateurCourant = utilisateur;
        this.callbackDeconnexion = callbackDeconnexion;

        demarrerTimer();

        LOG.info("Session ouverte pour : {} (rôle: {})",
            utilisateur.getEmail(), utilisateur.getRole());
    }

    /**
     * Déconnecte l'utilisateur et ferme la session.
     * Arrête le timer d'inactivité et vide les données de session.
     */
    public void deconnecter() {
        if (utilisateurCourant != null) {
            LOG.info("Déconnexion de l'utilisateur : {}", utilisateurCourant.getEmail());
        }

        arreterTimer();
        utilisateurCourant = null;
        callbackDeconnexion = null;
    }

    /**
     * Réinitialise le timer d'inactivité.
     * Doit être appelé à chaque action utilisateur dans l'application.
     */
    public void resetTimer() {
        if (timerInactivite != null && utilisateurCourant != null) {
            timerInactivite.stop();
            timerInactivite.playFromStart();
        }
    }

    // ================================================================
    // Accesseurs
    // ================================================================

    /**
     * Retourne l'utilisateur actuellement connecté.
     *
     * @return Utilisateur connecté, ou null si aucune session active
     */
    public Utilisateur getUtilisateur() {
        return utilisateurCourant;
    }

    /**
     * Vérifie si une session est active.
     *
     * @return {@code true} si un utilisateur est connecté
     */
    public boolean isConnecte() {
        return utilisateurCourant != null;
    }

    /**
     * Vérifie si l'utilisateur courant a le rôle ADMIN.
     *
     * @return {@code true} si l'utilisateur est administrateur
     */
    public boolean isAdmin() {
        return isConnecte() && utilisateurCourant.estAdmin();
    }

    /**
     * Vérifie si l'utilisateur courant peut saisir des transactions.
     *
     * @return {@code true} si le rôle permet la saisie de transactions
     */
    public boolean peutSaisirTransactions() {
        return isConnecte() && utilisateurCourant.peutSaisirTransactions();
    }

    /**
     * Retourne le rôle de l'utilisateur courant.
     *
     * @return Rôle ou chaîne vide si non connecté
     */
    public String getRoleCourant() {
        if (!isConnecte()) return "";
        return utilisateurCourant.getRole();
    }

    // ================================================================
    // Gestion du timer d'inactivité
    // ================================================================

    /**
     * Démarre le timer JavaFX d'inactivité.
     * Si le timer expire, déclenche la déconnexion automatique.
     */
    private void demarrerTimer() {
        arreterTimer(); // Arrêter l'ancien timer si existant

        timerInactivite = new Timeline(
            new KeyFrame(
                Duration.minutes(TIMEOUT_INACTIVITE_MINUTES),
                event -> deconnexionParTimeout()
            )
        );
        timerInactivite.setCycleCount(1);
        timerInactivite.play();

        LOG.debug("Timer d'inactivité démarré : {} minutes", TIMEOUT_INACTIVITE_MINUTES);
    }

    /**
     * Arrête le timer d'inactivité.
     */
    private void arreterTimer() {
        if (timerInactivite != null) {
            timerInactivite.stop();
            timerInactivite = null;
        }
    }

    /**
     * Déclenché lorsque le timeout d'inactivité expire.
     * Déconnecte l'utilisateur et exécute le callback de navigation vers le login.
     */
    private void deconnexionParTimeout() {
        String email = utilisateurCourant != null ? utilisateurCourant.getEmail() : "inconnu";
        LOG.warn("Timeout d'inactivité : déconnexion automatique de {}", email);

        Runnable callback = callbackDeconnexion;
        deconnecter();

        if (callback != null) {
            Platform.runLater(callback);
        }
    }
}
