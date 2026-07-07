package com.example.PayEasy.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.PayEasy.Repository.TransactionRepository;

@Service
public class IdempotencyService {

    private final Set<String> sessionCache = ConcurrentHashMap.newKeySet();

    @Autowired
    private TransactionRepository transactions;

    public boolean alreadyProcessed(String packetHash) {
        if (sessionCache.contains(packetHash)) {
            return true;
        }
        if (transactions.existsByPacketHash(packetHash)) {
            sessionCache.add(packetHash);
            return true;
        }
        return false;
    }

    public void markProcessed(String packetHash) {
        sessionCache.add(packetHash);
    }

    public int cacheSize() {
        return sessionCache.size();
    }

    public void clearCache() {
        sessionCache.clear();
    }
}
