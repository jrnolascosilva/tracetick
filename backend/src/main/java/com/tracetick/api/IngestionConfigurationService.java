package com.tracetick.api;

import com.tracetick.api.dto.CreateIngestionConfigurationRequest;
import com.tracetick.api.dto.UpdateIngestionConfigurationRequest;
import com.tracetick.domain.IngestionConfiguration;
import com.tracetick.domain.Role;
import com.tracetick.domain.User;
import com.tracetick.ingest.IngestionTokenGenerator;
import com.tracetick.persistence.IngestionConfigurationRepository;
import com.tracetick.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class IngestionConfigurationService {

    private static final int TOKEN_UNIQUENESS_ATTEMPTS = 4;

    private final IngestionConfigurationRepository repository;
    private final UserRepository userRepository;

    public IngestionConfigurationService(IngestionConfigurationRepository repository,
                                         UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<IngestionConfiguration> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public IngestionConfiguration load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "IngestionConfiguration not found"));
    }

    @Transactional
    public IngestionConfiguration create(CreateIngestionConfigurationRequest request) {
        User assignee = loadAssignee(request.defaultAssigneeUserId());
        Map<String, String> tags = sanitizedTags(request.defaultTags());
        IngestionConfiguration config = IngestionConfiguration.create(
                request.name().trim(),
                uniqueUrlToken(),
                IngestionTokenGenerator.hmacSecret(),
                request.defaultSeverity(),
                assignee,
                tags);
        return repository.save(config);
    }

    @Transactional
    public IngestionConfiguration update(Long id, UpdateIngestionConfigurationRequest request) {
        IngestionConfiguration existing = load(id);
        if (request.name() != null && !request.name().isBlank()) {
            existing.rename(request.name().trim());
        }
        if (request.defaultSeverity() != null) {
            existing.changeDefaultSeverity(request.defaultSeverity());
        }
        if (request.defaultAssigneeUserId() != null) {
            existing.changeDefaultAssignee(loadAssignee(request.defaultAssigneeUserId()));
        }
        if (request.defaultTags() != null) {
            existing.replaceDefaultTags(sanitizedTags(request.defaultTags()));
        }
        if (Boolean.TRUE.equals(request.active())) {
            existing.activate();
        } else if (Boolean.FALSE.equals(request.active())) {
            existing.deactivate();
        }
        if (Boolean.TRUE.equals(request.rotateSecret())) {
            existing.rotateSecret(IngestionTokenGenerator.hmacSecret());
        }
        return existing;
    }

    private User loadAssignee(Long userId) {
        if (userId == null) {
            return null;
        }
        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee user not found"));
        if (assignee.getRole() != Role.TECHNICIAN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Default assignee must have TECHNICIAN role");
        }
        if (!assignee.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Default assignee must be active");
        }
        return assignee;
    }

    private static Map<String, String> sanitizedTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> cleaned = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag key must not be blank");
            }
            if (value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tag value for key '" + key + "' must not be null");
            }
            cleaned.put(key, value);
        }
        return cleaned;
    }

    private String uniqueUrlToken() {
        for (int attempt = 0; attempt < TOKEN_UNIQUENESS_ATTEMPTS; attempt++) {
            String token = IngestionTokenGenerator.urlToken();
            if (!repository.existsByUrlToken(token)) {
                return token;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not allocate a unique URL token");
    }
}
