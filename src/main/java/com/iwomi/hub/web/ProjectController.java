package com.iwomi.hub.web;

import com.iwomi.hub.domain.Project;
import com.iwomi.hub.repo.ProjectRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController extends CrudController<Project, ProjectRepository> {
    public ProjectController(ProjectRepository repo) {
        super(repo, Project::setId);
    }
}
