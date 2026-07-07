package com.example.PayEasy.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.PayEasy.Model.Transaction;

public interface TransactionRepository extends JpaRepository<com.example.PayEasy.Model.Transaction, Long> {
    List<Transaction> findTop20ByOrderByIdDesc();
    boolean existsByPacketHash(String packetHash);
    
}
