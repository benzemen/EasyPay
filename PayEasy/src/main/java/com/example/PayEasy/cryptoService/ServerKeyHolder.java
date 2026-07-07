package com.example.PayEasy.cryptoService;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
//  ye class rsa ke liye public aur private key banakar rakhthi hai taki jabh aap
// server start karo toh ye keys turant dusri classes ko mil jai aur voh encryption kar sake 

/**
 * Holds the server's RSA keypair.
 *
 * In production, the private key would live in an HSM (Hardware Security
 * Module)
 * or at least a KMS like AWS KMS / HashiCorp Vault. NEVER in the JAR or source.
 *
 * For this demo we generate a fresh keypair on every startup. The public key is
 * exposed via /api/server-key so the (simulated) sender devices can use it to
 * encrypt payloads.
 */

@Component
public class ServerKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(ServerKeyHolder.class);
    private KeyPair keyPair;

    @PostConstruct // @PostConstruct ek Spring Boot (aur Jakarta) ka annotation hai. Iska asan
                   // matlab hota hai: "Object banane ke turant baad isko chalao."
    public void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
        log.info("Server RSA keypair generated (2048). Public Key fingerprint: {}",
                getPublicKeyBase64().substring(0, 31) + "...");
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    private String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

}
