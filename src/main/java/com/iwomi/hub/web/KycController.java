package com.iwomi.hub.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.iwomi.hub.domain.Enums.KycStatus;
import com.iwomi.hub.domain.PartnerKyc;
import com.iwomi.hub.repo.PartnerKycRepository;
import com.iwomi.hub.service.KycReviewService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/kyc")
public class KycController extends CrudController<PartnerKyc, PartnerKycRepository> {

    public record ReviewRequest(KycStatus status, String notes) {}

    private final KycReviewService reviewService;

    public KycController(PartnerKycRepository repo, KycReviewService reviewService) {
        super(repo, PartnerKyc::setId);
        this.reviewService = reviewService;
    }

    /** AI checks completeness and consistency and suggests a recommendation. The officer decides. */
    @PostMapping("/{id}/ai-review")
    public JsonNode aiReview(@PathVariable Long id) throws Exception {
        return reviewService.review(id);
    }

    @PostMapping("/{id}/submit")
    public PartnerKyc submit(@PathVariable Long id) {
        PartnerKyc k = get(id);
        k.setStatus(KycStatus.SUBMITTED);
        k.setSubmittedAt(Instant.now());
        return repo.save(k);
    }

    @PostMapping("/{id}/review")
    public PartnerKyc review(@PathVariable Long id, @RequestBody ReviewRequest r) {
        if (r.status() != KycStatus.APPROVED && r.status() != KycStatus.REJECTED) {
            throw new IllegalArgumentException("Review status must be APPROVED or REJECTED");
        }
        PartnerKyc k = get(id);
        k.setStatus(r.status());
        k.setNotes(r.notes());
        k.setReviewedAt(Instant.now());
        return repo.save(k);
    }
}
