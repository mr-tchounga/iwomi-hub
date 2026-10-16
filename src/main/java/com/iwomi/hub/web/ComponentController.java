package com.iwomi.hub.web;

import com.iwomi.hub.domain.ProjectComponent;
import com.iwomi.hub.repo.ProjectComponentRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/components")
public class ComponentController extends CrudController<ProjectComponent, ProjectComponentRepository> {
    public ComponentController(ProjectComponentRepository repo) {
        super(repo, ProjectComponent::setId);
    }
}
