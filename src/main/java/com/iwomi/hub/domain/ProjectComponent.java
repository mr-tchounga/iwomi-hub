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
public class ProjectComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentType type;
    private String name;
    private String repoUrl;
    private String stack;
    private String version;
    private String environmentUrls;
}
