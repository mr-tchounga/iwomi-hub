package com.iwomi.hub.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iwomi.hub.domain.Enums.*;

@Entity
@Table(name = "ticket_routing_rule")
@Getter
@Setter
@NoArgsConstructor
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    @Enumerated(EnumType.STRING)
    private ComponentType componentType;
    private String category;
    @Column(nullable = false)
    private Long departmentId;
    private int priority;
}
