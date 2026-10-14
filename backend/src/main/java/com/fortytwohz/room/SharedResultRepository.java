
package com.fortytwohz.room;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SharedResultRepository
        extends JpaRepository<SharedResult, UUID> {

    Optional<SharedResult> findByRoom(Room room);

    Optional<SharedResult> findByShareToken(String shareToken);
}
