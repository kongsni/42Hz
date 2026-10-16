
package com.fortytwohz.room;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "shared_results",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_shared_result_room",
                        columnNames = "room_id"
                ),
                @UniqueConstraint(
                        name = "uk_shared_result_token",
                        columnNames = "share_token"
                )
        }
)
public class SharedResult {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "share_token", nullable = false, length = 64)
    private String shareToken;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "different_count", nullable = false)
    private int differentCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "retention_review_required", nullable = false)
    private boolean retentionReviewRequired;
    @Column(name = "retention_review_detected_at")
    private Instant retentionReviewDetectedAt;

    protected SharedResult() {}

    public boolean isExpired(Instant now) {
        return retentionReviewRequired || createdAt == null || expiresAt == null
                || createdAt.isAfter(now)
                || !createdAt.plusSeconds(7 * 86400).equals(expiresAt)
                || !expiresAt.isAfter(now);
    }

    public SharedResult(
            Room room,
            String shareToken,
            int matchedCount,
            int differentCount,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.shareToken = shareToken;
        this.matchedCount = matchedCount;
        this.differentCount = differentCount;
        this.createdAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.expiresAt = createdAt.plusSeconds(7 * 86400);
    }

    public String getShareToken() {
        return shareToken;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public int getDifferentCount() {
        return differentCount;
    }
}
