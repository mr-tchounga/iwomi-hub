package com.iwomi.hub.service;

import com.iwomi.hub.domain.Enums.ComponentType;
import com.iwomi.hub.domain.Enums.ProjectStatus;
import com.iwomi.hub.domain.Enums.TicketPriority;
import com.iwomi.hub.domain.Project;
import com.iwomi.hub.domain.Ticket;
import com.iwomi.hub.repo.ProjectComponentRepository;
import com.iwomi.hub.repo.ProjectRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the public (not logged in) visitors can see and do. Nothing internal is exposed here. */
@Service
public class PortalService {

    public record CatalogComponent(ComponentType type, String name, String version) {}

    public record CatalogItem(Long id, String name, String description, ProjectStatus status,
                              List<CatalogComponent> components) {}

    public record PortalRequest(
            @Size(max = 200) String name,
            @Size(max = 200) String contact,
            @NotBlank @Size(max = 300) String subject,
            @NotBlank @Size(max = 4000) String description,
            String category,
            Long projectId) {}

    private static final Set<String> CATEGORIES = Set.of("QUESTION", "BUG", "HELP", "OTHER");

    private final ProjectRepository projects;
    private final ProjectComponentRepository components;
    private final TicketService tickets;

    public PortalService(ProjectRepository projects, ProjectComponentRepository components, TicketService tickets) {
        this.projects = projects;
        this.components = components;
        this.tickets = tickets;
    }

    public List<CatalogItem> catalog() {
        return projects.findAll().stream()
                .filter(Project::isPublicVisible)
                .filter(p -> p.getStatus() != ProjectStatus.ARCHIVED)
                .map(p -> new CatalogItem(p.getId(), p.getName(), p.getDescription(), p.getStatus(),
                        components.findByProjectId(p.getId()).stream()
                                .map(c -> new CatalogComponent(c.getType(), c.getName(), c.getVersion())).toList()))
                .toList();
    }

    public boolean isPublicProject(Long projectId) {
        return projectId != null && catalog().stream().anyMatch(c -> c.id().equals(projectId));
    }

    public static String normalizeCategory(String category) {
        if (category == null) return "HELP";
        String c = category.trim().toUpperCase();
        return CATEGORIES.contains(c) ? c : "HELP";
    }

    public static TicketPriority parsePriority(String p) {
        if (p == null) return TicketPriority.MEDIUM;
        try {
            TicketPriority parsed = TicketPriority.valueOf(p.trim().toUpperCase());
            return parsed == TicketPriority.URGENT ? TicketPriority.HIGH : parsed;
        } catch (IllegalArgumentException e) {
            return TicketPriority.MEDIUM;
        }
    }

    /** Anonymous help request. The visitor gets a reference and a secret tracking code. */
    public Map<String, Object> createAnonymousRequest(PortalRequest r) {
        Long projectId = isPublicProject(r.projectId()) ? r.projectId() : null;
        Ticket t = tickets.create(new TicketService.CreateTicket(r.name(), r.contact(), r.subject(), r.description(),
                normalizeCategory(r.category()), TicketPriority.MEDIUM, projectId, null, null, "PORTAL"), null, true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reference", t.getReference());
        out.put("trackingCode", t.getTrackingCode());
        out.put("message", "Request received. Log in or create an account, then use your reference and tracking code to follow the replies.");
        return out;
    }
}
