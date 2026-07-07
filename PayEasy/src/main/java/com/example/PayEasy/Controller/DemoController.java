package com.example.PayEasy.Controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.PayEasy.Model.Account;
import com.example.PayEasy.Model.MeshPacket;
import com.example.PayEasy.Model.Transaction;
import com.example.PayEasy.Repository.AccountRepository;
import com.example.PayEasy.Repository.TransactionRepository;
import com.example.PayEasy.Service.BridgeIngestionService;
import com.example.PayEasy.Service.DemoService;
import com.example.PayEasy.Service.IdempotencyService;
import com.example.PayEasy.Service.MeshSimulatorService;

@Controller
public class DemoController {

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private MeshSimulatorService mesh;
    @Autowired
    private DemoService demo;
    @Autowired
    private BridgeIngestionService bridgeIngestion;
    @Autowired
    private IdempotencyService idempotency;

    @GetMapping("/demo")
    public String demoPage() {
        return "demo";
    }

    @GetMapping("/api/mesh/state")
    @ResponseBody
    public Map<String, Object> meshState() {
        Map<String, Object> state = new HashMap<>();
        state.put("devices", mesh.deviceStates());
        state.put("idempotencyCacheSize", idempotency.cacheSize());
        state.put("accounts", accounts.findAll());
        return state;
    }

    @PostMapping("/api/demo/send")
    @ResponseBody
    public Map<String, Object> sendPacket(@RequestBody SendRequest request) throws Exception {
        DemoService.SendResult result = demo.sendAndInject(
                request.senderVpa(),
                request.receiverVpa(),
                BigDecimal.valueOf(request.amount()),
                request.pin(),
                request.ttl() > 0 ? request.ttl() : 5,
                request.startDevice());

        MeshPacket packet = result.packet();
        mesh.registerPacket(
                packet.getPacketId(),
                request.senderVpa(),
                request.receiverVpa(),
                BigDecimal.valueOf(request.amount()));
        mesh.inject(result.deviceId(), packet);

        String preview = packet.getCipherText();
        if (preview.length() > 48) {
            preview = preview.substring(0, 48) + "...";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("packetId", packet.getPacketId());
        body.put("shortId", packet.getPacketId().substring(0, 8));
        body.put("injectedAt", result.deviceId());
        body.put("senderVpa", request.senderVpa());
        body.put("receiverVpa", request.receiverVpa());
        body.put("amount", request.amount());
        body.put("ttl", packet.getTtl());
        body.put("ciphertextPreview", preview);
        return body;
    }

    @PostMapping("/api/mesh/gossip")
    @ResponseBody
    public MeshSimulatorService.GossipResult gossip() {
        return mesh.gossipOnce();
    }

    @PostMapping("/api/mesh/flush")
    @ResponseBody
    public FlushResult flushBridges() {
        List<MeshSimulatorService.BridgeUpload> uploads = mesh.collectBridgeUploads();
        List<UploadResult> results = new ArrayList<>();
        for (MeshSimulatorService.BridgeUpload upload : uploads) {
            UploadResult result = processUpload(upload);
            results.add(result);
            // Remove the packet from the bridge device after processing,
            // regardless of outcome (settled, duplicate, or invalid).
            // This prevents the same packet from being re-flushed.
            mesh.removePacketFromDevice(upload.bridgeNodeId(), upload.packet().getPacketId());
        }
        return new FlushResult(uploads.size(), results);
    }

    @PostMapping("/api/mesh/reset")
    @ResponseBody
    public Map<String, String> resetMesh() {
        mesh.resetMesh();
        idempotency.clearCache();
        // Delete all transactions so the ledger is clean
        transactions.deleteAll();
        // Reset account balances to seed values
        accounts.findById("heru@pay").ifPresent(a -> { a.setBalance(new BigDecimal("5000.00")); accounts.save(a); });
        accounts.findById("sheru@pay").ifPresent(a -> { a.setBalance(new BigDecimal("1000.00")); accounts.save(a); });
        accounts.findById("tera@pay").ifPresent(a -> { a.setBalance(new BigDecimal("2500.00")); accounts.save(a); });
        accounts.findById("bhaiya@pay").ifPresent(a -> { a.setBalance(new BigDecimal("500.00")); accounts.save(a); });
        Map<String, String> response = new HashMap<>();
        response.put("status", "reset");
        return response;
    }

    @GetMapping("/api/accounts")
    @ResponseBody
    public List<Account> listAccounts() {
        return accounts.findAll();
    }

    @GetMapping("/api/transactions")
    @ResponseBody
    public List<Transaction> listTransactions() {
        return transactions.findTop20ByOrderByIdDesc();
    }

    private UploadResult processUpload(MeshSimulatorService.BridgeUpload upload) {
        String packetId = upload.packet().getPacketId();
        String shortId = packetId.length() > 8 ? packetId.substring(0, 8) : packetId;
        MeshSimulatorService.PacketMeta meta = mesh.packetMeta(packetId);
        String sender = meta != null ? meta.senderVpa() : "?";
        String receiver = meta != null ? meta.receiverVpa() : "?";
        double amount = meta != null ? meta.amount().doubleValue() : 0;

        try {
            int hops = Math.max(0, 5 - upload.packet().getTtl());
            Transaction tx = bridgeIngestion.ingest(upload.packet(), upload.bridgeNodeId(), hops);
            return new UploadResult(
                    upload.bridgeNodeId(), shortId, tx.getStatus().name(), null,
                    sender, receiver, amount, tx.getId());
        } catch (IllegalStateException e) {
            return new UploadResult(
                    upload.bridgeNodeId(), shortId, "DUPLICATE_DROPPED", e.getMessage(),
                    sender, receiver, amount, null);
        } catch (IllegalArgumentException e) {
            return new UploadResult(
                    upload.bridgeNodeId(), shortId, "INVALID", e.getMessage(),
                    sender, receiver, amount, null);
        } catch (Exception e) {
            return new UploadResult(
                    upload.bridgeNodeId(), shortId, "INVALID", e.getMessage(),
                    sender, receiver, amount, null);
        }
    }

    public record SendRequest(
            String senderVpa,
            String receiverVpa,
            double amount,
            String pin,
            int ttl,
            String startDevice) {
    }

    public record UploadResult(
            String bridgeNode,
            String packetId,
            String outcome,
            String reason,
            String senderVpa,
            String receiverVpa,
            double amount,
            Long transactionId) {
    }

    public record FlushResult(int uploadsAttempted, List<UploadResult> results) {
    }
}
