package com.example.PayEasy.Model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MeshPacket {

    @NotBlank
    private String packetId;
    @Min(0)
    private int ttl;
    @NotNull
    private Long createdAt;
    @NotBlank
    private String cipherText;
    public MeshPacket() {}

    public String getPacketId() {
        return packetId;
    }
    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }
    public int getTtl() {
        return ttl;
    }
    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
    public Long getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
    public String getCipherText() {
        return cipherText;
    }
    public void setCipherText(String cipherText) {
        this.cipherText = cipherText;
    }
    
    
    
}
