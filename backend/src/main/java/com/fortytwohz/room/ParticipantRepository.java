package com.fortytwohz.room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
    long countByRoom(Room room);
    List<Participant> findByRoomOrderByNicknameAsc(Room room);
    Optional<Participant> findByRoomAndTokenHash(Room room, String tokenHash);
}
