package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.config.RateLimiter;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.domain.Opportunity;
import com.iwomi.hub.repo.OpportunityRepository;
import com.iwomi.hub.service.OpportunityService;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Stored savings and investment ideas. Personal advice is for internal staff, not admins or customers. */
@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {

    public record AdviceRequest(BigDecimal salary, @Size(max = 2000) String question, Long opportunityId) {}
    public record AdviceResponse(String advice) {}

    private final OpportunityRepository repo;
    private final OpportunityService service;
    private final RateLimiter limiter;

    public OpportunityController(OpportunityRepository repo, OpportunityService service, RateLimiter limiter) {
        this.repo = repo;
        this.service = service;
        this.limiter = limiter;
    }

    @GetMapping
    public List<Opportunity> list() {
        AuthUser user = CurrentUser.require();
        if (user.is(Role.ADMIN)) return repo.findAllByOrderByIdAsc();
        return repo.findByActiveTrueOrderByIdAsc();
    }

    @PostMapping("/advice")
    public AdviceResponse advice(@RequestBody(required = false) AdviceRequest r) throws Exception {
        AuthUser user = CurrentUser.require();
        if (user.is(Role.ADMIN) || user.is(Role.EXTERNAL)) {
            throw new ForbiddenException("Personal opportunity advice is for internal staff");
        }
        if (!limiter.allow("opp-advice:" + user.id(), 20, 3600)) {
            throw new TooManyRequestsException("Too many questions this hour.");
        }
        AdviceRequest body = r == null ? new AdviceRequest(null, null, null) : r;
        try {
            return new AdviceResponse(service.advise(body.salary(), body.question(), body.opportunityId()));
        } catch (AiException | NotFoundException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(e.getMessage() == null ? "The assistant could not answer" : e.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Opportunity create(@RequestBody Opportunity body) {
        body.setId(null);
        body.setCreatedAt(null);
        service.validate(body);
        return repo.save(body);
    }

    @PutMapping("/{id}")
    public Opportunity update(@PathVariable Long id, @RequestBody Opportunity body) {
        Opportunity existing = repo.findById(id).orElseThrow(() -> new NotFoundException("Opportunity not found"));
        body.setId(id);
        body.setCreatedAt(existing.getCreatedAt());
        service.validate(body);
        return repo.save(body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repo.existsById(id)) throw new NotFoundException("Opportunity not found");
        repo.deleteById(id);
    }
}
