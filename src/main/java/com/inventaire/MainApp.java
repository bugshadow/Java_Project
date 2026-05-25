package com.inventaire;

import com.inventaire.blockchain.EthereumClient;
import com.inventaire.dao.DatabaseConnection;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Point d'entree principal de l'application JavaFX.
 *
 * <p>Gere le cycle de vie de l'application :
 * <ul>
 *   <li>Initialisation des connexions (DB, Blockchain)</li>
 *   <li>Affichage de la fenetre principale</li>
 *   <li>Navigation entre les vues FXML</li>
 *   <li>Fermeture propre des ressources a l'arret</li>
 * </ul>
 *
 * @author Systeme Inventaire
 * @version 1.0.0
 */
public class MainApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);

    private static Stage primaryStg;

    @Override
    public void init() throws Exception {
        LOG.info("Demarrage de l'application Inventaire Blockchain...");
        
        // 1. Initialisation de la base de donnees
        try {
            DatabaseConnection.getInstance().getConnection().close();
            LOG.info("Connexion PostgreSQL verifiee avec succes.");
        } catch (Exception e) {
            LOG.error("Echec de la connexion PostgreSQL. Verifiez que la BD est lancee.", e);
        }

        // 2. Initialisation asynchrone de la connexion Blockchain
        Thread ethThread = new Thread(() -> {
            try {
                EthereumClient.getInstance();
            } catch (Exception e) {
                LOG.warn("Mode degrade : La connexion Ethereum a echoue. Les fonctions blockchain seront indisponibles.");
            }
        });
        ethThread.setDaemon(true);
        ethThread.start();
    }

    @Override
    public void start(Stage stage) throws Exception {
        primaryStg = stage;
        primaryStg.setTitle("Systeme d'Inventaire - Secured by Ethereum Blockchain");
        
        // Tente de charger une icone (si presente dans resources/img)
        try {
            URL iconUrl = getClass().getResource("/img/icon.png");
            if (iconUrl != null) {
                primaryStg.getIcons().add(new Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {
            LOG.debug("Icone non trouvee, utilisation par defaut.");
        }

        changerEcran("login.fxml");
        
        primaryStg.setWidth(1200);
        primaryStg.setHeight(800);
        primaryStg.centerOnScreen();
        primaryStg.show();
    }

    @Override
    public void stop() throws Exception {
        LOG.info("Arret de l'application...");
        
        // Fermeture des connexions
        DatabaseConnection.getInstance().fermer();
        
        LOG.info("Application arretee proprement.");
        Platform.exit();
        System.exit(0);
    }

    /**
     * Change la vue (ecran) actuelle de l'application.
     *
     * @param fxml Nom du fichier FXML (ex: "dashboard.fxml")
     */
    public static void changerEcran(String fxml) {
        try {
            URL url = MainApp.class.getResource("/fxml/" + fxml);
            if (url == null) {
                LOG.error("Fichier FXML introuvable : /fxml/{}", fxml);
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            
            Scene scene = primaryStg.getScene();
            if (scene == null) {
                scene = new Scene(root);
                primaryStg.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            
            LOG.debug("Navigation vers l'ecran : {}", fxml);
            
        } catch (IOException e) {
            LOG.error("Erreur lors du chargement de l'ecran {} : {}", fxml, e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
