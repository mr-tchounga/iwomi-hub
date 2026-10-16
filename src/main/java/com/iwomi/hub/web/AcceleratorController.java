package com.iwomi.hub.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.iwomi.hub.service.AcceleratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/accelerator")
public class AcceleratorController {

    public record GenerateRequest(@NotBlank @Size(max = 30000) String text) {}

    private final AcceleratorService accelerator;

    public AcceleratorController(AcceleratorService accelerator) {
        this.accelerator = accelerator;
    }

    @PostMapping("/generate")
    public Map<String, JsonNode> generate(@Valid @RequestBody GenerateRequest r) throws Exception {
        return Map.of("result", accelerator.generate(r.text()));
    }
}
