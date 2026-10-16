package com.iwomi.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Streaming client for any chat AI provider.
 *  - provider "openai": any OpenAI-compatible API (OpenAI, Google Gemini, Groq, OpenRouter, Together, Mistral, Ollama, LM Studio...)
 *  - provider "anthropic": Claude
 *  - provider "cursor": a Cursor API key (crsr_...). Cursor has no chat-completions endpoint, so each reply
 *    is a short-lived no-repo Cloud Agent. Only the key is required; the model defaults to the account's choice.
 * Text arrives chunk by chunk so the UI can show (and speak) the answer while it is being written.
 */
@Service
public class LlmClient {

    public record ChatMessage(String role, String content) {}

    private static final int MAX_HISTORY = 20;

    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final boolean anthropic;
    private final boolean cursor;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String maxTokensParam;

    public LlmClient(ObjectMapper om,
                     @Value("${hub.ai.provider:}") String provider,
                     @Value("${hub.ai.api-key:}") String apiKey,
                     @Value("${hub.ai.base-url:}") String baseUrl,
                     @Value("${hub.ai.model:}") String model,
                     @Value("${hub.ai.max-tokens-param:max_tokens}") String maxTokensParam) {
        this.om = om;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String p = provider == null ? "" : provider.trim().toLowerCase();
        if (p.isEmpty()) p = detect(this.apiKey);
        this.anthropic = "anthropic".equals(p);
        this.cursor = "cursor".equals(p);
        // defaults per provider: { base url, model }; any of them can be overridden with HUB_AI_BASE_URL / HUB_AI_MODEL.
        // Cursor's model is left blank so the account default is used unless HUB_AI_MODEL is set.
        String[] d = switch (p) {
            case "anthropic" -> new String[]{"https://api.anthropic.com", "claude-sonnet-5-5"};
            case "gemini" -> new String[]{"https://generativelanguage.googleapis.com/v1beta/openai", "gemini-flash-latest"};
            case "groq" -> new String[]{"https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"};
            case "openrouter" -> new String[]{"https://openrouter.ai/api/v1", "openrouter/auto"};
            case "ollama" -> new String[]{"http://localhost:11434/v1", "llama3.1"};
            case "cursor" -> new String[]{"https://api.cursor.com", ""};
            default -> new String[]{"https://api.openai.com/v1", "gpt-4o-mini"};
        };
        String url = (baseUrl == null || baseUrl.isBlank()) ? d[0] : baseUrl.trim();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.model = (model == null || model.isBlank()) ? d[1] : model.trim();
        this.maxTokensParam = maxTokensParam;
    }

    /** One variable is enough: the provider is recognised from the key's prefix. */
    private static String detect(String key) {
        if (key.startsWith("crsr_") || key.startsWith("cursor_")) return "cursor";
        if (key.startsWith("sk-ant-")) return "anthropic";
        if (key.startsWith("sk-or-")) return "openrouter";
        if (key.startsWith("gsk_")) return "groq";
        if (key.startsWith("AIza")) return "gemini";
        return "openai";
    }

    private boolean localServer() {
        return baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1") || baseUrl.contains("host.docker.internal");
    }

    /** Local servers (Ollama, LM Studio) need no key. Every other provider, including Cursor, needs only the key. */
    public boolean configured() {
        return !apiKey.isBlank() || localServer();
    }

    public String notConfiguredMessage() {
        return "The AI assistant is not configured yet. Ask the administrator to set HUB_AI_API_KEY (see README).";
    }

    /** Streams the answer. onText receives each chunk; the call returns early when cancelled returns true. */
    public void stream(String system, List<ChatMessage> history, int maxTokens, Consumer<String> onText,
                       BooleanSupplier cancelled) throws Exception {
        if (cursor) {
            streamCursor(system, history, onText, cancelled);
            return;
        }
        ArrayNode messages = om.createArrayNode();
        if (!anthropic) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", system);
        }
        int from = Math.max(0, history.size() - MAX_HISTORY);
        for (ChatMessage m : history.subList(from, history.size())) {
            if (m.content() == null || m.content().isBlank()) continue;
            ObjectNode node = messages.addObject();
            node.put("role", "assistant".equals(m.role()) ? "assistant" : "user");
            node.put("content", m.content().length() > 4000 ? m.content().substring(0, 4000) : m.content());
        }

        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.put("stream", true);
        HttpRequest.Builder request;
        if (anthropic) {
            body.put("max_tokens", maxTokens);
            body.put("system", system);
            body.set("messages", messages);
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                    .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
        } else {
            body.put(maxTokensParam, maxTokens);
            body.set("messages", messages);
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"));
            if (!apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest req = request.timeout(Duration.ofSeconds(120)).header("content-type", "application/json")
                .header("accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body))).build();

        HttpResponse<Stream<String>> res = http.send(req, HttpResponse.BodyHandlers.ofLines());
        if (res.statusCode() / 100 != 2) {
            String err = res.body().collect(Collectors.joining("\n"));
            throw new IllegalStateException("AI provider error " + res.statusCode() + ": "
                    + (err.length() > 400 ? err.substring(0, 400) : err));
        }
        try (Stream<String> lines = res.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                if (cancelled.getAsBoolean()) return;
                String line = it.next();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                JsonNode node = om.readTree(data);
                String text = anthropic ? anthropicText(node) : openAiText(node);
                if (text != null && !text.isEmpty()) onText.accept(text);
            }
        }
    }

    /** One question in, the whole text out (used by the accelerator and the KYC review). */
    public String complete(String system, String userText, int maxTokens) throws Exception {
        StringBuilder sb = new StringBuilder();
        stream(system, List.of(new ChatMessage("user", userText)), maxTokens, sb::append, () -> false);
        return sb.toString();
    }

    private static String anthropicText(JsonNode n) {
        String type = n.path("type").asText();
        if ("content_block_delta".equals(type) && "text_delta".equals(n.path("delta").path("type").asText())) {
            return n.path("delta").path("text").asText();
        }
        if ("error".equals(type)) throw new IllegalStateException("AI provider error: " + n.path("error").path("message").asText());
        return null;
    }

    private static String openAiText(JsonNode n) {
        JsonNode choices = n.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode content = choices.get(0).path("delta").path("content");
            if (content.isTextual()) return content.asText();
        } else if (n.has("error")) {
            throw new IllegalStateException("AI provider error: " + n.path("error").path("message").asText());
        }
        return null;
    }

