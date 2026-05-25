# Systeme de Gestion d'Inventaire - Ethereum & JavaFX

Une application d'entreprise complete pour la gestion des stocks, garantissant la tracabilite absolue et l'immuabilite des transactions grace a la blockchain publique Ethereum (Testnet Sepolia).

## Architecture & Stack Technique

- **Interface Utilisateur (Desktop)** : JavaFX 17+ (MVC via FXML)
- **Base de donnees principale** : PostgreSQL 15+ (HikariCP pour le pool JDBC)
- **Reseau Blockchain** : Ethereum (Sepolia Testnet)
- **Smart Contract** : Solidity (InventaireContract.sol)
- **Communication Blockchain** : Web3j (via API Alchemy/Infura)
- **Generation de Rapports** : iText 7 (PDF) & Apache POI (Excel)
- **Securite** : jBCrypt (Hachage mots de passe)

## Prerequis Systeme

1. **Java Development Kit (JDK)** 17 ou superieur.
2. **Maven** (3.6+).
3. **PostgreSQL** 15+ installe en local.

## Installation et Demarrage

### 1. Base de donnees PostgreSQL

Creez une base de donnees PostgreSQL nommee `inventaire_db`.
```bash
psql -U postgres
CREATE DATABASE inventaire_db;
```

Executez le script SQL fourni pour creer le schema initial (les tables) :
```bash
psql -U postgres -d inventaire_db -f sql/schema.sql
```

### 2. Configuration du Portefeuille (MetaMask) et Reseau Sepolia

