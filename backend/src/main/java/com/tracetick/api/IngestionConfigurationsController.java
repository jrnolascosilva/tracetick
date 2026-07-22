package com.tracetick.api;

import com.tracetick.api.dto.CreateIngestionConfigurationRequest;
import com.tracetick.api.dto.IngestionConfigurationDto;
import com.tracetick.api.dto.UpdateIngestionConfigurationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ingestion-configurations")
public class IngestionConfigurationsController {

    private final IngestionConfigurationService service;

    public IngestionConfigurationsController(IngestionConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('TECHNICIAN')")
    public List<IngestionConfigurationDto> list() {
        return service.list().stream().map(IngestionConfigurationDto::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IngestionConfigurationDto get(@PathVariable Long id) {
        return IngestionConfigurationDto.from(service.load(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<IngestionConfigurationDto> create(
            @Valid @RequestBody CreateIngestionConfigurationRequest request) {
        var saved = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/ingestion-configurations/" + saved.getId()))
                .body(IngestionConfigurationDto.exposingSecret(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IngestionConfigurationDto update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateIngestionConfigurationRequest request) {
        var updated = service.update(id, request);
        return Boolean.TRUE.equals(request.rotateSecret())
                ? IngestionConfigurationDto.exposingSecret(updated)
                : IngestionConfigurationDto.from(updated);
    }
}
