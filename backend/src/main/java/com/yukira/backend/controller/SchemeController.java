package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schemes")
public class SchemeController {

    private final SchemeRepository schemeRepository;
    private final SchemeOptionRepository schemeOptionRepository;

    public SchemeController(SchemeRepository schemeRepository, SchemeOptionRepository schemeOptionRepository) {
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
    }

    @GetMapping
    public ResponseEntity<List<Scheme>> getAllSchemes() {
        return ResponseEntity.ok(schemeRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Scheme> getSchemeById(@PathVariable Long id) {
        return schemeRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/options")
    public ResponseEntity<List<SchemeOption>> getAllOptions() {
        return ResponseEntity.ok(schemeOptionRepository.findAll());
    }

    @GetMapping("/{id}/options")
    public ResponseEntity<List<SchemeOption>> getOptionsBySchemeId(@PathVariable Long id) {
        return ResponseEntity.ok(schemeOptionRepository.findByPlanSchemeId(id));
    }
}
