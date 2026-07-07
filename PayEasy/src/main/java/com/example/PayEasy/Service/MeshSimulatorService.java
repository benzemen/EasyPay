package com.example.PayEasy.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.PayEasy.Model.MeshPacket;

/**
 * Simulates the Bluetooth mesh.
 *
 * Each VirtualDevice represents a phone. The "gossip" step picks pairs of
 * devices that are nearby (we just say all devices are nearby for the demo)
 * and copies packets between them, decrementing TTL each hop.
 *
 * When a device with internet (a "bridge node") holds a packet, the demo's
 * /api/mesh/flush endpoint causes it to actually POST that packet to our
 * backend — simulating the moment a phone walks outside and gets 4G.
 */

@Service
public class MeshSimulatorService {
    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorService.class);

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();
    private final Map<String, PacketMeta> packetRegistry = new ConcurrentHashMap<>();

    // 1. SIMULATOR KE LIYE “PHONES” BANATE HAIN
    public MeshSimulatorService() {
        // Default scenario: 4 offline phones in a basement, 1 phone outside with 4G
        seedDefaultDevices();

    }

    public void seedDefaultDevices() {
        devices.put("phone-heru", new VirtualDevice("phone-heru", false));
        devices.put("phone-sheru", new VirtualDevice("phone-sheru", false));
        devices.put("phone-tera", new VirtualDevice("phone-tera", false));
        devices.put("phone-bhaiya", new VirtualDevice("phone-bhaiya", false));
        devices.put("phone-bridge", new VirtualDevice("phone-bridge", true));

    }

    public Collection<VirtualDevice> getDevices() {
        return devices.values();
    }

    public VirtualDevice getDevice(String id) {
        return devices.get(id);
    }

    /**
     * Sender drops a packet into the mesh by handing it to their own device.
     */
    public void inject(String senderDeviceId, MeshPacket packet) {
        VirtualDevice sender = devices.get(senderDeviceId);
        if (sender == null)
            throw new IllegalArgumentException("Unknown device: " + senderDeviceId);
        sender.hold(packet);
        log.info("Packet {} injected at {}(TTL={})", packet.getPacketId().substring(0, 8), senderDeviceId,
                packet.getTtl());
    }

    public void registerPacket(String packetId, String senderVpa, String receiverVpa, BigDecimal amount) {
        packetRegistry.put(packetId, new PacketMeta(senderVpa, receiverVpa, amount));
    }

    public String vpaForDevice(String deviceId) {
        if ("phone-bridge".equals(deviceId)) {
            return "bridge@pay";
        }
        String local = deviceId.replace("phone-", "");
        return local + "@pay";
    }

    /**
     * One round of gossip. Every device shares everything it has with every
     * other device. TTL is decremented per hop; packets at TTL 0 stay where
     * they are but are not forwarded further.(kyuki ttl ek type ki limit se jisse
     * agar na lagai toh phir aisa hoga ki packet baar baar infinitely hop karta
     * rahega )
     * 
     *
     * Real BLE gossip would be pair-by-pair when devices come into range.
     * For the demo we let everyone gossip with everyone in one round, which
     * is equivalent to "fast-forward N rounds of pairwise gossip".
     * 
     */
    // Asli duniya mein, agar aap kisi aisi jagah hain jahan internet nahi hai
    // (jaise basement ya parking), aur aapke paas koi important data/packet hai,
    // toh aapka phone use Bluetooth ke zariye aas-paas ke dusre phones ko bhej deta
    // hai. Jab woh dusre log chalte-phirte kisi internet wale area mein jaate hain,
    // toh unka phone us data ko internet par upload kar deta hai. Isse Bluetooth
    // Mesh Network kehte hain.

    // Is code mein, gossipOnce() usi movement ko virtually dikha raha hai:

    // The Simulation Shortcut: Asli duniya mein dono phones ka aamne-saamne aana
    // zaroori hai. Lekin is demo ko simple rakhne ke liye, code yeh maan leta hai
    // ki saare phones ek hi kamre mein hain aur ek sath ek-doosre ke range mein
    // hain.

    // One "Round" of Gossip: gossipOnce() ko call karne ka matlab hai ki aapne time
    // ko thoda fast-forward kar diya. Ek round mein har phone apne paas maujood
    // saare packets apne aas-paas ke har dusre phone ko "gossip" (pass) kar deta
    // hai.

    public GossipResult gossipOnce() {
        int transfers = 0;
        List<GossipTransfer> transferDetails = new ArrayList<>();
        List<VirtualDevice> deviceList = new ArrayList<>(devices.values());

        // Snapshot what each device holds at the start of this round, so
        // we don't gossip the same packet through 5 devices in 1 step.
        Map<String, List<MeshPacket>> snapshot = new HashMap<>();
        for (VirtualDevice d : deviceList) {
            snapshot.put(d.getDeviceId(), new ArrayList<>(d.getHeldPackets()));
        }

        for (VirtualDevice src : deviceList) {
            for (MeshPacket pkt : snapshot.get(src.getDeviceId())) {
                if (pkt.getTtl() <= 0) {
                    continue;
                }
                for (VirtualDevice dst : deviceList) {
                    if (dst == src)
                        continue;
                    if (dst.holds(pkt.getPacketId()))
                        continue;
                    MeshPacket copy = new MeshPacket();
                    copy.setPacketId(pkt.getPacketId());
                    copy.setCipherText(pkt.getCipherText());
                    copy.setTtl(pkt.getTtl() - 1);
                    copy.setCreatedAt(pkt.getCreatedAt());
                    dst.hold(copy);
                    transfers++;
                    transferDetails.add(new GossipTransfer(
                            src.getDeviceId(),
                            dst.getDeviceId(),
                            pkt.getPacketId(),
                            copy.getTtl(),
                            metaLabel(pkt.getPacketId())));
                }
            }

        }
        log.info("Gossip round complete: {} packet transfers", transfers);
        return new GossipResult(transfers, snapshotMap(), transferDetails);
    }

    private String metaLabel(String packetId) {
        PacketMeta meta = packetRegistry.get(packetId);
        if (meta == null) {
            return packetId.substring(0, Math.min(8, packetId.length()));
        }
        return "₹" + meta.amount().stripTrailingZeros().toPlainString()
                + " " + meta.senderVpa() + "→" + meta.receiverVpa();
    }

    public Map<String, Integer> snapshotMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (VirtualDevice d : devices.values()) {
            m.put(d.getDeviceId(), d.packetCount());
        }
        return m;
    }

    public List<DeviceState> deviceStates() {
        List<DeviceState> states = new ArrayList<>();
        for (VirtualDevice d : devices.values()) {
            List<PacketView> packets = d.getHeldPackets().stream()
                    .map(this::toPacketView)
                    .toList();
            states.add(new DeviceState(
                    d.getDeviceId(),
                    vpaForDevice(d.getDeviceId()),
                    d.hasInternet(),
                    d.packetCount(),
                    packets));
        }
        return states;
    }

    private PacketView toPacketView(MeshPacket packet) {
        PacketMeta meta = packetRegistry.get(packet.getPacketId());
        String shortId = packet.getPacketId().substring(0, Math.min(8, packet.getPacketId().length()));
        if (meta == null) {
            return new PacketView(shortId, packet.getPacketId(), packet.getTtl(), "?", "?", 0);
        }
        return new PacketView(
                shortId,
                packet.getPacketId(),
                packet.getTtl(),
                meta.senderVpa(),
                meta.receiverVpa(),
                meta.amount().doubleValue());
    }

    /**
     * Returns all packets held by devices with internet — these are what would
     * be uploaded to the backend the moment they reach connectivity.
     */
    public List<BridgeUpload> collectBridgeUploads() {
        List<BridgeUpload> out = new ArrayList<>();
        for (VirtualDevice d : devices.values()) {
            if (!d.hasInternet())
                continue;
            for (MeshPacket pkt : d.getHeldPackets()) {
                out.add(new BridgeUpload(d.getDeviceId(), pkt));
            }
        }
        return out;
    }

    /**
     * Remove a specific packet from a device after it has been processed
     * (settled, rejected, or duplicate-dropped).
     */
    public void removePacketFromDevice(String deviceId, String packetId) {
        VirtualDevice d = devices.get(deviceId);
        if (d != null) {
            d.removePacket(packetId);
        }
    }

    public void resetMesh() {
        devices.values().forEach(VirtualDevice::clear);
        packetRegistry.clear();
    }

    public PacketMeta packetMeta(String packetId) {
        return packetRegistry.get(packetId);
    }

    public record PacketMeta(String senderVpa, String receiverVpa, BigDecimal amount) {
    }

    public record PacketView(String shortId, String packetId, int ttl, String senderVpa, String receiverVpa,
            double amount) {
    }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts, List<GossipTransfer> transferDetails) {
    }

    public record GossipTransfer(String from, String to, String packetId, int ttlAfter, String label) {
    }

    public record DeviceState(String deviceId, String vpa, boolean hasInternet, int packetCount,
            List<PacketView> packets) {
    }

    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {
    }

}
