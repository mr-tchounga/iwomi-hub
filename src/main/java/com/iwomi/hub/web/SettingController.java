package com.iwomi.hub.web;

import com.iwomi.hub.domain.Setting;
import com.iwomi.hub.repo.SettingRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingRepository settings;

    public SettingController(SettingRepository settings) {
        this.settings = settings;
    }

    @GetMapping
    public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        settings.findAll().forEach(s -> out.put(s.getKey(), s.getValue()));
        return out;
    }

    @PutMapping
    public Map<String, String> put(@RequestBody Setting s) {
        if (s.getKey() == null || s.getKey().isBlank()) throw new IllegalArgumentException("key is required");
        settings.save(s);
        return all();
    }
}
