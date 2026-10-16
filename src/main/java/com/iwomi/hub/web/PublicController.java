package com.iwomi.hub.web;

import com.iwomi.hub.domain.*;
import com.iwomi.hub.repo.*;
import com.iwomi.hub.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API for IWOMI apps (authenticated with X-API-Key). Replaces the "contact us on WhatsApp" link:
 * apps create tickets here, and fetch partner / KYC information from the central Hub.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final TicketService ticketService;
    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final PartnerRepository partners;
    private final PartnerKycRepository kycs;
    private final KycDocumentRepository kycDocuments;
    private final ProjectPartnerRepository sales;
    private final ProjectRepository projects;

    public PublicController(TicketService ticketService, TicketRepository tickets, TicketMessageRepository messages,
                            PartnerRepository partners, PartnerKycRepository kycs, KycDocumentRepository kycDocuments,
                            ProjectPartnerRepository sales, ProjectRepository projects) {
        this.ticketService = ticketService;
        this.tickets = tickets;
        this.messages = messages;
        this.partners = partners;
        this.kycs = kycs;
        this.kycDocuments = kycDocuments;
        this.sales = sales;
        this.projects = projects;
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTicket(@AuthenticationPrincipal AppClient client,
                                            @Valid @RequestBody TicketService.CreateTicket body) {
        Long projectId = body.projectId() != null ? body.projectId() : client.getProjectId();
        String source = body.source() != null ? body.source() : client.getName();
        Ticket t = ticketService.create(new TicketService.CreateTicket(body.reporterName(), body.reporterContact(),
                body.subject(), body.description(), body.category(), body.priority(), projectId,
                body.componentId(), body.partnerId(), source));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", t.getId());
        out.put("reference", t.getReference());
        out.put("status", t.getStatus());
        return out;
    }

    /** Lets the app show the user the status of a ticket they opened. */
    @GetMapping("/tickets/{reference}")
    public Map<String, Object> ticketStatus(@PathVariable String reference, @RequestParam String contact) {
        Ticket t = tickets.findByReferenceIgnoreCase(reference)
                .filter(x -> x.getReporterContact() != null && x.getReporterContact().equalsIgnoreCase(contact.trim()))
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + reference));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reference", t.getReference());
        out.put("subject", t.getSubject());
        out.put("status", t.getStatus());
        out.put("priority", t.getPriority());
        out.put("createdAt", t.getCreatedAt());
        out.put("messages", messages.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(t.getId()));
        return out;
    }

    /** Central partner profile: identity, KYC status and documents, and the projects sold to them. */
    @GetMapping("/partners/{id}")
    public Map<String, Object> partnerProfile(@PathVariable Long id) {
        Partner p = partners.findById(id).orElseThrow(() -> new NotFoundException("Partner not found: " + id));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("partner", p);
        PartnerKyc kyc = kycs.findByPartnerId(id).orElse(null);
        out.put("kyc", kyc);
        out.put("kycDocuments", kyc == null ? List.of() : kycDocuments.findByKycId(kyc.getId()));
        List<Map<String, Object>> owned = new ArrayList<>();
        for (ProjectPartner s : sales.findByPartnerId(id)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", s.getProjectId());
            row.put("project", projects.findById(s.getProjectId()).map(Project::getName).orElse(null));
            row.put("soldAt", s.getSoldAt());
            row.put("status", s.getStatus());
            owned.add(row);
        }
        out.put("projects", owned);
        return out;
    }

    @GetMapping("/partners")
    public List<Partner> findPartners(@RequestParam(required = false) String email) {
        return partners.findAll().stream()
                .filter(p -> email == null || email.equalsIgnoreCase(p.getContactEmail()))
                .toList();
    }
}
