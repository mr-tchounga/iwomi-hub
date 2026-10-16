package com.iwomi.hub.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iwomi.hub.domain.Enums.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reference;
    private Long projectId;
    private Long componentId;
    private Long partnerId;
    private String reporterName;
    private String reporterContact;
    private String category;
    @Column(nullable = false)
    private String subject;
    private String description;
    @Enumerated(EnumType.STRING)
    private TicketPriority priority = TicketPriority.MEDIUM;
    @Enumerated(EnumType.STRING)
    private TicketStatus status = TicketStatus.OPEN;
    private Long departmentId;
    private Long assigneeId;
    private Long requesterUserId;
    private String trackingCode;
    private String source;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
