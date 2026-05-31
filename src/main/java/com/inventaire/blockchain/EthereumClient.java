package com.inventaire.blockchain;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

public class EthereumClient {
    private static final Logger LOG = LoggerFactory.getLogger(EthereumClient.class);
    
    private static EthereumClient instance;
    private Web3j web3j;
    private Credentials credentials;
    private TransactionManager txManager;
    private String contractAddress;

    private EthereumClient() {
        init();
    }

    public static EthereumClient getInstance() {
        if (instance == null) {
            instance = new EthereumClient();
        }
        return instance;
    }

    private void init() {
        try {
            Dotenv dotenv = loadDotenv();
            
            String rpcUrl = dotenv.get("ETH_NODE_URL");
            String privateKey = dotenv.get("WALLET_PRIVATE_KEY");
            this.contractAddress = dotenv.get("CONTRACT_ADDRESS");

            if(rpcUrl == null || privateKey == null || contractAddress == null) {
                LOG.error("Configuration Ethereum manquante dans le fichier .env !");
                return;
            }

            // Init Web3j
            web3j = Web3j.build(new HttpService(rpcUrl));
            credentials = Credentials.create(privateKey);
            
            long chainId = 11155111; // Sepolia Testnet ID
            txManager = new RawTransactionManager(web3j, credentials, chainId);

            LOG.info("Connecte a Ethereum Sepolia Testnet avec succes.");
        } catch (Exception e) {
            LOG.error("Erreur de connexion a Ethereum: " + e.getMessage());
        }
    }

    private Dotenv loadDotenv() {
        Path currentDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        while (currentDir != null) {
            Path dotenvPath = currentDir.resolve(".env");
            if (Files.exists(dotenvPath)) {
                LOG.info("Using .env from: {}", dotenvPath);
                return Dotenv.configure()
                    .directory(currentDir.toString())
                    .ignoreIfMissing()
                    .load();
            }

            Path moduleDir = currentDir.resolve("inventaire-blockchain");
            Path moduleDotenv = moduleDir.resolve(".env");
            if (Files.exists(moduleDotenv)) {
                LOG.info("Using .env from: {}", moduleDotenv);
                return Dotenv.configure()
                    .directory(moduleDir.toString())
                    .ignoreIfMissing()
                    .load();
            }

            currentDir = currentDir.getParent();
        }

        throw new IllegalStateException(
            ".env not found. Put it in the project root or the inventaire-blockchain module folder."
        );
    }

    /**
     * Envoie la transaction directement sur Sepolia sans wrapper genere.
     */
    public String validerTransactionSurReseau(String txId, String productRef, String txType, int quantite, String userEmail) throws Exception {
        if(web3j == null || credentials == null) {
            throw new Exception("Le client Ethereum n'est pas configure ou connecte.");
        }

        LOG.info("Envoi de la transaction {} sur Sepolia...", txId);

        // Definition de la fonction 'recordTransaction' du Smart Contract (ABI)
        Function function = new Function(
            "recordTransaction", 
            Arrays.asList(
                new Utf8String(txId), 
                new Utf8String(productRef), 
                new Utf8String(txType), 
                new Uint256(BigInteger.valueOf(quantite)), 
                new Utf8String(userEmail)
            ), 
            Collections.emptyList()
        );

        // Encodage des donnees pour l'EVM
        String encodedFunction = FunctionEncoder.encode(function);

        // Limites de gas standards pour un testnet
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(3000000L);

        // Envoyer la transaction !
        EthSendTransaction ethSendTx = txManager.sendTransaction(
            gasPrice,
            gasLimit,
            contractAddress,
            encodedFunction,
            BigInteger.ZERO
        );

        if (ethSendTx.hasError()) {
            throw new Exception("Erreur EVM: " + ethSendTx.getError().getMessage());
        }

        String transactionHash = ethSendTx.getTransactionHash();
        LOG.info("Transaction envoyee au reseau ! TX Hash: {}", transactionHash);
        
        return transactionHash;
    }
}