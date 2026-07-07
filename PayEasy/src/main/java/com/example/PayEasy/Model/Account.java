package com.example.PayEasy.Model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private String vpa; // ye voh unique identifier hai jo har account ke liye alag hota hai aur security se liye hamne is vpa rakha hai kyuki voh sabhka unique hoga

    @Column(nullable = false) // ye batata hai ki ye column null nahi ho sakta, yani har account ke paas ek holder name hona chahiye aur ye bhi ki "accounts" table ki column mai ek entry iski bhi hogi
    private String holderName;

    @Column(nullable = false) // ye batata hai ki ye column null nahi ho sakta, yani har account ke paas ek balance hona chahiye aur ye bhi ki "accounts" table ki column mai ek entry iski bhi hogi
    private BigDecimal balance;
    
    @Version
    private Long version; // database concurrency control ke liye version field ka use hota hai, jisse multiple transactions ke beech data consistency maintain ki ja sakti hai ex 
                                // Problem: Lost Update Problem (Data Overwrite Hona)
                                // Jab do log ek hi waqt par same data read karte hain aur dono usko modify karke save karte hain, toh baad mein save karne wale ka data pehle wale ke changes ko overwrite (khatam) kar deta hai. Isse pehle user ki transaction puri tarah gayab ho jati hai.
                                // Solution: @Version (Optimistic Locking)
                                // @Version table mein ek automatic counter add kar deta hai (start with 0).
                                // Jab bhi koi data save karne jata hai, Hibernate check karta hai ki user ka version aur database ka current version match ho raha hai ya nahi.
                                // Agar match hota hai: Data successfully save ho jayega aur version +1 badh jayega.
                                // Agar match nahi hota: Iska matlab kisine already data update kar diya hai. Hibernate data overwrite karne se mana kar dega aur process rok kar Error (OptimisticLockException) de dega, taaki data safe rahe.

    public Account() {}
    public Account(String vpa, String holderName, BigDecimal balance) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.balance = balance;
    }
    public String getVpa() {
        return vpa;
    }
    public void setVpa(String vpa) {
        this.vpa = vpa;
    }
    public String getHolderName() {
        return holderName;
    }
    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }
    public BigDecimal getBalance() {
        return balance;
    }
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }


}
