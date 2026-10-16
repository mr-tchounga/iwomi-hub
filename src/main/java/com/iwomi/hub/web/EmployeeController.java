package com.iwomi.hub.web;

import com.iwomi.hub.domain.Employee;
import com.iwomi.hub.repo.EmployeeRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController extends CrudController<Employee, EmployeeRepository> {
    public EmployeeController(EmployeeRepository repo) {
        super(repo, Employee::setId);
    }
}
