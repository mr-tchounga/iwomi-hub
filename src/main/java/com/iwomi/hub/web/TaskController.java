package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.domain.Enums.TaskStatus;
import com.iwomi.hub.domain.Task;
import com.iwomi.hub.repo.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** ADMIN and CTO manage tasks. An EMPLOYEE can only change the status of tasks assigned to them. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository tasks;

    public TaskController(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /** PAST = done or cancelled, ONGOING = in progress or blocked, FUTURE = planned. */
    public static String timelineOf(Task t) {
        return switch (t.getStatus()) {
            case DONE, CANCELLED -> "PAST";
            case IN_PROGRESS, BLOCKED -> "ONGOING";
            case PLANNED -> "FUTURE";
        };
    }

    @GetMapping
    public List<Task> list(@RequestParam(required = false) Long employeeId,
                           @RequestParam(required = false) Long projectId,
                           @RequestParam(required = false) String timeline) {
        return tasks.findAll().stream()
                .filter(t -> employeeId == null || Objects.equals(t.getAssigneeId(), employeeId))
                .filter(t -> projectId == null || Objects.equals(t.getProjectId(), projectId))
                .filter(t -> timeline == null || timeline.equalsIgnoreCase(timelineOf(t)))
                .sorted(Comparator.comparing((Task t) -> t.getDueDate() == null ? LocalDate.MAX : t.getDueDate()))
                .toList();
    }

    @GetMapping("/timeline/{employeeId}")
    public Map<String, List<Task>> timeline(@PathVariable Long employeeId) {
        Map<String, List<Task>> out = new LinkedHashMap<>();
        for (String bucket : List.of("PAST", "ONGOING", "FUTURE")) {
            out.put(bucket, list(employeeId, null, bucket));
        }
        return out;
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable Long id) {
        return tasks.findById(id).orElseThrow(() -> new NotFoundException("Task not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@RequestBody Task body) {
        if (CurrentUser.require().is(Role.EMPLOYEE)) throw new ForbiddenException("Employees cannot create tasks");
        body.setId(null);
        return save(body);
    }

    @PutMapping("/{id}")
    public Task update(@PathVariable Long id, @RequestBody Task body) {
        Task existing = tasks.findById(id).orElseThrow(() -> new NotFoundException("Task not found: " + id));
        AuthUser user = CurrentUser.require();
        if (user.is(Role.EMPLOYEE)) {
            if (user.employeeId() == null || !user.employeeId().equals(existing.getAssigneeId())) {
                throw new ForbiddenException("You can only update your own tasks");
            }
            if (body.getStatus() != null) existing.setStatus(body.getStatus());
            return save(existing);
        }
        body.setId(id);
        body.setCreatedAt(existing.getCreatedAt());
        body.setCompletedAt(existing.getCompletedAt());
        return save(body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (CurrentUser.require().is(Role.EMPLOYEE)) throw new ForbiddenException("Employees cannot delete tasks");
        if (!tasks.existsById(id)) throw new NotFoundException("Task not found: " + id);
        tasks.deleteById(id);
    }

    private Task save(Task t) {
        if (t.getStatus() == null) t.setStatus(TaskStatus.PLANNED);
        if (t.getStatus() == TaskStatus.DONE && t.getCompletedAt() == null) t.setCompletedAt(Instant.now());
        if (t.getStatus() != TaskStatus.DONE) t.setCompletedAt(null);
        if (t.getStatus() == TaskStatus.IN_PROGRESS && t.getStartDate() == null) t.setStartDate(LocalDate.now());
        return tasks.save(t);
    }
}
