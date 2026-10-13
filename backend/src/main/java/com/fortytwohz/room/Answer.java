package com.fortytwohz.room;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answers", uniqueConstraints = @UniqueConstraint(columnNames = {"participant_id", "question_index"}))
public class Answer {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false) private Room room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "participant_id", nullable = false) private Participant participant;
    @Column(name = "question_index", nullable = false) private int questionIndex;
    @Column(nullable = false, length = 1000) private String answer;
    @Column(name = "submitted_at", nullable = false) private Instant submittedAt;
    protected Answer() {}
    public Answer(Room room, Participant participant, int questionIndex, String answer) {
        this.id = UUID.randomUUID(); this.room = room; this.participant = participant;
        this.questionIndex = questionIndex; this.answer = answer; this.submittedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public Participant getParticipant() { return participant; }
    public int getQuestionIndex() { return questionIndex; }
    public String getAnswer() { return answer; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void update(String value) { this.answer = value; this.submittedAt = Instant.now(); }
}
