package com.iwomi.hub.web;

import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.config.RateLimiter;
import com.iwomi.hub.service.AssistantService;
import com.iwomi.hub.service.LlmClient.ChatMessage;
import com.iwomi.hub.service.PortalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** Open to everyone (no login): catalog, anonymous help request, public AI assistant. */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    public record PortalChatRequest(List<ChatMessage> messages, String language) {}

    private final PortalService portal;
    private final AssistantService assistant;
    private final RateLimiter limiter;

    public PortalController(PortalService portal, AssistantService assistant, RateLimiter limiter) {
        this.portal = portal;
        this.assistant = assistant;
        this.limiter = limiter;
    }

    @GetMapping("/projects")
    public List<PortalService.CatalogItem> projects() {
        return portal.catalog();
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> request(@Valid @RequestBody PortalService.PortalRequest r, HttpServletRequest http) {
        if (!limiter.allow("portal-request:" + RateLimiter.ip(http), 5, 3600)) {
            throw new TooManyRequestsException("Too many requests from this address. Please try again later.");
        }
        return portal.createAnonymousRequest(r);
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody PortalChatRequest r, HttpServletRequest http) {
        if (r.messages() == null || r.messages().isEmpty()) throw new IllegalArgumentException("messages is required");
        String ip = RateLimiter.ip(http);
        if (!limiter.allow("portal-chat:" + ip, 60, 3600)) {
            throw new TooManyRequestsException("You have asked many questions recently. Please try again in a while.");
        }
        return assistant.streamPortal(r.messages(), r.language(), CurrentUser.get(), ip);
    }
}
