package com.iwomi.hub.service;

import com.iwomi.hub.domain.Opportunity;
import com.iwomi.hub.repo.OpportunityRepository;
import com.iwomi.hub.web.AiException;
import com.iwomi.hub.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Advice grounded only in stored opportunities, each of which has a public source. */
@Service
public class OpportunityService {

    private static final String SYSTEM = """
        You help an IWOMI Technologies staff member use bank and public-market opportunities.
        You are not a licensed financial adviser and you do not manage their money.
        Rules:
        - Recommend only opportunities from the catalog below. Never invent a product, a rate, a minimum, a fee, or a bank offer.
        - Every recommendation must name the opportunity and include its source URL on its own line so the reader can open it.
        - If a figure is not in the catalog, say to read it on the source page.
        - Separate money they must keep for living costs or payroll from money that can wait.
        - When a monthly salary in XAF is given, suggest a concrete split (spend / keep liquid / set aside) using only catalog opportunities. Round to whole francs.
        - When they ask about one opportunity, explain who it is for and what to check on the source before acting.
        - If nothing in the catalog fits, say so. Do not fill the gap with general products.
        - Write in the language of the question. If they did not write a question, write in English.
        - Keep it short: a few paragraphs or a short list. No preamble about being an AI.
        """;

    private final OpportunityRepository opportunities;
    private final LlmClient llm;

    public OpportunityService(OpportunityRepository opportunities, LlmClient llm) {
        this.opportunities = opportunities;
        this.llm = llm;
    }

    public void validate(Opportunity o) {
        if (o.getTitle() == null || o.getTitle().isBlank() || o.getTitle().length() > 200) {
            throw new IllegalArgumentException("Title is required (200 characters max)");
        }
        if (o.getSummary() == null || o.getSummary().isBlank() || o.getSummary().length() > 2000) {
            throw new IllegalArgumentException("Summary is required (2000 characters max)");
        }
        if (o.getAudience() == null || !o.getAudience().matches("PERSONAL|COMPANY|BOTH")) {
            throw new IllegalArgumentException("Audience must be PERSONAL, COMPANY, or BOTH");
        }
        if (o.getSourceName() == null || o.getSourceName().isBlank() || o.getSourceName().length() > 200) {
            throw new IllegalArgumentException("Source name is required");
        }
        String url = o.getSourceUrl() == null ? "" : o.getSourceUrl().trim();
        if (!url.matches("https://\\S{8,480}")) {
            throw new IllegalArgumentException("Source must be an https link");
        }
        o.setTitle(o.getTitle().trim());
        o.setSummary(o.getSummary().trim());
        o.setSourceName(o.getSourceName().trim());
        o.setSourceUrl(url);
    }

    public String advise(BigDecimal salary, String question, Long opportunityId) throws Exception {
        if (!llm.configured()) throw new AiException(llm.notConfiguredMessage());
        List<Opportunity> catalog = opportunities.findByActiveTrueOrderByIdAsc();
        if (catalog.isEmpty()) throw new AiException("No opportunities are published yet.");
        Opportunity focus = null;
        if (opportunityId != null) {
            focus = catalog.stream().filter(o -> o.getId().equals(opportunityId)).findFirst()
                    .orElseThrow(() -> new NotFoundException("Opportunity not found"));
        }
        String q = question == null ? "" : question.trim();
        if (salary == null && q.isEmpty() && focus == null) {
            throw new IllegalArgumentException("Give a salary, a question, or an opportunity");
        }
        if (salary != null && (salary.signum() < 0 || salary.compareTo(new BigDecimal("100000000000")) > 0)) {
            throw new IllegalArgumentException("Salary must be a positive amount in XAF");
        }
        if (q.length() > 2000) throw new IllegalArgumentException("Question is too long");

        StringBuilder user = new StringBuilder();
        user.append("Catalog (the only allowed recommendations):\n");
        for (Opportunity o : catalog) {
            user.append("- [").append(o.getId()).append("] ").append(o.getTitle())
                    .append(" (").append(o.getAudience()).append(")\n  ")
                    .append(o.getSummary()).append("\n  Source: ").append(o.getSourceName())
                    .append("\n  ").append(o.getSourceUrl()).append("\n");
        }
        if (focus != null) {
            user.append("\nThey asked about opportunity ").append(focus.getId())
                    .append(" (").append(focus.getTitle()).append("). Stay on that idea, still using the catalog rules.\n");
        }
        if (salary != null) user.append("\nMonthly salary: ").append(salary.toPlainString()).append(" XAF.\n");
        if (!q.isEmpty()) user.append("\nQuestion: ").append(q).append("\n");
        return llm.complete(SYSTEM, user.toString(), 1200);
    }
}
