package com.fortytwohz.room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AnswerRepository extends JpaRepository<Answer, UUID> {
    Optional<Answer> findByParticipantAndQuestionIndex(Participant participant, int questionIndex);
    List<Answer> findByRoomAndQuestionIndex(Room room, int questionIndex);
    List<Answer> findByRoomOrderByQuestionIndexAsc(Room room);
}
