package com.tracetick.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetick.domain.Customer;
import com.tracetick.domain.IngestionConfiguration;
import com.tracetick.domain.Severity;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketState;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.IngestionConfigurationRepository;
import com.tracetick.persistence.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Webhook ingest pipeline. Steps:
 *
 * <ol>
 *   <li>Look up the {@link IngestionConfiguration} by URL token. Reject (401) on unknown
 *       token, inactive config, missing signature header, or HMAC mismatch — these are
 *       indistinguishable from the caller's perspective to avoid leaking which tokens exist.</li>
 *   <li>Parse the body as a JSON object. Reject (400) on malformed JSON, non-object
 *       payloads, or any field whose type is not what the v1 contract expects.</li>
 *   <li>Extract severity (payload overrides config default) and labels (merged with config
 *       defaults; payload values win on key collision).</li>
 *   <li>Extract alert_identity from the payload and compute the fingerprint per ADR-0004.
 *       If no alert_identity is present, fingerprint is null and dedup is skipped.</li>
 *   <li>Look up an existing active ticket (state in {@link #ACTIVE_STATES}) for the
 *       fingerprint. If found, record a refire and return it (200). Otherwise, create a
 *       new ticket (201).</li>
 * </ol>
 *
 * <p>v1 deliberately does not extract title or description from the payload — those fields
 * land in {@code raw_payload} and the ticket carries generic title/description text. The
 * only fields the v1 pipeline reads are {@code severity} and {@code labels} (per
 * implementation decisions in the parent spec).
 */
@Service
public class WebhookIngestService {

    private static final Set<TicketState> ACTIVE_STATES = EnumSet.of(
            TicketState.OPEN, TicketState.IN_PROGRESS, TicketState.RESOLVED);

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final IngestionConfigurationRepository ingestionConfigurationRepository;
    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;

    public WebhookIngestService(IngestionConfigurationRepository ingestionConfigurationRepository,
                                TicketRepository ticketRepository,
                                CustomerRepository customerRepository,
                                ObjectMapper objectMapper) {
        this.ingestionConfigurationRepository = ingestionConfigurationRepository;
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestResult ingest(String urlToken, byte[] rawBody, String signatureHeader) {
        IngestionConfiguration config = resolveActiveConfiguration(urlToken);
        verifySignature(rawBody, signatureHeader, config);
        Map<String, Object> payload = parsePayload(rawBody);

        Severity severity = SeverityExtractor.extract(stringField(payload, "severity"),
                config.getDefaultSeverity());
        Map<String, String> mergedTags = mergedTags(config.getDefaultTags(),
                stringMapField(payload, "labels"));
        List<Tag> tags = LabelExtractor.extract(mergedTags);
        String alertIdentity = AlertIdentityExtractor.extract(payload);
        String fingerprint = alertIdentity == null ? null : config.getId() + ":" + alertIdentity;

        if (fingerprint != null) {
            var existing = ticketRepository.findFirstByFingerprintAndStateIn(fingerprint, ACTIVE_STATES);
            if (existing.isPresent()) {
                Ticket ticket = existing.get();
                ticket.recordRefire();
                return new IngestResult(ticket, false);
            }
        }

        Ticket ticket = Ticket.createWebhook(
                resolveCustomer(config),
                config.getDefaultAssignee(),
                config,
                genericTitle(config.getName()),
                genericDescription(config.getName()),
                severity,
                fingerprint,
                payload,
                tags);
        Ticket saved = ticketRepository.save(ticket);
        return new IngestResult(saved, true);
    }

    private IngestionConfiguration resolveActiveConfiguration(String urlToken) {
        IngestionConfiguration config = urlToken == null
                ? null
                : ingestionConfigurationRepository.findByUrlToken(urlToken).orElse(null);
        if (config == null || !config.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook credentials");
        }
        return config;
    }

    private static void verifySignature(byte[] rawBody, String signatureHeader, IngestionConfiguration config) {
        boolean valid = HmacSignatureVerifier.verify(rawBody, signatureHeader, config.getHmacSecret());
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
    }

    private Map<String, Object> parsePayload(byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty body");
        }
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, OBJECT_MAP);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload");
        }
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload must be a JSON object");
        }
        return payload;
    }

    private Customer resolveCustomer(IngestionConfiguration config) {
        if (config.getDefaultAssignee() != null) {
            return config.getDefaultAssignee().getCustomer();
        }
        List<Customer> customers = customerRepository.findAll();
        if (customers.size() != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot resolve Customer for webhook ticket");
        }
        return customers.get(0);
    }

    private static String genericTitle(String configName) {
        return "Alert from " + configName;
    }

    private static String genericDescription(String configName) {
        return "Alert ingested from " + configName + ". See raw_payload for details.";
    }

    private static Map<String, String> mergedTags(Map<String, String> defaults, Map<String, String> payloadLabels) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (defaults != null) {
            merged.putAll(defaults);
        }
        if (payloadLabels != null) {
            merged.putAll(payloadLabels);
        }
        return merged;
    }

    private static String stringField(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Field '" + fieldName + "' must be a string");
        }
        return s;
    }

    private static Map<String, String> stringMapField(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Field '" + fieldName + "' must be a JSON object");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Field '" + fieldName + "' keys must be strings");
            }
            Object entryValue = entry.getValue();
            if (entryValue == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Field '" + fieldName + "' value for key '" + key + "' must not be null");
            }
            if (!(entryValue instanceof String stringValue)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Field '" + fieldName + "' value for key '" + key + "' must be a string");
            }
            out.put(key, stringValue);
        }
        return out;
    }

    public record IngestResult(Ticket ticket, boolean created) {
    }
}
