package com.iwomi.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iwomi.hub.domain.KycDocument;
import com.iwomi.hub.domain.Partner;
import com.iwomi.hub.domain.PartnerKyc;
import com.iwomi.hub.repo.KycDocumentRepository;
import com.iwomi.hub.repo.PartnerKycRepository;
import com.iwomi.hub.repo.PartnerRepository;
import com.iwomi.hub.web.AiException;
import com.iwomi.hub.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI-assisted review of a partner's KYC file: consistency, completeness and a risk summary. A human decides. */
@Service
public class KycReviewService {

    private static final String SYSTEM = """
        You are a KYC / compliance analyst assistant at a Cameroonian fintech. You review a partner organisation's KYC file
        (the submitted data and the list of attached documents) and prepare a short review for the compliance officer.
        Check: completeness (missing fields or documents), consistency (legal name versus partner name, representative and contact details,
        plausible registration number format, address present), and anything that looks unusual. You only see the data below, not the documents' content;
        never invent facts, and say when something cannot be verified from the data.
        Answer with ONE JSON object and nothing else:
        {"riskLevel":"LOW|MEDIUM|HIGH","summary":"2-3 sentences","issues":[{"field":"","issue":"","severity":"LOW|MEDIUM|HIGH"}],
         "missingItems":["..."],"recommendation":"APPROVE|REQUEST_MORE_INFO|REJECT","reasoning":"why, in plain language"}
        You assist the officer; the final decision is always human.
        """;

    private final LlmClient llm;
    private final ObjectMapper om;
    private final PartnerKycRepository kycs;
    private final PartnerRepository partners;
    private final KycDocumentRepository documents;

    public KycReviewService(LlmClient llm, ObjectMapper om, PartnerKycRepository kycs,
                            PartnerRepository partners, KycDocumentRepository documents) {
        this.llm = llm;
        this.om = om;
        this.kycs = kycs;
        this.partners = partners;
        this.documents = documents;
    }

    public JsonNode review(Long kycId) throws Exception {
        if (!llm.configured()) throw new AiException(llm.notConfiguredMessage());
        PartnerKyc k = kycs.findById(kycId).orElseThrow(() -> new NotFoundException("KYC not found: " + kycId));
        Partner p = partners.findById(k.getPartnerId()).orElseThrow(() -> new NotFoundException("Partner not found"));
        List<KycDocument> docs = documents.findByKycId(kycId);

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("today", LocalDate.now().toString());
        facts.put("partner", Map.of("name", String.valueOf(p.getName()), "country", String.valueOf(p.getCountry()),
                "contactEmail", String.valueOf(p.getContactEmail())));
        Map<String, Object> kyc = new LinkedHashMap<>();
        kyc.put("legalName", k.getLegalName());
        kyc.put("registrationNumber", k.getRegistrationNumber());
        kyc.put("address", k.getAddress());
        kyc.put("representativeName", k.getRepresentativeName());
        kyc.put("status", k.getStatus());
        kyc.put("submittedAt", k.getSubmittedAt());
        kyc.put("notes", k.getNotes());
        facts.put("kyc", kyc);
        facts.put("documents", docs.stream().map(d -> Map.of("type", String.valueOf(d.getDocType()),
                "fileName", String.valueOf(d.getFileName()))).toList());

        String raw = llm.complete(SYSTEM, "KYC file data:\n" + om.writeValueAsString(facts), 1500);
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new AiException("The AI answer was not in the expected format. Please try again.");
        try {
            return om.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            throw new AiException("The AI answer was malformed. Please try again.");
        }
    }
}
