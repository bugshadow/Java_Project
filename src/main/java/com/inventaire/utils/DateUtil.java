package com.inventaire.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Utilitaire de formatage et manipulation des dates.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public final class DateUtil {

    /** Format d'affichage date + heure (ex: 25/05/2026 14:30:00). */
    public static final DateTimeFormatter FORMAT_DATE_HEURE =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Format d'affichage date seule (ex: 25/05/2026). */
    public static final DateTimeFormatter FORMAT_DATE =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Format d'affichage heure seule (ex: 14:30). */
    public static final DateTimeFormatter FORMAT_HEURE =
        DateTimeFormatter.ofPattern("HH:mm");

    /** Format ISO 8601 pour les échanges avec la blockchain. */
    public static final DateTimeFormatter FORMAT_ISO =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Format pour les noms de fichiers de rapports (ex: 2026-05-25_143000). */
    public static final DateTimeFormatter FORMAT_FICHIER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /** Constructeur privé — classe utilitaire. */
    private DateUtil() {}

    // ================================================================
    // Formatage
    // ================================================================

    /**
     * Formate un {@link LocalDateTime} en chaîne lisible (dd/MM/yyyy HH:mm:ss).
     *
     * @param dateHeure Date et heure à formater
     * @return Chaîne formatée, ou "—" si null
     */
    public static String formaterDateHeure(LocalDateTime dateHeure) {
        if (dateHeure == null) return "—";
        return dateHeure.format(FORMAT_DATE_HEURE);
    }

    /**
     * Formate un {@link LocalDate} en chaîne lisible (dd/MM/yyyy).
     *
     * @param date Date à formater
     * @return Chaîne formatée, ou "—" si null
     */
    public static String formaterDate(LocalDate date) {
        if (date == null) return "—";
        return date.format(FORMAT_DATE);
    }

    /**
     * Formate un {@link LocalDateTime} pour les noms de fichiers.
     *
     * @param dateHeure Date et heure
     * @return Chaîne au format yyyy-MM-dd_HHmmss
     */
    public static String formaterPourFichier(LocalDateTime dateHeure) {
        if (dateHeure == null) return LocalDateTime.now().format(FORMAT_FICHIER);
        return dateHeure.format(FORMAT_FICHIER);
    }

    /**
     * Formate un {@link LocalDateTime} au format ISO pour la blockchain.
     *
     * @param dateHeure Date et heure
     * @return Chaîne ISO 8601
     */
    public static String formaterIso(LocalDateTime dateHeure) {
        if (dateHeure == null) return LocalDateTime.now().format(FORMAT_ISO);
        return dateHeure.format(FORMAT_ISO);
    }

    // ================================================================
    // Calculs
    // ================================================================

    /**
     * Calcule la différence en minutes entre deux instants.
     *
     * @param debut Instant de début
     * @param fin   Instant de fin
     * @return Nombre de minutes (positif si fin est après début)
     */
    public static long minutesEntre(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null || fin == null) return 0;
        return ChronoUnit.MINUTES.between(debut, fin);
    }

    /**
     * Vérifie si une date est dans le futur (après maintenant).
     *
     * @param date Date à vérifier
     * @return {@code true} si la date est dans le futur
     */
    public static boolean estDansFutur(LocalDateTime date) {
        if (date == null) return false;
        return date.isAfter(LocalDateTime.now());
    }

    /**
     * Retourne une représentation relative d'un instant (ex: "il y a 5 min").
     *
     * @param dateHeure Instant à représenter
     * @return Chaîne relative (ou date complète si > 24h)
     */
    public static String affichageRelatif(LocalDateTime dateHeure) {
        if (dateHeure == null) return "—";

        long minutes = ChronoUnit.MINUTES.between(dateHeure, LocalDateTime.now());

        if (minutes < 1)  return "À l'instant";
        if (minutes < 60) return "Il y a " + minutes + " min";

        long heures = ChronoUnit.HOURS.between(dateHeure, LocalDateTime.now());
        if (heures < 24)  return "Il y a " + heures + "h";

        return formaterDateHeure(dateHeure);
    }

    /**
     * Retourne la date de début d'une période en jours depuis aujourd'hui.
     *
     * @param nbJours Nombre de jours en arrière (ex: 30 pour les 30 derniers jours)
     * @return Date de début de la période
     */
    public static LocalDate debutPeriode(int nbJours) {
        return LocalDate.now().minusDays(nbJours);
    }
}
