package com.inventaire.tools;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

/**
 * Small helper to check whether the deployed Remix contract matches what the app expects.
 */
public final class EthereumContractVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(EthereumContractVerifier.class);

    private EthereumContractVerifier() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            run(args);
        } catch (Exception e) {
            exitCode = 1;
            LOG.error("Verification failed: {}", e.getMessage());
            System.err.println("Verification failed: " + e.getMessage());
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void run(String[] args) throws Exception {
        Dotenv dotenv = loadDotenv();

        String rpcUrl = dotenv.get("ETH_NODE_URL");
        String privateKey = dotenv.get("WALLET_PRIVATE_KEY");
        String contractAddress = dotenv.get("CONTRACT_ADDRESS");

        if (rpcUrl == null || rpcUrl.isBlank()) {
            throw new IllegalStateException("ETH_NODE_URL is missing in .env");
        }
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("WALLET_PRIVATE_KEY is missing in .env");
        }
        if (contractAddress == null || contractAddress.isBlank()) {
            throw new IllegalStateException("CONTRACT_ADDRESS is missing in .env");
        }

        String txId = args.length > 0 ? args[0] : "TEST-TX-001";
        String productRef = args.length > 1 ? args[1] : "PROD-TEST";
        String txType = args.length > 2 ? args[2] : "ENTREE";
        int quantite = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        String userEmail = args.length > 4 ? args[4] : "test@local";

        Credentials credentials = Credentials.create(privateKey);
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));

        EthChainId chainIdResponse = web3j.ethChainId().send();
        long chainId = chainIdResponse.getChainId().longValue();
        System.out.println("Chain ID: " + chainId);
        if (chainId != 11155111L) {
            System.out.println("Warning: expected Sepolia (11155111), got " + chainId);
        }

        EthGetCode codeResponse = web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST).send();
        String code = codeResponse.getCode();
        if (code == null || "0x".equals(code)) {
            throw new IllegalStateException("No contract code found at address: " + contractAddress);
        }
        System.out.println("Contract code found at address: " + contractAddress);

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

        String encoded = FunctionEncoder.encode(function);
        System.out.println("Encoded recordTransaction data: " + encoded);

        Transaction callTx = Transaction.createEthCallTransaction(
            credentials.getAddress(),
            contractAddress,
            encoded
        );

        EthCall ethCall = web3j.ethCall(callTx, DefaultBlockParameterName.LATEST).send();
        if (ethCall.isReverted()) {
            String reason = ethCall.getRevertReason();
            throw new IllegalStateException(
                "Contract call reverted. Revert reason: " + (reason == null ? "unknown" : reason)
            );
        }

        System.out.println("eth_call success. Contract/function signature looks valid.");
        System.out.println("From: " + credentials.getAddress());
        System.out.println("To:   " + contractAddress);
        System.out.println("If Remix deployment is correct, the live sendTransaction should also work.");
    }

    private static Dotenv loadDotenv() {
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
            ".env not found. Put it in the project root or a parent folder of the launch directory."
        );
    }
}