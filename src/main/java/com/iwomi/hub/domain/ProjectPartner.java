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
public class ProjectPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;
    @Column(nullable = false)
    private Long partnerId;
    private LocalDate soldAt;
    private BigDecimal amount;
    private String currency = "XAF";
    @Enumerated(EnumType.STRING)
    private SaleStatus status = SaleStatus.ACTIVE;
}
