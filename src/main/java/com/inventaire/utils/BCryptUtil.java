package com.inventaire.utils;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitaire de hachage BCrypt pour la sécurisation des mots de passe.
 *
 * <p>Utilise la bibliothèque jBCrypt avec un facteur de coût de 12.
 * Le mot de passe en clair n'est JAMAIS loggué, même en mode debug.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public final class BCryptUtil {

    private static final Logger LOG = LoggerFactory.getLogger(BCryptUtil.class);

    /** Facteur de coût BCrypt (2^12 = 4096 itérations). Ajuster selon les performances. */
    private static final int FACTEUR_COUT = 12;

    /** Constructeur privé — classe utilitaire non instanciable. */
    private BCryptUtil() {
        throw new UnsupportedOperationException("Classe utilitaire — ne pas instancier");
    }

    // ================================================================
    // Méthodes publiques
    // ================================================================

    /**
     * Génère un hash BCrypt sécurisé à partir d'un mot de passe en clair.
     *
     * <p>Le hash inclut automatiquement un sel aléatoire unique.
     *
     * @param motDePasseClair Mot de passe en clair (ne sera pas loggué)
     * @return Hash BCrypt prêt à être stocké en base de données
     * @throws IllegalArgumentException si le mot de passe est null ou vide
     */
    public static String hasher(String motDePasseClair) {
        if (motDePasseClair == null || motDePasseClair.isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide");
        }

        String sel = BCrypt.gensalt(FACTEUR_COUT);
        String hash = BCrypt.hashpw(motDePasseClair, sel);

        LOG.debug("Hash BCrypt généré avec facteur de coût {}", FACTEUR_COUT);
        // IMPORTANT : ne JAMAIS logger le mot de passe ou le hash complet en production
        return hash;
    }

    /**
     * Vérifie si un mot de passe en clair correspond à un hash BCrypt stocké.
     *
     * @param motDePasseClair Mot de passe saisi par l'utilisateur
     * @param hashStocke      Hash BCrypt récupéré de la base de données
     * @return {@code true} si le mot de passe correspond au hash
     * @throws IllegalArgumentException si l'un des paramètres est null
     */
    public static boolean verifier(String motDePasseClair, String hashStocke) {
        if (motDePasseClair == null || hashStocke == null) {
            throw new IllegalArgumentException("Paramètres de vérification invalides");
        }

        try {
            boolean resultat = BCrypt.checkpw(motDePasseClair, hashStocke);
            LOG.debug("Vérification BCrypt : {}", resultat ? "succès" : "échec");
            return resultat;
        } catch (Exception e) {
            // Hash mal formé ou attaque par force brute
            LOG.warn("Erreur vérification BCrypt (hash potentiellement invalide) : {}", e.getMessage());
            return false;
        }
    }

    /**
     * Valide la robustesse d'un mot de passe selon les règles de sécurité.
     *
     * <p>Règles :
     * <ul>
     *   <li>Minimum 8 caractères</li>
     *   <li>Au moins 1 lettre majuscule</li>
     *   <li>Au moins 1 chiffre</li>
     *   <li>Au moins 1 caractère spécial (@$!%*?&amp;#)</li>
     * </ul>
     *
     * @param motDePasse Mot de passe à valider
     * @return Message d'erreur si invalide, ou null si valide
     */
    public static String validerRobustesse(String motDePasse) {
        if (motDePasse == null || motDePasse.length() < 8) {
            return "Le mot de passe doit contenir au moins 8 caractères";
        }
        if (!motDePasse.matches(".*[A-Z].*")) {
            return "Le mot de passe doit contenir au moins une lettre majuscule";
        }
        if (!motDePasse.matches(".*[0-9].*")) {
            return "Le mot de passe doit contenir au moins un chiffre";
        }
        if (!motDePasse.matches(".*[@$!%*?&#+\\-_].*")) {
            return "Le mot de passe doit contenir au moins un caractère spécial (@$!%*?&#)";
        }
        return null; // Valide
    }

    /**
     * Génère un mot de passe temporaire aléatoire conforme aux règles de sécurité.
     * Utilisé lors de la création d'un utilisateur par un administrateur.
     *
     * @return Mot de passe temporaire aléatoire
     */
    public static String genererMotDePasseTemporaire() {
        // Générer un mot de passe sécurisé combinant différents jeux de caractères
        String majuscules = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String minuscules = "abcdefghijklmnopqrstuvwxyz";
        String chiffres   = "0123456789";
        String speciaux   = "@$!%*?&#";

        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();

        // Au moins un de chaque catégorie requis
        sb.append(majuscules.charAt(random.nextInt(majuscules.length())));
        sb.append(minuscules.charAt(random.nextInt(minuscules.length())));
        sb.append(chiffres.charAt(random.nextInt(chiffres.length())));
        sb.append(speciaux.charAt(random.nextInt(speciaux.length())));

        // Compléter avec des caractères aléatoires jusqu'à 12 caractères
        String tousCaracteres = majuscules + minuscules + chiffres + speciaux;
        for (int i = 4; i < 12; i++) {
            sb.append(tousCaracteres.charAt(random.nextInt(tousCaracteres.length())));
        }

        // Mélanger les caractères pour éviter un motif prévisible
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }
}
