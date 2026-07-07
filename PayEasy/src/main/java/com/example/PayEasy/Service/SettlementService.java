package com.example.PayEasy.Service;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.PayEasy.Model.Account;
import com.example.PayEasy.Model.PaymentInstruction;
import com.example.PayEasy.Model.Transaction;
import com.example.PayEasy.Repository.AccountRepository;
import com.example.PayEasy.Repository.TransactionRepository;

import jakarta.transaction.Transactional;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;

    @Transactional
    public Transaction settle(PaymentInstruction instruction,String packetHash,String bridgeNodeId,int hopCount){
        Account sender=accounts.findById(instruction.getSenderVpa())
        .orElseThrow(()-> new IllegalArgumentException(
            "Unknown sender VPA: "+instruction.getSenderVpa()
        ));
        Account receiver=accounts.findById(instruction.getReceiverVpa())
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown reciever VPA: "+instruction.getReceiverVpa()
        ));
        BigDecimal amount=instruction.getAmount();
        if(amount.signum()<=0){
            throw new IllegalArgumentException("Amount must be positive");
        }
        if(sender.getBalance().compareTo(amount)<0){
           log.warn("Insufficient balance:{} has ₹{}, tried to send ₹{}",sender.getVpa(),sender.getBalance(),amount);
            return recordRejected(instruction,packetHash,bridgeNodeId,hopCount);
        }
        //perform actual debits and credits
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        // now saving the whole process as a transaction taki agar koi aur same credential ka
        // use karke transaction karne ki koshish kare toh database usko reject kar dega.

        Transaction t=new Transaction();
        t.setPacketHash(packetHash);
        t.setSenderVpa(instruction.getSenderVpa());
        t.setReceiverVpa(instruction.getReceiverVpa());
        t.setAmount(amount);
        t.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        t.setSettledAt(Instant.now());
        t.setBridgeNodeId(bridgeNodeId);
        t.setHopCount(hopCount);
        t.setStatus(Transaction.Status.SETTLED);
        transactions.save(t);
        log.info("SETTLED ₹{} from {} to {} (packetHash={},bridge={},hops={})",
                amount, sender.getVpa(), receiver.getVpa(),
                packetHash.substring(0, 12) + "...", bridgeNodeId, hopCount);
        return t; 
        
        
    }

    private Transaction recordRejected(PaymentInstruction instruction, String packetHash, String bridgeNodeId,
            int hopCount) {
        Transaction t = new Transaction();
        t.setPacketHash(packetHash);
        t.setSenderVpa(instruction.getSenderVpa());
        t.setReceiverVpa(instruction.getReceiverVpa());
        t.setAmount(instruction.getAmount());
        t.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        t.setSettledAt(Instant.now());
        t.setBridgeNodeId(bridgeNodeId);
        t.setHopCount(hopCount);
        t.setStatus(Transaction.Status.REJECTED);
        return transactions.save(t);
    }
}
