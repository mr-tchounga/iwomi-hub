package com.iwomi.hub.web;

import com.iwomi.hub.domain.Partner;
import com.iwomi.hub.repo.PartnerRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/partners")
public class PartnerController extends CrudController<Partner, PartnerRepository> {
    public PartnerController(PartnerRepository repo) {
        super(repo, Partner::setId);
    }
}
