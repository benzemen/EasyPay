package com.example.PayEasy.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.PayEasy.Model.MeshPacket;

public class VirtualDevice {

    private final String deviceId;
    private final boolean internet;
    private final Map<String, MeshPacket> heldPackets = new ConcurrentHashMap<>();

    public VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.internet = hasInternet;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean hasInternet() {
        return internet;
    }

    public void hold(MeshPacket packet) {
        heldPackets.put(packet.getPacketId(), packet);
    }

    public boolean holds(String packetId) {
        return heldPackets.containsKey(packetId);
    }

    public List<MeshPacket> getHeldPackets() {
        return new ArrayList<>(heldPackets.values());
    }

    public int packetCount() {
        return heldPackets.size();
    }

    public void removePacket(String packetId) {
        heldPackets.remove(packetId);
    }

    public void clear() {
        heldPackets.clear();
    }
}
