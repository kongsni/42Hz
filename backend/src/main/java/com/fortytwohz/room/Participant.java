package com.fortytwohz.room;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "participants")
public class Participant {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @Column(nullable = false, length = 16)
    private String nickname;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    public UUID getId() { return id; }
    public String getNickname() { return nickname; }
    public String getTokenHash() { return tokenHash; }
    protected Participant() {}
    public Participant(Room room, String nickname, String tokenHash) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.nickname = nickname;
        this.tokenHash = tokenHash;
    }
}
