package com.iwomi.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iwomi.hub.web.AiException;
import org.springframework.stereotype.Service;

/** Turns requirements or an API specification (OpenAPI, SOAP/WSDL, plain text) into a delivery pack. */
@Service
public class AcceleratorService {

    private static final String SYSTEM = """
        You are a senior business analyst and QA lead at a fintech software company that builds software for banks.
        The user pastes client requirements or an API specification (OpenAPI/Swagger, SOAP/WSDL, or plain text).
        Produce a delivery pack as ONE JSON object and nothing else (no markdown fences, no commentary), with exactly this shape:
        {
          "summary": "2-3 sentences describing what the system must do",
          "userStories": [
            {"title": "", "asA": "", "iWant": "", "soThat": "", "priority": "HIGH|MEDIUM|LOW", "acceptanceCriteria": ["..."]}
          ],
          "apiDocumentation": "Markdown documentation of each endpoint or operation: purpose, request, response, error cases, a short example. Empty string if the input has no API.",
          "testCases": [
            {"id": "TC-01", "title": "", "type": "FUNCTIONAL|NEGATIVE|SECURITY|EDGE", "relatedStory": "title of the user story it covers",
             "steps": ["..."], "expected": "", "priority": "HIGH|MEDIUM|LOW"}
          ],
          "risksAndQuestions": ["open questions and risks to clarify with the client"]
        }
        Rules: at most 6 user stories and 12 test cases; be concrete and specific to the input, never generic filler.
        Banking context: always consider amount validation and rounding, idempotency and duplicate submissions, authorization and roles,
        audit trail, error handling and timeouts, and data protection. Base everything only on the provided material;
        put anything unclear in risksAndQuestions instead of guessing. Write in the language of the input.
        """;

    private final LlmClient llm;
    private final ObjectMapper om;

    public AcceleratorService(LlmClient llm, ObjectMapper om) {
        this.llm = llm;
        this.om = om;
    }

    public JsonNode generate(String sourceText) throws Exception {
        if (!llm.configured()) throw new AiException(llm.notConfiguredMessage());
        String raw = llm.complete(SYSTEM, "Source material:\n\n" + sourceText, 6000);
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new AiException("The AI answer was not in the expected format. Please try again.");
        try {
            return om.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            throw new AiException("The AI answer was cut or malformed. Try again with a shorter specification.");
        }
    }
}
