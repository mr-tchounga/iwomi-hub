package com.iwomi.hub.web;

import com.iwomi.hub.domain.RoutingRule;
import com.iwomi.hub.repo.RoutingRuleRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController extends CrudController<RoutingRule, RoutingRuleRepository> {
    public RoutingRuleController(RoutingRuleRepository repo) {
        super(repo, RoutingRule::setId);
    }
}
