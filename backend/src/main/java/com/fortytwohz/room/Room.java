package com.fortytwohz.room;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = "invite_code"))
public class Room {
    @Id
    private UUID id;
    @Column(name = "invite_code", nullable = false, length = 12)
    private String inviteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected Room() {}
    public Room(String inviteCode) {
        this.id = UUID.randomUUID();
        this.inviteCode = inviteCode;
        this.status = RoomStatus.WAITING;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(24 * 60 * 60);
    }
    public boolean isExpired() { return !expiresAt.isAfter(Instant.now()); }
    public void markReady() { this.status = RoomStatus.READY; }
    public UUID getId() { return id; }
    public String getInviteCode() { return inviteCode; }
    public RoomStatus getStatus() { return status; }
}
