package com.iwomi.hub.service;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.domain.*;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.domain.Enums.TicketStatus;
import com.iwomi.hub.repo.*;
import com.iwomi.hub.web.TaskController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the live context given to the AI: company knowledge plus a snapshot of the data the user is allowed to see.
 * One request to the model is enough (no tool round trips), which keeps answers fast and works with any provider.
 */
@Service
public class ChatContextService {

    private final String publicKnowledge = load("ai/iwomi-knowledge.md");
    private final String staffKnowledge = load("ai/staff-knowledge.md");

    private final PortalService portal;
    private final ProjectRepository projects;
    private final ProjectComponentRepository components;
    private final ProjectPartnerRepository sales;
    private final PartnerRepository partners;
    private final PartnerKycRepository kycs;
    private final TicketRepository tickets;
    private final TaskRepository tasks;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;

    public ChatContextService(PortalService portal, ProjectRepository projects, ProjectComponentRepository components,
                              ProjectPartnerRepository sales, PartnerRepository partners, PartnerKycRepository kycs,
                              TicketRepository tickets, TaskRepository tasks, EmployeeRepository employees,
                              DepartmentRepository departments) {
        this.portal = portal;
        this.projects = projects;
        this.components = components;
        this.sales = sales;
        this.partners = partners;
        this.kycs = kycs;
        this.tickets = tickets;
        this.tasks = tasks;
        this.employees = employees;
        this.departments = departments;
    }

    private static String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public String publicKnowledge() {
        return publicKnowledge;
    }

    public String staffKnowledge() {
        return staffKnowledge;
    }

    private static String s(Object o) {
        return o == null ? "-" : String.valueOf(o);
    }

    private static String cut(String text, int max) {
        if (text == null) return "-";
        String t = text.replace('\n', ' ');
        return t.length() > max ? t.substring(0, max) + "..." : t;
    }

