package com.example.PayEasy.cryptoService;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.PayEasy.Model.PaymentInstruction;
import com.fasterxml.jackson.databind.ObjectMapper;

// Public Key aur Private Key ka Asli Matlab
// RSA algorithm mai do keys ka ek joda (pair) hota hai:

// Public Key (Khula Hua Taala 🔓): 
// Ye naam ki tarah "Public" hoti hai. 
// Ye server sabhi mobile phones ko baant deta hai. 
// Iska kaam sirf Lock (Encrypt) karna hota hai, ye unlock nahi kar sakti.

// Private Key (Akeli Chaabi 🔑): 
// Ye sirf aur sirf Server ke paas hoti hai, duniya mai kisi aur ke paas nahi. 
// Iska kaam sirf Unlock (Decrypt) karna hota hai.

// Core Concept:
//RSA (Asymmetric): 
// Bahut secure hai (isme Public aur Private key hoti hai). 
// Lekin iski ek badi problem hai—yeh bahut slow hota hai aur ek baar mein sirf bahut chota data (jaise 245 bytes) hi encrypt kar sakta hai. 
// Aapki payment JSON file isse badi ho sakti hai.

//AES (Symmetric): 
// Yeh bahut fast hota hai aur kitna bhi bada data encrypt kar sakta hai. 
// Main Reason to use RSA :- Lekin isme problem yeh hai ki sender aur receiver dono ke paas same key honi chahiye. 
// Agar aap wo key internet par bhejenge, toh koi bhi usko chura kar data padh lega.

//  Yeh system AES (Symmetric) aur RSA (Asymmetric) encryption ko mila kar banta hai taaki bade data ko fast aur tamper-proof tarike se transfer kiya ja sake.

// Sender Side (Locking): 
// User ka mobile phone asli payment data ko ek fresh, temporary AES Key se lock karta hai (kyunki AES bade data ke liye fast hai). Phir us AES Key ko server ki Public Key (Khula Taala 🔓) se lock kar deta hai. In dono ko ek sath combine karke Base64 format mai mesh network mai bhej diya jata hai.

// Mesh Network (Transit): 
// Raste ke temporary 'Stranger' phones sirf postman ka kaam karte hain. 
// RSA ke rule ke mutabiq, Public Key se lock huye data ko koi dusri public key nahi khol sakti. Unke paas server ki Private Key nahi hoti, isiliye data raste mai 100% safe rehta hai.

// Server Side (Unlocking): 
// Jab packet server ke paas pahunchta hai, toh server apni safe tijori se Private Key (Akeli Chaabi 🔑) nikaalta hai aur RSA lock ko khol kar AES Key bahar nikaalta hai. Phir us AES Key se asli payment data (AES-GCM mode) ko unlock kiya jata hai. 
// Agar raste mai kisi ne 1 bit ka bhi badlav kiya hoga, toh GCM authentication tag fail ho jayega aur server use turant reject kar dega.

@Service
public class HybridCryptoService {
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BITS = 256;

    // Wire format (after base64 encoding):
    // [ 256 bytes RSA-encrypted AES key ][ 12 bytes GCM IV ][ ciphertext + 16-byte
    // tag ]
    private static final int GCM_IV_BITS = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int RSA_ENCRYPTED_KEY_BYTES = 256;

    private final SecureRandom rng = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private ServerKeyHolder serverKey;

    // now here we do Encrpting the paymentinstruction in which we will first
    // encrypt the instruction with AES-GCM then we will encrypt the AES key with
    // RSA-OAEP kyuki aes mai key ko bhejna padha hai server tak us encrypted data
    // to padhne ke liye

    public String encrypt(PaymentInstruction instruction, PublicKey serverPublicKey) throws Exception {
        byte[] plaintext = json.writeValueAsBytes(instruction);

        // 1. generate a one-time AES key for this packet

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS);
        SecretKey aesKey = kg.generateKey();

        // 2. AES_GCM instruction(aur ye instruction hamne plaintext mai as a byte array
        // mai conver kar diya hai ) ko encrpyt karthe hai

        byte[] IV = new byte[GCM_IV_BITS];
        rng.nextBytes(IV);
        Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, IV));
        byte[] aesCiphertext = aes.doFinal(plaintext);

        // 3. now encrpt the AES_KEY_Using serverPublicKey

        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.ENCRYPT_MODE, serverPublicKey, oaep);
        byte[] encryptedAesKey = rsa.doFinal(aesKey.getEncoded());

        // 4. Pack: [encrypted AES key][IV][AES ciphertext + tag]
        ByteBuffer buf = ByteBuffer.allocate(encryptedAesKey.length + IV.length + aesCiphertext.length);
        buf.put(encryptedAesKey);
        buf.put(IV);
        buf.put(aesCiphertext);
        return Base64.getEncoder().encodeToString(buf.array());

    }

    public PaymentInstruction decrypt(String base64CipherText) throws Exception {

        byte[] all = Base64.getDecoder().decode(base64CipherText);
        // check karne ke liye kya code ko encrypt karne ke baad koi chera toh nhi na
        if (all.length < RSA_ENCRYPTED_KEY_BYTES + GCM_IV_BITS + GCM_TAG_BITS / 8) {
            throw new IllegalArgumentException("Ciphertext too short");

        }

        byte[] encryptedAesKey = new byte[RSA_ENCRYPTED_KEY_BYTES];
        byte[] IV = new byte[GCM_IV_BITS];
        byte[] aesCiphertext = new byte[all.length - RSA_ENCRYPTED_KEY_BYTES - GCM_IV_BITS];

        ByteBuffer buf = ByteBuffer.wrap(all);
        buf.get(encryptedAesKey);
        buf.get(IV);
        buf.get(aesCiphertext);

        // 1. RSA ko decrypt karo
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaep = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.DECRYPT_MODE, serverKey.getPrivateKey(), oaep);
        byte[] aesKeyBytes = rsa.doFinal(encryptedAesKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        // 2. ab is aes key se aes ciphertext ko decrypt karo (GCM mode authentication
        // bhi karega)
        Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
        aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, IV));
        byte[] decryptedPlaintext = aes.doFinal(aesCiphertext);

        // 3. byte array ko wapis PaymentInstruction object mai convert karo
        return json.readValue(decryptedPlaintext, PaymentInstruction.class);
    }

    /**
     * SHA-256 of the ciphertext. THIS is the idempotency key.
     *
     * Why ciphertext and not packetId? Because intermediates can rewrite packetId
     * but cannot forge a valid ciphertext for a different payload. Two delivered
     * copies of the same packet have identical ciphertexts, hence identical hashes.
     */

    public String hashCiphertext(String base64Ciphertext) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(base64Ciphertext.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));

        }
        // single line built in function to do the same done by the loop
        // String hash1=HexFormat.of().formatHex(hash);
        return hex.toString();
    }

}
