package com.example.PayEasy.Model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "transactions", indexes = { @Index(name = "idx_packet_hash", columnList = "packetHash", unique = true), // packetHash
                                                                                                                      // pe
                                                                                                                      // unique
                                                                                                                      // index
                                                                                                                      // isliye
                                                                                                                      // hai
                                                                                                                      // taaki
                                                                                                                      // agar
                                                                                                                      // koi
                                                                                                                      // hacker
                                                                                                                      // same
                                                                                                                      // packet
                                                                                                                      // ko
                                                                                                                      // baar-baar
                                                                                                                      // bhejne
                                                                                                                      // ki
                                                                                                                      // koshish
                                                                                                                      // kare
                                                                                                                      // (replay
                                                                                                                      // attack),
                                                                                                                      // toh
                                                                                                                      // database
                                                                                                                      // usko
                                                                                                                      // reject
                                                                                                                      // kar
                                                                                                                      // dega
                                                                                                                      // kyunki
                                                                                                                      // packetHash
                                                                                                                      // already
                                                                                                                      // exist
                                                                                                                      // karega.
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64) // UUID ke liye 36 characters ka length hota hai, aur ye
                                                          // unique bhi hona chahiye taaki har transaction ka alag
                                                          // packetHash ho.
    private String packetHash; // MeshPacket ke cipherText ka hash, taaki server ko pata chale ki ye packet
                               // pehle process hua hai ya nahi. Agar same packetHash pehle se exist karta hai,
                               // toh server usko reject kar dega, isse replay attack se bachav hota hai.

    @Column(nullable = false)
    private String senderVpa;
    @Column(nullable = false)
    private String receiverVpa;

    @Column(nullable = false, precision = 19, scale = 2) // BigDecimal ke liye precision aur scale specify karna zaroori
                                                         // hai. Precision total digits ka number hai (19 isliye kiya
                                                         // hai taaki 10^17 tak ke amounts handle kar sake), aur scale
                                                         // decimal points ke baad digits ka number hai (2 isliye kiya
                                                         // hai taaki paise ke liye 2 decimal places ho).
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant signedAt;

    @Column(nullable = false)
    private Instant settledAt;

    @Column(nullable = false)
    private String bridgeNodeId; // Jis mesh node ne is transaction ko process kiya, uska unique identifier. Isse
                                 // hume pata chalega ki transaction kis node ke through gaya.

    @Column(nullable = false)
    private int hopCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status {
        SETTLED,
        REJECTED
    }

    public Transaction() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPacketHash() {
        return packetHash;
    }

    public void setPacketHash(String packetHash) {
        this.packetHash = packetHash;
    }

    public String getSenderVpa() {
        return senderVpa;
    }

    public void setSenderVpa(String senderVpa) {
        this.senderVpa = senderVpa;
    }

    public String getReceiverVpa() {
        return receiverVpa;
    }

    public void setReceiverVpa(String receiverVpa) {
        this.receiverVpa = receiverVpa;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public String getBridgeNodeId() {
        return bridgeNodeId;
    }

    public void setBridgeNodeId(String bridgeNodeId) {
        this.bridgeNodeId = bridgeNodeId;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

}
