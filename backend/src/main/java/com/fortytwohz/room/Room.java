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

    @Column(name = "sharing_blocked", nullable = false)
    private boolean sharingBlocked;
    @Column(name = "retention_review_required", nullable = false)
    private boolean retentionReviewRequired;
    @Column(name = "retention_review_detected_at")
    private Instant retentionReviewDetectedAt;

    protected Room() {}
    public Room(String inviteCode, Instant now) {
        this.id = UUID.randomUUID();
        this.inviteCode = inviteCode;
        this.status = RoomStatus.WAITING;
        this.createdAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.expiresAt = createdAt.plusSeconds(24 * 60 * 60);
    }
    public boolean isExpired(Instant now) {
        return retentionReviewRequired || createdAt == null || expiresAt == null
                || createdAt.isAfter(now) || !createdAt.plusSeconds(86400).equals(expiresAt)
                || !expiresAt.isAfter(now);
    }
    public boolean isSharingBlocked() { return sharingBlocked; }
    public void blockSharing() { sharingBlocked = true; }
    public void markReady() { this.status = RoomStatus.READY; }
    public UUID getId() { return id; }
    public String getInviteCode() { return inviteCode; }
    public RoomStatus getStatus() { return status; }
}