    /**
     * Cursor's API key runs a Cloud Agent, not /chat/completions. A no-repo agent answers the chat,
     * its text is streamed, and the agent is deleted so nothing is left behind.
     */
    private void streamCursor(String system, List<ChatMessage> history, Consumer<String> onText,
                              BooleanSupplier cancelled) throws Exception {
        ObjectNode body = om.createObjectNode();
        body.put("name", "IWOMI Hub chat");
        body.putObject("prompt").put("text", cursorPrompt(system, history));
        if (!model.isBlank()) body.putObject("model").put("id", model);

        HttpRequest create = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/agents"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();
        HttpResponse<String> created = http.send(create, HttpResponse.BodyHandlers.ofString());
        if (created.statusCode() / 100 != 2) {
            throw new IllegalStateException("AI provider error " + created.statusCode() + ": " + clip(created.body()));
        }
        JsonNode root = om.readTree(created.body());
        String agentId = root.path("agent").path("id").asText("");
        String runId = root.path("run").path("id").asText("");
        if (runId.isBlank()) runId = root.path("agent").path("latestRunId").asText("");
        if (agentId.isBlank() || runId.isBlank()) {
            throw new IllegalStateException("AI provider error: Cursor did not return an agent run");
        }

        try {
            if (cancelled.getAsBoolean()) {
                cancelRun(agentId, runId);
                return;
            }
            HttpRequest streamReq = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/agents/" + agentId + "/runs/" + runId + "/stream"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<Stream<String>> res = http.send(streamReq, HttpResponse.BodyHandlers.ofLines());
            if (res.statusCode() / 100 != 2) {
                String err = res.body().collect(Collectors.joining("\n"));
                throw new IllegalStateException("AI provider error " + res.statusCode() + ": " + clip(err));
            }
            boolean[] sawText = {false};
            try (Stream<String> lines = res.body()) {
                Iterator<String> it = lines.iterator();
                String event = "";
                StringBuilder data = new StringBuilder();
                while (it.hasNext()) {
                    if (cancelled.getAsBoolean()) {
                        cancelRun(agentId, runId);
                        return;
                    }
                    String line = it.next();
                    if (line.isEmpty()) {
                        emitCursorEvent(event, data.toString(), onText, sawText);
                        event = "";
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith("event:")) event = line.substring(6).trim();
                    else if (line.startsWith("data:")) {
                        if (data.length() > 0) data.append('\n');
                        data.append(line.substring(5).trim());
                    }
                }
                emitCursorEvent(event, data.toString(), onText, sawText);
            }
        } finally {
            deleteAgent(agentId);
        }
    }

    private String cursorPrompt(String system, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("Answer this chat. There is no repository. Do not use tools, edit files, or run commands. Reply with the answer only.\n\n");
        sb.append("Instructions:\n").append(system == null ? "" : system).append("\n\nConversation:\n");
        int from = Math.max(0, history.size() - MAX_HISTORY);
        for (ChatMessage m : history.subList(from, history.size())) {
            if (m.content() == null || m.content().isBlank()) continue;
            String content = m.content().length() > 4000 ? m.content().substring(0, 4000) : m.content();
            sb.append("assistant".equals(m.role()) ? "Assistant: " : "User: ").append(content).append("\n\n");
        }
        sb.append("Reply to the latest user message.");
        return sb.toString();
    }

    private void emitCursorEvent(String event, String data, Consumer<String> onText, boolean[] sawText) throws Exception {
        if (data == null || data.isBlank()) return;
        JsonNode node = om.readTree(data);
        if ("assistant".equals(event)) {
            String text = node.path("text").asText("");
            if (!text.isEmpty()) {
                sawText[0] = true;
                onText.accept(text);
            }
        } else if ("error".equals(event)) {
            throw new IllegalStateException("AI provider error: " + node.path("message").asText(clip(data)));
        } else if ("result".equals(event)) {
            String status = node.path("status").asText("");
            if ("ERROR".equals(status) || "EXPIRED".equals(status)) {
                String detail = node.path("text").asText("");
                throw new IllegalStateException("AI provider error: " + (detail.isBlank() ? status : detail));
            }
            if (!sawText[0]) {
                String text = node.path("text").asText("");
                if (!text.isEmpty()) onText.accept(text);
            }
        }
    }

    private void cancelRun(String agentId, String runId) {
        try {
            http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/agents/" + agentId + "/runs/" + runId + "/cancel"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // the agent is deleted next either way
        }
    }

    private void deleteAgent(String agentId) {
        try {
            http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/agents/" + agentId))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .DELETE()
                    .build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private static String clip(String err) {
        if (err == null) return "";
        return err.length() > 400 ? err.substring(0, 400) : err;
    }
}
