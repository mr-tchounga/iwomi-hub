package com.iwomi.hub.repo;

import com.iwomi.hub.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findAllByOrderByCreatedAtDesc();
    List<Ticket> findByRequesterUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Ticket> findByReferenceIgnoreCase(String reference);
}
