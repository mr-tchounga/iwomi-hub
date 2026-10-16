package com.iwomi.hub.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String summary;

    /** PERSONAL, COMPANY, or BOTH. */
    @Column(nullable = false, length = 20)
    private String audience;

    @Column(nullable = false, length = 200)
    private String sourceName;

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    @Column(nullable = false)
    private boolean active = true;

    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
