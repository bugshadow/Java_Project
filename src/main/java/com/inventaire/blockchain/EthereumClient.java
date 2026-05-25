package com.inventaire.blockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;

public class EthereumClient {
    private static final Logger LOG = LoggerFactory.getLogger(EthereumClient.class);
    
    private static EthereumClient instance;
    private Web3j web3j;
    private Credentials credentials;
    // Replace this with your actual contract address after deploying
    private String contractAddress = "0xYOUR_CONTRACT_ADDRESS_HERE";
    // private InventaireContract contract;  // COMMENTED OUT FOR NOW until Web3j wrapper is generated

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
            // Sepolia RPC URL (e.g., from Infura or Alchemy)
            String rpcUrl = "https://sepolia.infura.io/v3/bc1cccecccb149b5ae7d9d06bde0d1cc"; // Example public key, better to use your own
            web3j = Web3j.build(new HttpService(rpcUrl));
            
            // Your MetaMask private key (Make sure account has Sepolia ETH)
            String privateKey = "YOUR_PRIVATE_KEY_HERE";
            credentials = Credentials.create(privateKey);
            
            long chainId = 11155111; // Sepolia
            TransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId);
            
            ContractGasProvider gasProvider = new StaticGasProvider(
                    BigInteger.valueOf(2000000000L), // gas price
                    BigInteger.valueOf(3000000L)     // gas limit
            );

            // The contract wrapper class will be generated from Solidity
            // contract = InventaireContract.load(contractAddress, web3j, txManager, gasProvider);

            LOG.info("✅ Connected to Ethereum Sepolia Testnet");
        } catch (Exception e) {
            LOG.error("❌ Failed to connect to Ethereum", e);
        }
    }
    
    // public InventaireContract getContract() { return contract; }
}