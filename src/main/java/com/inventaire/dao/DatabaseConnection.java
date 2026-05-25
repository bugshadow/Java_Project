package com.inventaire.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gestionnaire de connexions PostgreSQL via HikariCP.
 *
 * <p>Implémente le pattern Singleton pour garantir un seul pool de connexions
 * partagé dans toute l'application. La configuration est chargée depuis
 * {@code application.properties}.
 *
 * <p>Exemple d'utilisation :
 * <pre>{@code
 * try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
 *     // ... utilisation de la connexion
 * }
 * }</pre>
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public final class DatabaseConnection {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnection.class);

    /** Instance unique (Singleton thread-safe). */
    private static volatile DatabaseConnection instance;

    /** Pool de connexions HikariCP. */
    private final HikariDataSource dataSource;

    // ================================================================
    // Constructeur privé — initialisation HikariCP
    // ================================================================

    /**
     * Initialise le pool HikariCP en lisant {@code application.properties}.
     *
     * @throws RuntimeException si la configuration est invalide ou PostgreSQL inaccessible
     */
    private DatabaseConnection() {
        Properties props = chargerConfiguration();
        
        // Chargement des variables d'environnement depuis le fichier .env
        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            LOG.warn("Fichier .env introuvable ou erreur de chargement : {}", e.getMessage());
        }

        HikariConfig config = new HikariConfig();

        // Résolution des paramètres (Priorité : 1. .env, 2. application.properties, 3. valeur par défaut)
        String dbUrl = (dotenv != null && dotenv.get("DB_URL") != null) ? dotenv.get("DB_URL") : 
            props.getProperty("db.url", "jdbc:postgresql://localhost:5432/inventaire_db");
            
        String dbUser = (dotenv != null && dotenv.get("DB_USERNAME") != null) ? dotenv.get("DB_USERNAME") : 
            props.getProperty("db.username", "postgres");
            
        String dbPass = (dotenv != null && dotenv.get("DB_PASSWORD") != null) ? dotenv.get("DB_PASSWORD") : 
            props.getProperty("db.password", "");

        // ---- Paramètres de connexion ----
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPass);
        config.setDriverClassName("org.postgresql.Driver");

        // ---- Pool de connexions ----
        config.setMaximumPoolSize(
            Integer.parseInt(props.getProperty("db.pool.maxSize", "10")));
        config.setMinimumIdle(
            Integer.parseInt(props.getProperty("db.pool.minIdle", "2")));
        config.setConnectionTimeout(
            Long.parseLong(props.getProperty("db.pool.connectionTimeout", "30000")));
        config.setIdleTimeout(
            Long.parseLong(props.getProperty("db.pool.idleTimeout", "600000")));
        config.setMaxLifetime(
            Long.parseLong(props.getProperty("db.pool.maxLifetime", "1800000")));

        // ---- Paramètres supplémentaires ----
        config.setPoolName("InventaireHikariPool");
        config.setAutoCommit(true);

        // Validation de la connexion
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);

        // Paramètres PostgreSQL spécifiques
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("ApplicationName", "InventaireBlockchain");

        // ---- Logging de la configuration (sans mot de passe) ----
        LOG.info("Initialisation HikariCP — URL: {} | Pool max: {}",
            config.getJdbcUrl(), config.getMaximumPoolSize());

        this.dataSource = new HikariDataSource(config);

        LOG.info("Pool de connexions PostgreSQL initialisé avec succès.");
    }

    // ================================================================
    // Pattern Singleton (double-checked locking)
    // ================================================================

    /**
     * Retourne l'instance unique du gestionnaire de connexions.
     *
     * @return Instance de {@code DatabaseConnection}
     */
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    // ================================================================
    // Méthodes publiques
    // ================================================================

    /**
     * Obtient une connexion depuis le pool HikariCP.
     *
     * <p>La connexion doit être fermée après usage (try-with-resources recommandé).
     * Sa fermeture la restitue automatiquement au pool.
     *
     * @return Connexion JDBC active
     * @throws SQLException si aucune connexion n'est disponible
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Vérifie que la connexion à PostgreSQL est active.
     *
     * @return {@code true} si PostgreSQL est joignable
     */
    public boolean estConnecte() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            LOG.error("Vérification connexion PostgreSQL échouée : {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ferme proprement le pool de connexions.
     * Doit être appelé à la fermeture de l'application.
     */
    public void fermer() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOG.info("Pool de connexions HikariCP fermé.");
        }
    }

    /**
     * Retourne les statistiques du pool (utile pour le monitoring).
     *
     * @return Chaîne de statistiques HikariCP
     */
    public String getStatistiques() {
        if (dataSource == null) return "Pool non initialisé";
        return String.format("Pool[actives=%d, inactives=%d, en attente=%d, total=%d]",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
            dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }

    // ================================================================
    // Méthodes utilitaires privées
    // ================================================================

    /**
     * Charge la configuration depuis {@code application.properties}.
     *
     * @return Propriétés chargées
     */
    private Properties chargerConfiguration() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                LOG.info("Configuration chargée depuis application.properties");
            } else {
                LOG.warn("application.properties introuvable — utilisation des valeurs par défaut");
            }
        } catch (IOException e) {
            LOG.error("Erreur lecture application.properties : {}", e.getMessage());
        }
        return props;
    }
}
