package com.iwomi.hub.config;

import com.iwomi.hub.domain.AppUser;
import com.iwomi.hub.domain.Employee;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.domain.Partner;
import com.iwomi.hub.repo.AppUserRepository;
import com.iwomi.hub.repo.EmployeeRepository;
import com.iwomi.hub.repo.PartnerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the admin account and (when demo mode is on) one demo account per role. Safe to run on every start. */
@Component
public class DataInitializer implements ApplicationRunner {

    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final PartnerRepository partners;
    private final PasswordEncoder encoder;
    private final String adminUser;
    private final String adminPassword;
    private final boolean demo;
    private final String demoPassword;

    public DataInitializer(AppUserRepository users, EmployeeRepository employees, PartnerRepository partners,
                           PasswordEncoder encoder,
                           @Value("${hub.admin.user}") String adminUser,
                           @Value("${hub.admin.password}") String adminPassword,
                           @Value("${hub.demo.enabled}") boolean demo,
                           @Value("${hub.demo.password}") String demoPassword) {
        this.users = users;
        this.employees = employees;
        this.partners = partners;
        this.encoder = encoder;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        this.demo = demo;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensure(adminUser, adminPassword, "Administrator", null, Role.ADMIN, null, null);
        if (!demo) return;
        ensure("cto", demoPassword, "Alice Nguema (CTO)", "alice@example.com", Role.CTO, employeeId("alice@example.com"), null);
        ensure("rh", demoPassword, "Rachel Mbida (HR)", null, Role.HR, null, null);
        ensure("compta", demoPassword, "Armand Tabi (Accountant)", null, Role.ACCOUNTANT, null, null);
        ensure("brice", demoPassword, "Brice Tchoua", "brice@example.com", Role.EMPLOYEE, employeeId("brice@example.com"), null);
        ensure("carine", demoPassword, "Carine Fotso", "carine@example.com", Role.EMPLOYEE, employeeId("carine@example.com"), null);
        ensure("estelle", demoPassword, "Estelle Kamga", "estelle@example.com", Role.EMPLOYEE, employeeId("estelle@example.com"), null);
        ensure("david", demoPassword, "David Essomba", "david@example.com", Role.EMPLOYEE, employeeId("david@example.com"), null);
        Long partnerId = partners.findAll().stream().filter(p -> "Demo Bank Beta".equals(p.getName()))
                .map(Partner::getId).findFirst().orElse(null);
        ensure("client", demoPassword, "Marie Ndjock (Demo Bank Beta)", "marie@beta.example.com", Role.EXTERNAL, null, partnerId);
    }

    private Long employeeId(String email) {
        return employees.findAll().stream().filter(e -> email.equalsIgnoreCase(e.getEmail()))
                .map(Employee::getId).findFirst().orElse(null);
    }

    private void ensure(String username, String password, String fullName, String email, Role role,
                        Long employeeId, Long partnerId) {
        if (users.existsByUsernameIgnoreCase(username)) return;
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(fullName);
        u.setEmail(email);
        u.setRole(role);
        u.setEmployeeId(employeeId);
        u.setPartnerId(partnerId);
        u.setActive(true);
        users.save(u);
    }
}
