package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.domain.Department;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.domain.Enums.TaskStatus;
import com.iwomi.hub.domain.Enums.TicketStatus;
import com.iwomi.hub.domain.ProjectPartner;
import com.iwomi.hub.domain.Ticket;
import com.iwomi.hub.repo.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ProjectRepository projects;
    private final PartnerRepository partners;
    private final EmployeeRepository employees;
    private final TicketRepository tickets;
    private final TaskRepository tasks;
    private final DepartmentRepository departments;
    private final ProjectPartnerRepository sales;

    public DashboardController(ProjectRepository projects, PartnerRepository partners, EmployeeRepository employees,
                               TicketRepository tickets, TaskRepository tasks, DepartmentRepository departments,
                               ProjectPartnerRepository sales) {
        this.projects = projects;
        this.partners = partners;
        this.employees = employees;
        this.tickets = tickets;
        this.tasks = tasks;
        this.departments = departments;
        this.sales = sales;
    }

    @GetMapping
    public Map<String, Object> stats() {
        AuthUser user = CurrentUser.require();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projects", projects.count());
        out.put("partners", partners.count());
        out.put("employees", employees.count());
        out.put("ongoingTasks", tasks.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.BLOCKED).count());

        if (user.is(Role.ADMIN, Role.CTO, Role.EMPLOYEE)) {
            List<Ticket> open = tickets.findAll().stream()
                    .filter(t -> t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED)
                    .toList();
            Map<String, Long> byDepartment = new LinkedHashMap<>();
            for (Department d : departments.findAll()) {
                byDepartment.put(d.getName(), open.stream().filter(t -> d.getId().equals(t.getDepartmentId())).count());
            }
            out.put("openTickets", open.size());
            out.put("openTicketsByDepartment", byDepartment);
        }
        if (user.is(Role.ADMIN, Role.CTO, Role.ACCOUNTANT)) {
            BigDecimal total = sales.findAll().stream().map(ProjectPartner::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            out.put("salesTotal", total);
            out.put("salesCurrency", "XAF");
        }
        return out;
    }
}
