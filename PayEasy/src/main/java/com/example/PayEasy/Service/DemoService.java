package com.example.PayEasy.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.PayEasy.Model.Account;
import com.example.PayEasy.Model.MeshPacket;
import com.example.PayEasy.Model.PaymentInstruction;
import com.example.PayEasy.Repository.AccountRepository;
import com.example.PayEasy.cryptoService.HybridCryptoService;
import com.example.PayEasy.cryptoService.ServerKeyHolder;

import com.google.common.hash.Hashing;

import jakarta.annotation.PostConstruct;

// Yeh DemoService class ek Spring Boot service hai jo hamare "PayEasy" system ke liye do main kaam karti hai:
// Data Seeding: Application shuru hote hi demo accounts banana.
// Packet Simulation: Yeh dikhana ki ek "Sender" ka phone kaise payment data ko encrypt karke server ko bhejne ke liye taiyar karta hai.

@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private HybridCryptoService crypto;
    @Autowired
    private ServerKeyHolder serverKey;

    // 1. seedAccounts() Method (@PostConstruct)
    // Yeh method server start hote hi automatic run hota hai.

    // Kaam: Yeh check karta hai ki database mein pehle se accounts hain ya nahi.
    // Agar database khaali hai, toh yeh 4 dummy users (Alice, Bob, Carol, Dave)
    // banakar unhe kuch default balance ke saath save kar deta hai.

    // Purpose: Testing ke liye ek ready-made environment taiyar karna.
    @PostConstruct
    public void seedAccounts() {
        if (accounts.count() == 0) {
            accounts.save(new Account("heru@pay", "heru", new BigDecimal("5000.00")));
            accounts.save(new Account("sheru@pay", "sheru", new BigDecimal("1000.00")));
            accounts.save(new Account("tera@pay", "tera", new BigDecimal("2500.00")));
            accounts.save(new Account("bhaiya@pay", "bhaiya", new BigDecimal("500.00")));
            log.info("Seeded 4 demo accounts");
        }
    }
    // 2. createPacket() Method
    // Yeh is class ka sabse important hissa hai.
    // Yeh simulate karta hai ki ek user ka phone (Android app) backend ko
    // transaction bhejne ke liye kya karta hai. Iske steps hain:

    // PaymentInstruction banana: Sabse pehle ek object banta hai jisme
    // sender/receiver ki details, amount, PIN ka hash (security ke liye), aur do
    // cheezein hoti hain:

    // Nonce: UUID.randomUUID() ka use karke ek unique ID banayi jaati hai taaki
    // agar koi ek hi transaction do baar bheje (Replay Attack), toh system use
    // pehchan sake.

    // Timestamp: Instant.now() se transaction ka time note hota hai taaki
    // "Freshness Check" ho sake (purane transactions ko reject karne ke liye).

    // Encryption (Hybrid Crypto): Phir crypto.encrypt() call hota hai. Yeh Hybrid
    // Encryption ka use karta hai:

    // Data ko AES (Symmetric) se encrypt kiya jata hai.

    // AES ki key ko RSA (Asymmetric) ke Server Public Key se encrypt kiya jata hai.

    // Isse data safe rehta hai, sirf jiske paas Server Private Key hai, wahi ise
    // padh sakta hai.

    // MeshPacket Packaging: End mein, encrypted ciphertext ko ek MeshPacket wrapper
    // mein daala jata hai jisme packet ki apni ID aur TTL (Time-To-Live) hota hai.
    // TTL batata hai ki yeh packet kitne jumps ya kitne time tak valid rahega.
    public MeshPacket createPacket(String senderVpa, String recevierVpa, BigDecimal amount, String pin, int ttl)
            throws Exception {
        PaymentInstruction instruction = new PaymentInstruction(
                senderVpa,
                recevierVpa,
                amount,
                sha256Hex(pin),
                UUID.randomUUID().toString(), // nonce -- gurantes the uniqueness
                Instant.now().toEpochMilli() // signedAt -- for freshness
        );

        String cipherText = crypto.encrypt(instruction, serverKey.getPublicKey());
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCipherText(cipherText);
        return packet;

    }

    // private String sha256Hex(String input) throws Exception{
    // MessageDigest md=MessageDigest.getInstance("SHA-256");
    // byte[] hash=md.digest(input.getBytes());
    // StringBuilder hex=new StringBuilder();
    // for(byte b:hash) hex.append(String.format("%02x", b));
    // return hex.toString();
    // }
    private String sha256Hex(String input) {
        return Hashing.sha256()
                .hashString(input, StandardCharsets.UTF_8)
                .toString();
    }

    public String deviceIdForVpa(String vpa) {
        String local = vpa.contains("@") ? vpa.substring(0, vpa.indexOf('@')) : vpa;
        return "phone-" + local;
    }

    public SendResult sendAndInject(String senderVpa, String receiverVpa, BigDecimal amount, String pin, int ttl,
            String startDevice) throws Exception {
        MeshPacket packet = createPacket(senderVpa, receiverVpa, amount, pin, ttl);
        String deviceId = startDevice != null && !startDevice.isBlank()
                ? startDevice
                : deviceIdForVpa(senderVpa);
        return new SendResult(packet, deviceId);
    }

    public record SendResult(MeshPacket packet, String deviceId) {
    }

}
