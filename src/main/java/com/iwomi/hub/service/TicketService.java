package com.iwomi.hub.service;

import com.iwomi.hub.domain.*;
import com.iwomi.hub.domain.Enums.ComponentType;
import com.iwomi.hub.domain.Enums.TicketPriority;
import com.iwomi.hub.domain.Enums.TicketStatus;
import com.iwomi.hub.repo.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class TicketService {

    /** Payload used by IWOMI apps (public API) and by staff to create a ticket. */
    public record CreateTicket(
            String reporterName,
            String reporterContact,
            @NotBlank String subject,
            String description,
            String category,
            TicketPriority priority,
            Long projectId,
            Long componentId,
            Long partnerId,
            String source) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final TicketRepository tickets;
    private final ProjectComponentRepository components;
    private final RoutingRuleRepository rules;
    private final SettingRepository settings;
    private final NotificationService notifications;

    public TicketService(TicketRepository tickets, ProjectComponentRepository components, RoutingRuleRepository rules,
                         SettingRepository settings, NotificationService notifications) {
        this.tickets = tickets;
        this.components = components;
        this.rules = rules;
        this.settings = settings;
        this.notifications = notifications;
    }

    public Ticket create(CreateTicket r) {
        return create(r, null, false);
    }

    /**
     * @param requesterUserId the logged-in external user who asked for help (null for anonymous / apps / staff)
     * @param trackingCode    generate a secret code so an anonymous visitor can later claim the request after login
     */
    public Ticket create(CreateTicket r, Long requesterUserId, boolean trackingCode) {
        Ticket t = new Ticket();
        t.setReporterName(r.reporterName());
        t.setReporterContact(r.reporterContact());
        t.setSubject(r.subject());
        t.setDescription(r.description());
        t.setCategory(r.category());
        t.setPriority(r.priority() == null ? TicketPriority.MEDIUM : r.priority());
        t.setProjectId(r.projectId());
        t.setComponentId(r.componentId());
        t.setPartnerId(r.partnerId());
        t.setSource(r.source());
        t.setStatus(TicketStatus.OPEN);
        t.setRequesterUserId(requesterUserId);
        if (trackingCode) t.setTrackingCode(newCode());

        ComponentType componentType = null;
        if (r.componentId() != null) {
            componentType = components.findById(r.componentId()).map(ProjectComponent::getType).orElse(null);
        }
        t.setDepartmentId(route(r.projectId(), componentType, r.category()));

        t = tickets.save(t);
        t.setReference(String.format("TCK-%05d", t.getId()));
        t = tickets.save(t);

        if (t.getDepartmentId() != null) {
            notifications.notifyDepartment(t.getDepartmentId(), "New ticket " + t.getReference(),
                    t.getSubject(), "TICKET", t.getId());
        }
        return t;
    }

    public static String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    /**
     * Picks the most specific matching routing rule (project > component type > category),
     * then the highest rule priority. Falls back to the default department from Settings.
     */
    public Long route(Long projectId, ComponentType componentType, String category) {
        RoutingRule best = null;
        int bestScore = -1;
        for (RoutingRule rule : rules.findAll()) {
            if (rule.getProjectId() != null && !rule.getProjectId().equals(projectId)) continue;
            if (rule.getComponentType() != null && rule.getComponentType() != componentType) continue;
            if (rule.getCategory() != null
                    && (category == null || !rule.getCategory().equalsIgnoreCase(category))) continue;
            int specificity = (rule.getProjectId() != null ? 1 : 0)
                    + (rule.getComponentType() != null ? 1 : 0)
                    + (rule.getCategory() != null ? 1 : 0);
            int score = specificity * 1000 + rule.getPriority();
            if (score > bestScore) {
                best = rule;
                bestScore = score;
            }
        }
        if (best != null) return best.getDepartmentId();
        return settings.findById("default_ticket_department").map(s -> {
            try {
                return Long.valueOf(s.getValue());
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }
}
