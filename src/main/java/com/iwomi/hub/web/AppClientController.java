package com.iwomi.hub.web;

import com.iwomi.hub.domain.AppClient;
import com.iwomi.hub.repo.AppClientRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app-clients")
public class AppClientController extends CrudController<AppClient, AppClientRepository> {
    public AppClientController(AppClientRepository repo) {
        super(repo, AppClient::setId);
    }
}
