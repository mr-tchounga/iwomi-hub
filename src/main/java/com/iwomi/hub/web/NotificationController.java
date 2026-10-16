package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.domain.Employee;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.domain.Notification;
import com.iwomi.hub.repo.EmployeeRepository;
import com.iwomi.hub.repo.NotificationRepository;
import com.iwomi.hub.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repo;
    private final NotificationService service;
    private final EmployeeRepository employees;

    public NotificationController(NotificationRepository repo, NotificationService service, EmployeeRepository employees) {
        this.repo = repo;
        this.service = service;
        this.employees = employees;
    }

    /** Departments this user may see; null means all (ADMIN, CTO). */
    private Set<Long> scope(AuthUser u) {
        if (u.is(Role.ADMIN, Role.CTO)) return null;
        if (u.employeeId() == null) return Set.of();
        return employees.findById(u.employeeId()).map(Employee::getDepartmentIds).orElse(Set.of());
    }

    @GetMapping
    public List<Notification> list(@RequestParam(required = false) List<Long> departmentIds) {
        Set<Long> scope = scope(CurrentUser.require());
        if (scope == null) {
            if (departmentIds == null || departmentIds.isEmpty()) return repo.findTop50ByOrderByCreatedAtDesc();
            return repo.findTop50ByDepartmentIdInOrderByCreatedAtDesc(departmentIds);
        }
        if (scope.isEmpty()) return List.of();
        return repo.findTop50ByDepartmentIdInOrderByCreatedAtDesc(scope);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return service.subscribe(scope(CurrentUser.require()));
    }

    @PostMapping("/{id}/read")
    public Notification markRead(@PathVariable Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> new NotFoundException("Notification not found: " + id));
        Set<Long> scope = scope(CurrentUser.require());
        if (scope != null && !scope.contains(n.getDepartmentId())) throw new ForbiddenException("Not your department");
        n.setRead(true);
        return repo.save(n);
    }

    @PostMapping("/read-all")
    public void markAllRead(@RequestParam(required = false) List<Long> departmentIds) {
        List<Notification> all = list(departmentIds);
        all.forEach(n -> n.setRead(true));
        repo.saveAll(all);
    }
}
