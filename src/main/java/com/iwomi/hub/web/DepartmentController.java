package com.iwomi.hub.web;

import com.iwomi.hub.domain.Department;
import com.iwomi.hub.repo.DepartmentRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController extends CrudController<Department, DepartmentRepository> {
    public DepartmentController(DepartmentRepository repo) {
        super(repo, Department::setId);
    }
}
