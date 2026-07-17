package com.tracetick.notifications;

import java.util.Map;

/**
 * Outbound notification seam. In v1 this is an interface only — no concrete implementation
 * exists, and the application does not configure a {@code NotificationSink} bean. Subsequent
 * specs layer email and webhook-out transports on top of this contract.
 */
public interface NotificationSink {

    /**
     * Publish a notification.
     *
     * @param eventKey a short event key (e.g. {@code ticket.state-changed},
     *                 {@code ticket.commented}); intentionally distinct from the
     *                 {@code com.tracetick.domain.Event} domain type to avoid name collision.
     * @param payload free-form, transport-agnostic event payload
     */
    void publish(String eventKey, Map<String, Object> payload);
}