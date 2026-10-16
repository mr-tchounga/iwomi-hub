package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.config.RateLimiter;
import com.iwomi.hub.service.AssistantService;
import com.iwomi.hub.service.LlmClient.ChatMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** Staff assistant, streamed word by word. It only sees data the user's role may see. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record ChatRequest(List<ChatMessage> messages) {}

    private final AssistantService assistant;
    private final RateLimiter limiter;

    public ChatController(AssistantService assistant, RateLimiter limiter) {
        this.assistant = assistant;
        this.limiter = limiter;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        AuthUser user = CurrentUser.require();
        if (request.messages() == null || request.messages().isEmpty()) throw new IllegalArgumentException("messages is required");
        if (!limiter.allow("staff-chat:" + user.id(), 300, 3600)) throw new TooManyRequestsException("Too many questions this hour.");
        return assistant.streamStaff(request.messages(), user);
    }
}
