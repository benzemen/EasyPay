package com.example.PayEasy.Model;

import java.math.BigDecimal;

/*
Bilkul! Agar MeshPacket ek locked lifafa (envelope) tha jo network mein ghoom raha tha, toh PaymentInstruction us lifafe ke andar rakhi hui asli chiththi (letter) hai.

Jab backend server MeshPacket ke ciphertext ko successfully decrypt kar leta hai, toh usko JSON format mein yahi PaymentInstruction ka data milta hai.

Aaiye isko step-by-step samajhte hain.

Is Class Mein Kya Hai? (The Fields)
Isme basic payment details ke sath-sath do bohot critical security features hain:

senderVpa & receiverVpa: Paise bhejney wale aur receive karne wale ki UPI ID (jaise alice@okhdfcbank).

amount: Kitne paise bheje ja rahe hain (₹500). BigDecimal use kiya gaya hai kyunki financial calculations mein float/double use karne se precision (decimals) ki errors aati hain.

pinHash: Sender ka UPI PIN. Real world mein server is PIN hash ko bank ke database se match karta hai transaction approve karne se pehle.

nonce (Number used ONCE): Yeh ek random, unique UUID hota hai jo har payment ke liye alag banta hai.

signedAt: Woh exact time (milliseconds mein) jab sender ne apne phone par "Pay" dabaya tha aur yeh packet encrypt hua tha.

Is Class Ko Alag Se Kyu Banana Pada?
Aap soch sakte hain ki "Yeh saari details direct MeshPacket mein kyu nahi daal di?" Iske 3 main reasons hain:

1. Data Hiding (Security Boundary)
Agar yeh details MeshPacket mein hoti, toh mesh network mein jo bhi anjaan log is packet ko forward kar rahe hain, woh dekh lete ki Alice Bob ko ₹500 bhej rahi hai. PaymentInstruction ko alag rakhne se hum is pure object ko JSON mein convert karte hain, usko AES-GCM se encrypt karte hain, aur phir us cipher (locked data) ko MeshPacket mein daalte hain.

2. The nonce Magic (Duplicate vs. Identical Transactions)
Maan lijiye Alice ko Bob ko ₹100 bhejney hain. Usne bheje. Thodi der baad usne wapas Bob ko ₹100 aur bheje.

Dono baari sender, receiver, amount aur pin same hain.

Agar hum seedha encrypt karte, toh dono transactions ka ciphertext exact same banta. Server ko lagta ki kisi device ne ek hi packet ko galti se 2 baar bhej diya hai (duplicate) aur woh dusre ₹100 reject kar deta.

nonce isko solve karta hai: Har baar jab aap pay karte hain, ek naya random nonce generate hota hai. Is choti si unique value ki wajah se, do identical payments ka final encrypted ciphertext bilkul alag banta hai, aur server dono ko process karta hai.

3. Freshness & Replay Attack Prevention (signedAt)
Maan lijiye kisi hacker ne Alice ka aaj ka ₹500 ka encrypted packet record kar liya. 6 mahine baad woh usi encrypted packet ko wapas server par bhej deta hai. Kyunki cryptography sahi hai, server usko decrypt kar lega.

Yahan signedAt kaam aata hai. Server decrypt karne ke baad check karega: "Yeh packet kab bana tha?"

Agar signedAt 24 ghante se purana hai (stale/purana packet), toh server usko reject kar dega. Ise "Replay Attack" se bachna kehte hain. Yeh time packet ke andar hona zaroori hai, taaki hacker use change na kar sake (agar change karega toh encryption toot jayega).

Short Summary: MeshPacket sirf rasta tay karne (routing) ke liye hai, aur PaymentInstruction transaction ko execute aur secure karne ke liye. Server pehle outer lock (MeshPacket) check karta hai, phir usko khol kar inner data (PaymentInstruction) padhta hai aur account se paise kat-ta hai.
*/

public class PaymentInstruction {

    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String pinHash;
    private String nonce;  //  UUID Har baar jab aap pay karte hain, ek naya random nonce generate hota hai. Is choti si unique value ki wajah se, do identical payments ka final encrypted ciphertext bilkul alag banta hai, aur server dono ko process karta hai
    private Long signedAt; 
    
    public PaymentInstruction() {}
    public PaymentInstruction(String senderVpa, String receiverVpa, BigDecimal amount, String pinHash, String nonce, Long signedAt) {
        this.senderVpa = senderVpa;
        this.receiverVpa = receiverVpa;
        this.amount = amount;
        this.pinHash = pinHash;
        this.nonce = nonce;
        this.signedAt = signedAt;
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
    public String getPinHash() {
        return pinHash;
    }
    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }
    public String getNonce() {
        return nonce;
    }
    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
    public Long getSignedAt() {
        return signedAt;
    }
    public void setSignedAt(Long signedAt) {
        this.signedAt = signedAt;
    }
    
}
