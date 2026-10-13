package com.fortytwohz.room;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByInviteCode(String inviteCode);
    Optional<Room> findByInviteCode(String inviteCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.inviteCode = :code")
    Optional<Room> findLockedByCode(@Param("code") String code);
}
