package com.iwomi.hub.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iwomi.hub.domain.Enums.*;

@Entity
@Table(name = "ticket_message")
@Getter
@Setter
@NoArgsConstructor
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;
    private String author;
    private String authorRole;
    @Column(nullable = false)
    private String body;
    @Column(name = "is_internal", nullable = false)
    private boolean internal;
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
