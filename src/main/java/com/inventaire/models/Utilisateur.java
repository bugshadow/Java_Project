package com.inventaire.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modèle métier représentant un utilisateur du système.
 *
 * <p>Correspond à la table {@code utilisateurs} de PostgreSQL.
 * Le mot de passe n'est jamais stocké en clair dans cet objet ;
 * seul le hash BCrypt est persisté.
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class Utilisateur {

    /** Identifiant unique UUID. */
    private UUID id;

    /** Nom de famille. */
    private String nom;

    /** Prénom. */
    private String prenom;

    /** Adresse email (identifiant de connexion). */
    private String email;

    /** Hash BCrypt du mot de passe (jamais le mot de passe en clair). */
    private String passwordHash;

    /** Rôle dans le système. */
    private String role;

    /** Indique si le compte est actif. */
    private boolean actif;

    /** Indique si l'utilisateur doit changer son mot de passe à la prochaine connexion. */
    private boolean premierLogin;

    /** Nombre de tentatives de connexion échouées consécutives. */
    private int tentativesEchec;

    /** Date jusqu'à laquelle le compte est verrouillé (null = non verrouillé). */
    private LocalDateTime verrouilleJusquAu;

    /** Date et heure de la dernière connexion réussie. */
    private LocalDateTime derniereConnexion;

    /** Clé publique pour signature blockchain (optionnel). */
    private String clePublique;

    /** Date de création du compte. */
    private LocalDateTime creeLe;

    /** Date de dernière modification du compte. */
    private LocalDateTime modifieLe;

    // ================================================================
    // Constructeurs
    // ================================================================

    /** Constructeur vide requis pour JDBC. */
    public Utilisateur() {}

    /**
     * Constructeur pour la création d'un nouvel utilisateur.
     *
     * @param nom          Nom de famille
     * @param prenom       Prénom
     * @param email        Email (identifiant unique)
     * @param passwordHash Hash BCrypt du mot de passe
     * @param role         Rôle système
     */
    public Utilisateur(String nom, String prenom, String email,
                       String passwordHash, String role) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.actif = true;
        this.premierLogin = true;
        this.tentativesEchec = 0;
    }

    // ================================================================
    // Méthodes métier
    // ================================================================

    /**
     * Retourne le nom complet (prénom + nom).
     *
     * @return Chaîne "Prénom Nom"
     */
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    /**
     * Vérifie si le compte est actuellement verrouillé.
     *
     * @return {@code true} si le compte est verrouillé et que le délai n'est pas expiré
     */
    public boolean estVerrouille() {
        if (verrouilleJusquAu == null) return false;
        return LocalDateTime.now().isBefore(verrouilleJusquAu);
    }

    /**
     * Vérifie si l'utilisateur possède le rôle spécifié.
     *
     * @param roleReqis Rôle à vérifier (ADMIN, GESTIONNAIRE, AUDITEUR, OPERATEUR)
     * @return {@code true} si l'utilisateur a ce rôle
     */
    public boolean aLeRole(String roleReqis) {
        return this.role != null && this.role.equalsIgnoreCase(roleReqis);
    }

    /**
     * Vérifie si l'utilisateur a les droits d'administration.
     *
     * @return {@code true} si le rôle est ADMIN
     */
    public boolean estAdmin() {
        return aLeRole("ADMIN");
    }

    /**
     * Vérifie si l'utilisateur peut saisir des transactions (entrée/sortie/transfert).
     *
     * @return {@code true} pour ADMIN, GESTIONNAIRE et OPERATEUR
     */
    public boolean peutSaisirTransactions() {
        return aLeRole("ADMIN") || aLeRole("GESTIONNAIRE") || aLeRole("OPERATEUR");
    }

    /**
     * Vérifie si l'utilisateur peut accéder aux rapports.
     *
     * @return {@code true} pour ADMIN, GESTIONNAIRE et AUDITEUR
     */
    public boolean peutVoirRapports() {
        return aLeRole("ADMIN") || aLeRole("GESTIONNAIRE") || aLeRole("AUDITEUR");
    }

    // ================================================================
    // Getters et Setters
    // ================================================================

    public UUID getId()                             { return id; }
    public void setId(UUID id)                      { this.id = id; }

    public String getNom()                          { return nom; }
    public void setNom(String nom)                  { this.nom = nom; }

    public String getPrenom()                       { return prenom; }
    public void setPrenom(String prenom)            { this.prenom = prenom; }

    public String getEmail()                        { return email; }
    public void setEmail(String email)              { this.email = email; }

    public String getPasswordHash()                 { return passwordHash; }
    public void setPasswordHash(String hash)        { this.passwordHash = hash; }

    public String getRole()                         { return role; }
    public void setRole(String role)                { this.role = role; }

    public boolean isActif()                        { return actif; }
    public void setActif(boolean actif)             { this.actif = actif; }

    public boolean isPremierLogin()                 { return premierLogin; }
    public void setPremierLogin(boolean pl)         { this.premierLogin = pl; }

    public int getTentativesEchec()                 { return tentativesEchec; }
    public void setTentativesEchec(int t)           { this.tentativesEchec = t; }

    public LocalDateTime getVerrouilleJusquAu()     { return verrouilleJusquAu; }
    public void setVerrouilleJusquAu(LocalDateTime d){ this.verrouilleJusquAu = d; }

    public LocalDateTime getDerniereConnexion()     { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime d){ this.derniereConnexion = d; }

    public String getClePublique()                  { return clePublique; }
    public void setClePublique(String cle)          { this.clePublique = cle; }

    public LocalDateTime getCreeLe()                { return creeLe; }
    public void setCreeLe(LocalDateTime d)          { this.creeLe = d; }

    public LocalDateTime getModifieLe()             { return modifieLe; }
    public void setModifieLe(LocalDateTime d)       { this.modifieLe = d; }

    @Override
    public String toString() {
        return "Utilisateur{email='" + email + "', role='" + role + "', actif=" + actif + "}";
    }
}