Pour interagir avec la blockchain de test, vous avez besoin d'un portefeuille Web3 configuré sur le bon réseau :
1. Installez l'extension de navigateur [MetaMask](https://metamask.io/).
2. Creez un nouveau portefeuille (sauvegardez precieusement votre phrase secrete).
3. Ouvrez MetaMask, cliquez sur le selecteur de reseau en haut a gauche. Activez "Afficher les reseaux de test" (Show test networks).
4. Selectionnez le reseau **Sepolia**.
5. Obtenez quelques ETH de test (Sepolia ETH) gratuitement depuis un Faucet comme [Alchemy Sepolia Faucet](https://sepoliafaucet.com/) ou [Infura Faucet](https://www.infura.io/faucet/sepolia). Ces faux ETH vous permettront d'executer et deployer le contrat.

### 3. Deploiement du Smart Contract Ethereum (Solidity)

Le fichier du contrat se trouve ici : `contracts/InventaireContract.sol`

1. Ouvrez [Remix IDE](https://remix.ethereum.org/).
2. Creez un nouveau fichier `InventaireContract.sol` et collez-y le code de notre contrat.
3. Allez dans l'onglet "Solidity Compiler" et compilez le contrat (version 0.8.x).
4. Allez dans l'onglet "Deploy & Run Transactions".
5. Changez "Environment" pour "Injected Provider - MetaMask" (Assurez-vous que votre MetaMask est sur le reseau Sepolia et que vous avez des fonds test).
6. Cliquez sur "Deploy". Une fois la transaction confirmee par MetaMask, votre contrat est deployee !
7. Copiez l'adresse du contrat deploye.

### 4. Fichier de Configuration (.env)

Creez un fichier `.env` a la racine du projet (en vous basant sur `.env.example`) :

```env
DB_URL=jdbc:postgresql://localhost:5432/inventaire_db
DB_USERNAME=postgres
DB_PASSWORD=VOTRE_MOT_DE_PASSE

# Configuration Ethereum Sepolia
ETH_NODE_URL=https://eth-sepolia.g.alchemy.com/v2/VOTRE_CLE_ALCHEMY_OU_INFURA
CONTRACT_ADDRESS=ADRESSE_DU_CONTRAT_DEPLOYE
WALLET_PRIVATE_KEY=VOTRE_CLE_PRIVEE_METAMASK
```
*(Ne partagez jamais ce fichier, il contient vos mots de passe et votre cle privee MetaMask !)*

> **Comptes utilisateurs par defaut (crees via le script SQL) :**
> 1. **Administrateur** | Email : `admin@inventaire.com` | Mot de passe : `Admin@1234`
> 2. **Gestionnaire** | Email : `gestionnaire@inventaire.com` | Mot de passe : `User@1234`
> 3. **Auditeur** | Email : `auditeur@inventaire.com` | Mot de passe : `User@1234`

### 5. Compilation et lancement de l'application JavaFX

A la racine du projet, executez Maven :

```bash
mvn clean javafx:run
```

Ou pour construire un executable complet avec dependances :

```bash
mvn clean package
```

## Structure du Projet

```text
inventaire-blockchain/
├── pom.xml                      # Configuration Maven
├── README.md                    # Ce fichier
├── sql/
│   └── schema.sql               # Structure PostgreSQL (Tables, Vues, Index)
├── contracts/
│   └── InventaireContract.sol   # Le Smart Contract Ethereum en Solidity
└── src/main/
    ├── java/com/inventaire/
    │   ├── MainApp.java         # Point d'entree JavaFX
    │   ├── models/              # POJOs (Produit, Transaction...)
    │   ├── dao/                 # Acces DB (JDBC pur)
    │   ├── services/            # Logique metier et appels blockchain
    │   ├── blockchain/          # Client Ethereum (Web3j)
    │   ├── controllers/         # Controleurs UI (JavaFX)
    │   ├── utils/               # Outils (Date, Validation, BCrypt)
    │   └── session/             # Session utilisateur
    └── resources/
        ├── application.properties # Configuration optionnelle
        ├── css/                 # Feuilles de style UI
        └── fxml/                # Vues interface graphique
```
## FAQ & Resolution des Problemes Connus (Historique)

Durant le developpement de ce projet, plusieurs defis techniques ont ete resolus. Voici les details pour aider les prochains auditeurs ou developpeurs :

### 1. Abandon d'Hyperledger Fabric et Migration vers Ethereum (Web3j)
**Le Probleme :** Lors de l'installation du chaincode Java en local via Docker Compose sur Windows, Hyperledger Fabric remontait l'erreur Error: chaincode type not supported: java command=detect. Le wrapper Docker pour Fabric plantait souvent par manque de ressources ou via des erreurs de timeout EOF, rendant le deploiement instable.
**La Solution :** L'architecture du projet a ete eppuree en abandonnant le deploiement lourd Hyperledger Fabric local au profit d'un environnement Ethereum base sur le Testnet public **Sepolia**. Tout le SDK Fabric a ete retire de Maven et remplace par **Web3j**, offrant une connexion via API (Alchemy/Infura) beaucoup plus legere et facile a tester en equipe.

### 2. Erreur de compilation Solidity : ParserError : Expected identifier but got reserved keyword 'reference'
**Le Probleme :** Lors de la compilation du contrat intelligent sur Remix IDE, Solidity (pour les versions superieures a la 0.8) refusait de compiler car il rencontrait l'attribut string reference dans la structure Product.
**La Solution :** Le mot 
eference est devenu un mot-cle reserve dans le langage Solidity (pour manipuler les pointeurs de memoire). L'attribut a ete renomme en productRef dans le fichier InventaireContract.sol, ce qui resout l'erreur de Remix.

### 3. Problemes d'Encodages (Caracteres avec accents)
**Le Probleme :** Sur l'invite de commande Windows et Powershell, la compilation Maven ou via javac provoquait des alertes de caracteres illisibles a cause des �, �, ou � incrustes dans le code Java ou meme d'erreurs d'encodage lors de la saisie via CLI.
**La Solution :** Tous les fichiers source (MainApp.java, HistoriqueController.java, TransactionController.java) ainsi que ce README.md ont ete entierement nettoyes pour remplacer les accents par des caracteres standard ascii (ex: e, a), supprimant la cause initiale des erreurs.
### 4. Probleme EVM Error: Product does not exist
**Le probleme :** Sur le testnet, une transaction via WebWait/Web3j renvoyait une erreur Revert : "Product does not exist" lorsqu'on tentait de decrementer un statut sur un nouveau produit, car il n'etait pas initialise avant.
**La solution :** Une initialisation paresseuse (lazy init) au sein de InventaireContract.sol (via verif bytes(prod.productRef).length == 0). Plus besoin d'invoquer specifiquement un enregistrement externe pour initialiser une reférence !
