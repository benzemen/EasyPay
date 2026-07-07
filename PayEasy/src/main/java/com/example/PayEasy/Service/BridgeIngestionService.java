package com.example.PayEasy.Service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.PayEasy.Model.MeshPacket;
import com.example.PayEasy.Model.PaymentInstruction;
import com.example.PayEasy.Model.Transaction;
import com.example.PayEasy.cryptoService.HybridCryptoService;

@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);
    private static final long MAX_PACKET_AGE_MS = 24 * 60 * 60 * 1000L;

    @Autowired
    private HybridCryptoService crypto;
    @Autowired
    private IdempotencyService idempotency;
    @Autowired
    private SettlementService settlement;

    public Transaction ingest(MeshPacket packet, String bridgeNodeId, int hopCount) throws Exception {
        String packetHash = crypto.hashCiphertext(packet.getCipherText());

        if (idempotency.alreadyProcessed(packetHash)) {
            log.warn("Duplicate packet rejected (hash={})", packetHash.substring(0, 12) + "...");
            throw new IllegalStateException("Packet already processed");
        }

        PaymentInstruction instruction = crypto.decrypt(packet.getCipherText());

        long ageMs = Instant.now().toEpochMilli() - instruction.getSignedAt();
        if (ageMs > MAX_PACKET_AGE_MS) {
            throw new IllegalArgumentException("Packet is stale (signedAt too old)");
        }

        Transaction transaction = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
        idempotency.markProcessed(packetHash);
        return transaction;
    }
}
