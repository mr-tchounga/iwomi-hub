package com.iwomi.hub.web;

import com.iwomi.hub.domain.ProjectPartner;
import com.iwomi.hub.repo.ProjectPartnerRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SaleController extends CrudController<ProjectPartner, ProjectPartnerRepository> {
    public SaleController(ProjectPartnerRepository repo) {
        super(repo, ProjectPartner::setId);
    }
}
