package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.config.RateLimiter;
import com.iwomi.hub.domain.Enums.TicketPriority;
import com.iwomi.hub.domain.Enums.TicketStatus;
import com.iwomi.hub.domain.Project;
import com.iwomi.hub.domain.Ticket;
import com.iwomi.hub.domain.TicketMessage;
import com.iwomi.hub.repo.ProjectRepository;
import com.iwomi.hub.repo.TicketMessageRepository;
import com.iwomi.hub.repo.TicketRepository;
import com.iwomi.hub.service.NotificationService;
import com.iwomi.hub.service.PortalService;
import com.iwomi.hub.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The logged-in external (customer) user: their own support requests and conversations with the IWOMI team. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record NewRequest(@NotBlank @Size(max = 300) String subject, @NotBlank @Size(max = 4000) String description,
                             String category, Long projectId) {}

    public record ReplyRequest(@NotBlank @Size(max = 4000) String body) {}

    public record ClaimRequest(@NotBlank String reference, @NotBlank String code) {}

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final ProjectRepository projects;
    private final TicketService ticketService;
    private final PortalService portal;
    private final NotificationService notifications;
    private final RateLimiter limiter;

    public MeController(TicketRepository tickets, TicketMessageRepository messages, ProjectRepository projects,
                        TicketService ticketService, PortalService portal, NotificationService notifications,
                        RateLimiter limiter) {
        this.tickets = tickets;
        this.messages = messages;
        this.projects = projects;
        this.ticketService = ticketService;
        this.portal = portal;
        this.notifications = notifications;
        this.limiter = limiter;
    }

    @GetMapping("/tickets")
    public List<Map<String, Object>> list() {
        AuthUser me = CurrentUser.require();
        return tickets.findByRequesterUserIdOrderByCreatedAtDesc(me.id()).stream().map(t -> view(t, false)).toList();
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody NewRequest r) {
        AuthUser me = CurrentUser.require();
        if (!limiter.allow("me-ticket:" + me.id(), 20, 3600)) {
            throw new TooManyRequestsException("Too many requests. Please try again later.");
        }
        Long projectId = portal.isPublicProject(r.projectId()) ? r.projectId() : null;
        Ticket t = ticketService.create(new TicketService.CreateTicket(me.fullName(), me.username(), r.subject(),
                r.description(), PortalService.normalizeCategory(r.category()), TicketPriority.MEDIUM, projectId, null,
                me.partnerId(), "PORTAL"), me.id(), false);
        return view(t, true);
    }

    @GetMapping("/tickets/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return view(own(id), true);
    }

    @PostMapping("/tickets/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest r) {
        AuthUser me = CurrentUser.require();
        Ticket t = own(id);
        TicketMessage m = new TicketMessage();
        m.setTicketId(id);
        m.setAuthor(me.fullName());
        m.setAuthorRole("EXTERNAL");
        m.setBody(r.body());
        m.setInternal(false);
        messages.save(m);
        if (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED) {
            t.setStatus(TicketStatus.OPEN);
        }
        t = tickets.save(t);
        if (t.getDepartmentId() != null) {
            notifications.notifyDepartment(t.getDepartmentId(), "New reply on " + t.getReference(),
                    me.fullName() + " answered", "TICKET", t.getId());
        }
        return view(t, true);
    }

    /** Links a request created anonymously (reference + secret tracking code) to this account. */
    @PostMapping("/tickets/claim")
    public Map<String, Object> claim(@Valid @RequestBody ClaimRequest r) {
        AuthUser me = CurrentUser.require();
        if (!limiter.allow("claim:" + me.id(), 10, 3600)) {
            throw new TooManyRequestsException("Too many attempts. Please try again later.");
        }
        Ticket t = tickets.findByReferenceIgnoreCase(r.reference().trim()).orElse(null);
        boolean ok = t != null && t.getRequesterUserId() == null && t.getTrackingCode() != null
                && MessageDigest.isEqual(t.getTrackingCode().getBytes(StandardCharsets.UTF_8),
                        r.code().trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new NotFoundException("No request matches this reference and code");
        t.setRequesterUserId(me.id());
        t.setTrackingCode(null);
        return view(tickets.save(t), true);
    }

    private Ticket own(Long id) {
        AuthUser me = CurrentUser.require();
        return tickets.findById(id).filter(t -> me.id().equals(t.getRequesterUserId()))
                .orElseThrow(() -> new NotFoundException("Request not found"));
    }

    /** Only fields a customer may see: no department, assignee, internal notes. */
    private Map<String, Object> view(Ticket t, boolean withMessages) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", t.getId());
        out.put("reference", t.getReference());
        out.put("subject", t.getSubject());
        out.put("description", t.getDescription());
        out.put("category", t.getCategory());
        out.put("status", t.getStatus());
        out.put("project", t.getProjectId() == null ? null
                : projects.findById(t.getProjectId()).map(Project::getName).orElse(null));
        out.put("createdAt", t.getCreatedAt());
        out.put("updatedAt", t.getUpdatedAt());
        if (withMessages) {
            out.put("messages", messages.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(t.getId()).stream().map(m -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("author", m.getAuthor());
                row.put("authorRole", m.getAuthorRole());
                row.put("body", m.getBody());
                row.put("createdAt", m.getCreatedAt());
                return row;
            }).toList());
        }
        return out;
    }
}
