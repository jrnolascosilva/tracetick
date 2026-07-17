/**
 * Outbound notifications: interface stub only in v1.
 *
 * <p>Exposes {@link com.tracetick.notifications.NotificationSink} so the rest of the system can
 * publish events without depending on a concrete transport. No email or webhook-out
 * implementation is provided in v1.
 */
package com.tracetick.notifications;