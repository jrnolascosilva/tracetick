package com.tracetick.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "ingestion_configurations")
@Getter
public class IngestionConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url_token", nullable = false, unique = true, length = 64)
    private String urlToken;

    @Column(name = "hmac_secret", nullable = false, length = 128)
    private String hmacSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_severity", nullable = false, length = 16)
    private Severity defaultSeverity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_assignee_user_id")
    private User defaultAssignee;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_tags", columnDefinition = "jsonb")
    private Map<String, String> defaultTags = new LinkedHashMap<>();

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IngestionConfiguration() {
    }

    private IngestionConfiguration(String name, String urlToken, String hmacSecret,
                                   Severity defaultSeverity, User defaultAssignee,
                                   Map<String, String> defaultTags, boolean active, Instant createdAt) {
        this.name = name;
        this.urlToken = urlToken;
        this.hmacSecret = hmacSecret;
        this.defaultSeverity = defaultSeverity;
        this.defaultAssignee = defaultAssignee;
        if (defaultTags != null) {
            this.defaultTags.putAll(defaultTags);
        }
        this.active = active;
        this.createdAt = createdAt;
    }

    public static IngestionConfiguration create(String name,
                                                String urlToken,
                                                String hmacSecret,
                                                Severity defaultSeverity,
                                                User defaultAssignee,
                                                Map<String, String> defaultTags) {
        return new IngestionConfiguration(
                name,
                urlToken,
                hmacSecret,
                defaultSeverity == null ? Severity.MEDIUM : defaultSeverity,
                defaultAssignee,
                defaultTags,
                true,
                Instant.now());
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void changeDefaultSeverity(Severity severity) {
        this.defaultSeverity = severity;
    }

    public void changeDefaultAssignee(User assignee) {
        this.defaultAssignee = assignee;
    }

    public void replaceDefaultTags(Map<String, String> tags) {
        this.defaultTags.clear();
        if (tags != null) {
            this.defaultTags.putAll(tags);
        }
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void rotateSecret(String newSecret) {
        this.hmacSecret = newSecret;
    }

    public Map<String, String> getDefaultTags() {
        return Map.copyOf(defaultTags);
    }
}