    /** Public solutions catalog (what any visitor may see). */
    public String publicCatalog() {
        StringBuilder sb = new StringBuilder();
        for (PortalService.CatalogItem p : portal.catalog()) {
            sb.append("- ").append(p.name()).append(" [").append(p.status()).append("]: ").append(cut(p.description(), 200));
            if (!p.components().isEmpty()) {
                sb.append(" Components: ").append(p.components().stream()
                        .map(c -> c.type() + (c.name() != null ? " " + c.name() : "") + (c.version() != null ? " v" + c.version() : ""))
                        .collect(Collectors.joining("; ")));
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? "(no public solutions published)\n" : sb.toString();
    }

    /** Snapshot of Hub data limited to what this user's role may see. */
    public String staff(AuthUser u) {
        boolean seeTickets = u.is(Role.ADMIN, Role.CTO, Role.EMPLOYEE);
        boolean seeSales = u.is(Role.ADMIN, Role.CTO, Role.ACCOUNTANT);
        boolean seeTasks = u.is(Role.ADMIN, Role.CTO, Role.HR, Role.EMPLOYEE);
        boolean seeRepos = u.is(Role.ADMIN, Role.CTO, Role.EMPLOYEE);

        Map<Long, String> deptNames = departments.findAll().stream().collect(Collectors.toMap(Department::getId, Department::getName));
        List<Employee> allEmployees = employees.findAll();
        Map<Long, String> empNames = allEmployees.stream().collect(Collectors.toMap(Employee::getId, Employee::getFullName));
        Map<Long, String> projectNames = projects.findAll().stream().collect(Collectors.toMap(Project::getId, Project::getName));
        Map<Long, String> partnerNames = partners.findAll().stream().collect(Collectors.toMap(Partner::getId, Partner::getName));

        StringBuilder sb = new StringBuilder();
        sb.append("Today: ").append(LocalDate.now()).append("\n");
        sb.append("Current user: ").append(u.fullName()).append(", role ").append(u.role());
        if (u.employeeId() != null) sb.append(", employee id ").append(u.employeeId());
        sb.append("\n\n## Projects\n");
        for (Project p : projects.findAll()) {
            sb.append("- ").append(p.getName()).append(" [").append(p.getStatus()).append("]: ").append(cut(p.getDescription(), 160));
            List<ProjectComponent> comps = components.findByProjectId(p.getId());
            if (!comps.isEmpty()) {
                sb.append(" | Components: ").append(comps.stream().map(c -> c.getType() + " " + s(c.getName()) + " v" + s(c.getVersion())
                        + " (" + s(c.getStack()) + ")" + (seeRepos ? " repo " + s(c.getRepoUrl()) + " env " + cut(c.getEnvironmentUrls(), 80) : ""))
                        .collect(Collectors.joining("; ")));
            }
            sb.append('\n');
        }

        sb.append("\n## Partners\n");
        Map<Long, PartnerKyc> kycByPartner = seeSales ? kycs.findAll().stream()
                .collect(Collectors.toMap(PartnerKyc::getPartnerId, k -> k, (a, b) -> a)) : Map.of();
        for (Partner p : partners.findAll()) {
            sb.append("- ").append(p.getName()).append(" (").append(s(p.getCountry())).append(", ").append(s(p.getContactEmail())).append(")");
            if (seeSales) {
                PartnerKyc k = kycByPartner.get(p.getId());
                sb.append(" | KYC: ").append(k == null ? "none" : k.getStatus());
                List<ProjectPartner> bought = sales.findByPartnerId(p.getId());
                if (!bought.isEmpty()) {
                    sb.append(" | Bought: ").append(bought.stream().map(x -> s(projectNames.get(x.getProjectId())) + " (sold " + s(x.getSoldAt())
                            + ", " + s(x.getAmount()) + " " + s(x.getCurrency()) + ", " + x.getStatus() + ")").collect(Collectors.joining("; ")));
                }
            }
            sb.append('\n');
        }
        if (!seeSales) sb.append("(Sales, amounts and KYC are not available to the ").append(u.role()).append(" role.)\n");

        sb.append("\n## Tickets\n");
        if (seeTickets) {
            Set<Long> myDepts = u.employeeId() == null ? Set.of() : allEmployees.stream()
                    .filter(e -> e.getId().equals(u.employeeId())).findFirst().map(Employee::getDepartmentIds).orElse(Set.of());
            List<Ticket> visible = tickets.findAllByOrderByCreatedAtDesc().stream()
                    .filter(t -> !u.is(Role.EMPLOYEE) || Objects.equals(t.getAssigneeId(), u.employeeId())
                            || (t.getDepartmentId() != null && myDepts.contains(t.getDepartmentId())))
                    .limit(40).toList();
            for (Ticket t : visible) {
                boolean open = t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED;
                sb.append("- ").append(t.getReference()).append(" [").append(t.getPriority()).append('/').append(t.getStatus()).append(open ? "" : ", closed")
                        .append("] \"").append(cut(t.getSubject(), 100)).append("\" project: ").append(s(projectNames.get(t.getProjectId())))
                        .append(", partner: ").append(s(partnerNames.get(t.getPartnerId()))).append(", department: ").append(s(deptNames.get(t.getDepartmentId())))
                        .append(", assignee: ").append(s(empNames.get(t.getAssigneeId()))).append(", category: ").append(s(t.getCategory()))
                        .append(", from: ").append(s(t.getSource())).append(", created ").append(t.getCreatedAt() == null ? "-" : t.getCreatedAt().toString().substring(0, 10))
                        .append(" | ").append(cut(t.getDescription(), 140)).append('\n');
            }
            if (visible.isEmpty()) sb.append("(no tickets)\n");
        } else {
            sb.append("(Tickets are not available to the ").append(u.role()).append(" role.)\n");
        }

        sb.append("\n## Tasks (PAST = done/cancelled, ONGOING = in progress/blocked, FUTURE = planned)\n");
        if (seeTasks) {
            List<Task> list = tasks.findAll().stream()
                    .sorted(Comparator.comparing((Task t) -> t.getDueDate() == null ? LocalDate.MAX : t.getDueDate())).limit(80).toList();
            for (Task t : list) {
                sb.append("- [").append(TaskController.timelineOf(t)).append('/').append(t.getStatus()).append("] ").append(cut(t.getTitle(), 100))
                        .append(" | assignee: ").append(s(empNames.get(t.getAssigneeId()))).append(" | project: ").append(s(projectNames.get(t.getProjectId())))
                        .append(" | ").append(s(t.getStartDate())).append(" -> ").append(s(t.getDueDate())).append('\n');
            }
            if (list.isEmpty()) sb.append("(no tasks)\n");
        } else {
            sb.append("(Tasks are not available to the ").append(u.role()).append(" role.)\n");
        }

        sb.append("\n## Team\n");
        for (Employee e : allEmployees) {
            sb.append("- ").append(e.getFullName()).append(" | ").append(s(e.getJobTitle())).append(" | departments: ")
                    .append(e.getDepartmentIds().stream().map(deptNames::get).filter(Objects::nonNull).sorted().collect(Collectors.joining(", ")))
                    .append(e.isActive() ? "" : " | inactive").append('\n');
        }
        return sb.toString();
    }
}
