package com.tracetick.persistence;

import com.tracetick.domain.Role;
import com.tracetick.domain.Severity;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketState;
import com.tracetick.domain.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> hasState(TicketState state) {
        return (root, query, cb) -> state == null ? null : cb.equal(root.get("state"), state);
    }

    public static Specification<Ticket> hasSeverity(Severity severity) {
        return (root, query, cb) -> severity == null ? null : cb.equal(root.get("severity"), severity);
    }

    public static Specification<Ticket> hasAssignee(Long assigneeUserId) {
        return (root, query, cb) -> assigneeUserId == null
                ? null
                : cb.equal(root.get("assignee").get("id"), assigneeUserId);
    }

    public static Specification<Ticket> hasTag(String tagKey, String tagValue) {
        if (tagKey == null || tagValue == null) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Ticket, Tag> tags = root.join("tags", JoinType.INNER);
            return cb.and(
                    cb.equal(tags.get("key"), tagKey),
                    cb.equal(tags.get("value"), tagValue));
        };
    }

    public static Specification<Ticket> searchFreeText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String like = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("description")), like));
    }

    public static Specification<Ticket> visibleTo(User viewer) {
        if (viewer == null) {
            return null;
        }
        if (viewer.getRole() == Role.TECHNICIAN) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("reporter").get("id"), viewer.getId());
    }
}
