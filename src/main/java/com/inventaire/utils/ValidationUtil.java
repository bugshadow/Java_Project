package com.inventaire.utils;

import java.util.regex.Pattern;

/**
 * Utilitaire de validation des données saisies dans les formulaires.
 *
 * <p>Toutes les méthodes retournent un message d'erreur (non null) si la
 * validation échoue, ou {@code null} si la valeur est valide.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public final class ValidationUtil {

    /** Expression régulière pour la validation des emails (RFC 5322 simplifié). */
    private static final Pattern PATTERN_EMAIL =
        Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    /** Expression régulière pour les références produit (alphanumériques + tirets). */
    private static final Pattern PATTERN_REFERENCE =
        Pattern.compile("^[A-Z0-9][A-Z0-9\\-_]{1,98}[A-Z0-9]$");

    /** Constructeur privé — classe utilitaire. */
    private ValidationUtil() {}

    // ================================================================
    // Validation email
    // ================================================================

    /**
     * Valide le format d'une adresse email.
     *
     * @param email Email à valider
     * @return Message d'erreur ou null si valide
     */
    public static String validerEmail(String email) {
        if (email == null || email.isBlank()) {
            return "L'adresse email est obligatoire";
        }
        if (!PATTERN_EMAIL.matcher(email.trim()).matches()) {
            return "Format d'email invalide (exemple : utilisateur@domaine.com)";
        }
        return null;
    }

    // ================================================================
    // Validation champs texte obligatoires
    // ================================================================

    /**
     * Valide un champ texte obligatoire avec longueur minimale et maximale.
     *
     * @param valeur    Valeur à valider
     * @param nomChamp  Nom du champ (pour le message d'erreur)
     * @param minLongueur Longueur minimale
     * @param maxLongueur Longueur maximale
     * @return Message d'erreur ou null si valide
     */
    public static String validerTexteObligatoire(String valeur, String nomChamp,
                                                  int minLongueur, int maxLongueur) {
        if (valeur == null || valeur.isBlank()) {
            return nomChamp + " est obligatoire";
        }
        String trimmed = valeur.trim();
        if (trimmed.length() < minLongueur) {
            return nomChamp + " doit contenir au moins " + minLongueur + " caractères";
        }
        if (trimmed.length() > maxLongueur) {
            return nomChamp + " ne peut pas dépasser " + maxLongueur + " caractères";
        }
        return null;
    }

    // ================================================================
    // Validation référence produit
    // ================================================================

    /**
     * Valide une référence produit (alphanumériques + tirets/underscores).
     *
     * @param reference Référence à valider
     * @return Message d'erreur ou null si valide
     */
    public static String validerReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return "La référence produit est obligatoire";
        }
        String upper = reference.trim().toUpperCase();
        if (upper.length() < 3 || upper.length() > 100) {
            return "La référence doit contenir entre 3 et 100 caractères";
        }
        if (!PATTERN_REFERENCE.matcher(upper).matches()) {
            return "La référence ne peut contenir que des lettres majuscules, "
                 + "chiffres, tirets (-) et underscores (_)";
        }
        return null;
    }

    // ================================================================
    // Validation quantités
    // ================================================================

    /**
     * Valide qu'une quantité est un entier strictement positif.
     *
     * @param valeur Valeur sous forme de texte
     * @return Message d'erreur ou null si valide
     */
    public static String validerQuantite(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return "La quantité est obligatoire";
        }
        try {
            int q = Integer.parseInt(valeur.trim());
            if (q <= 0) {
                return "La quantité doit être un entier strictement positif";
            }
        } catch (NumberFormatException e) {
            return "La quantité doit être un nombre entier valide";
        }
        return null;
    }

    /**
     * Valide une quantité entière avec des bornes min/max.
     *
     * @param valeur Valeur sous forme de texte
     * @param min    Valeur minimale autorisée
     * @param max    Valeur maximale autorisée
     * @return Message d'erreur ou null si valide
     */
    public static String validerQuantiteBornee(String valeur, int min, int max) {
        String erreurBase = validerQuantite(valeur);
        if (erreurBase != null) return erreurBase;

        int q = Integer.parseInt(valeur.trim());
        if (q < min) return "La quantité doit être d'au moins " + min;
        if (q > max) return "La quantité ne peut pas dépasser " + max;

        return null;
    }

    // ================================================================
    // Validation prix
    // ================================================================

    /**
     * Valide un prix en décimal (peut être vide pour les champs optionnels).
     *
     * @param valeur Valeur sous forme de texte
     * @return Message d'erreur ou null si valide (ou vide)
     */
    public static String validerPrix(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null; // Prix optionnel
        }
        try {
            double prix = Double.parseDouble(valeur.trim().replace(",", "."));
            if (prix < 0) {
                return "Le prix ne peut pas être négatif";
            }
        } catch (NumberFormatException e) {
            return "Le prix doit être un nombre décimal valide (ex: 12.99)";
        }
        return null;
    }

    // ================================================================
    // Méthodes utilitaires
    // ================================================================

    /**
     * Nettoie et normalise une référence produit en majuscules.
     *
     * @param reference Référence brute saisie par l'utilisateur
     * @return Référence normalisée
     */
    public static String normaliserReference(String reference) {
        if (reference == null) return "";
        return reference.trim().toUpperCase().replaceAll("\\s+", "-");
    }

    /**
     * Vérifie si une chaîne est non nulle et non vide.
     *
     * @param valeur Chaîne à vérifier
     * @return {@code true} si la chaîne est non nulle et non vide
     */
    public static boolean estNonVide(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
