package com.iwomi.hub.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iwomi.hub.domain.Enums.*;

@Entity
@Table(name = "partner_kyc")
@Getter
@Setter
@NoArgsConstructor
public class PartnerKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long partnerId;
    private String legalName;
    private String registrationNumber;
    private String address;
    private String representativeName;
    @Enumerated(EnumType.STRING)
    private KycStatus status = KycStatus.DRAFT;
    private String notes;
    private Instant submittedAt;
    private Instant reviewedAt;
}
