package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.domain.Employee;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.domain.Enums.TicketPriority;
import com.iwomi.hub.domain.Enums.TicketStatus;
import com.iwomi.hub.domain.Ticket;
import com.iwomi.hub.domain.TicketMessage;
import com.iwomi.hub.repo.EmployeeRepository;
import com.iwomi.hub.repo.TicketMessageRepository;
import com.iwomi.hub.repo.TicketRepository;
import com.iwomi.hub.service.NotificationService;
import com.iwomi.hub.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Staff view of tickets. Employees only see tickets of their departments or assigned to them. */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    public record TicketUpdate(TicketStatus status, TicketPriority priority, Long assigneeId, Long departmentId) {}

    public record MessageRequest(@NotBlank String body, Boolean internal) {}

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final EmployeeRepository employees;
    private final TicketService service;
    private final NotificationService notifications;

    public TicketController(TicketRepository tickets, TicketMessageRepository messages, EmployeeRepository employees,
                            TicketService service, NotificationService notifications) {
        this.tickets = tickets;
        this.messages = messages;
        this.employees = employees;
        this.service = service;
        this.notifications = notifications;
    }

    private boolean visible(Ticket t, AuthUser u) {
        if (!u.is(Role.EMPLOYEE)) return true;
        Set<Long> departments = u.employeeId() == null ? Set.of()
                : employees.findById(u.employeeId()).map(Employee::getDepartmentIds).orElse(Set.of());
        return Objects.equals(t.getAssigneeId(), u.employeeId())
                || (t.getDepartmentId() != null && departments.contains(t.getDepartmentId()));
    }

    private Ticket load(Long id) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket not found: " + id));
        if (!visible(t, CurrentUser.require())) throw new NotFoundException("Ticket not found: " + id);
        return t;
    }

    @GetMapping
    public List<Ticket> list(@RequestParam(required = false) TicketStatus status,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) Long projectId,
                             @RequestParam(required = false) Long assigneeId) {
        AuthUser user = CurrentUser.require();
        return tickets.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> visible(t, user))
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> departmentId == null || Objects.equals(t.getDepartmentId(), departmentId))
                .filter(t -> projectId == null || Objects.equals(t.getProjectId(), projectId))
                .filter(t -> assigneeId == null || Objects.equals(t.getAssigneeId(), assigneeId))
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Ticket t = load(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ticket", t);
        out.put("messages", messages.findByTicketIdOrderByCreatedAtAsc(id));
        return out;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket create(@Valid @RequestBody TicketService.CreateTicket body) {
        return service.create(body);
    }

    @PatchMapping("/{id}")
    public Ticket update(@PathVariable Long id, @RequestBody TicketUpdate u) {
        Ticket t = load(id);
        Long oldDepartment = t.getDepartmentId();
        if (u.priority() != null) t.setPriority(u.priority());
        if (u.departmentId() != null) t.setDepartmentId(u.departmentId());
        if (u.assigneeId() != null) {
            t.setAssigneeId(u.assigneeId());
            if (t.getStatus() == TicketStatus.OPEN && u.status() == null) t.setStatus(TicketStatus.ASSIGNED);
        }
        if (u.status() != null) t.setStatus(u.status());
        t = tickets.save(t);
        if (t.getDepartmentId() != null && !Objects.equals(oldDepartment, t.getDepartmentId())) {
            notifications.notifyDepartment(t.getDepartmentId(), "Ticket " + t.getReference() + " transferred to your department",
                    t.getSubject(), "TICKET", t.getId());
        }
        return t;
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketMessage addMessage(@PathVariable Long id, @Valid @RequestBody MessageRequest r) {
        load(id);
        AuthUser user = CurrentUser.require();
        TicketMessage m = new TicketMessage();
        m.setTicketId(id);
        m.setAuthor(user.fullName());
        m.setAuthorRole(user.role().name());
        m.setBody(r.body());
        m.setInternal(Boolean.TRUE.equals(r.internal()));
        return messages.save(m);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!CurrentUser.require().is(Role.ADMIN)) throw new ForbiddenException("Only an administrator can delete tickets");
        if (!tickets.existsById(id)) throw new NotFoundException("Ticket not found: " + id);
        tickets.deleteById(id);
    }
}
