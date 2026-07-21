package com.tracetick.api;

import com.tracetick.api.dto.CommentRequest;
import com.tracetick.api.dto.CreateTicketRequest;
import com.tracetick.api.dto.UpdateTicketRequest;
import com.tracetick.domain.Customer;
import com.tracetick.domain.InvalidTicketStateException;
import com.tracetick.domain.Role;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketEvent;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.TicketEventRepository;
import com.tracetick.persistence.TicketRepository;
import com.tracetick.persistence.TicketSpecifications;
import com.tracetick.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.tracetick.api.dto.CreateTicketRequest;
import com.tracetick.api.dto.UpdateTicketRequest;
import com.tracetick.domain.Customer;
import com.tracetick.domain.InvalidTicketStateException;
import com.tracetick.domain.Role;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketEvent;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.TicketEventRepository;
import com.tracetick.persistence.TicketRepository;
import com.tracetick.persistence.TicketSpecifications;
import com.tracetick.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * CUSTOMER role-scoping rule is implemented in two places on purpose and must stay in
 * lock-step:
 * <ul>
 *   <li>List queries — {@code TicketSpecifications.visibleTo(viewer)} (SQL via JPA Criteria).</li>
 *   <li>Single-ticket access — {@link #ensureVisible(Ticket, User)} (in-memory, post-load).</li>
 * </ul>
 * Both must evaluate to: a CUSTOMER can see a ticket iff
 * {@code ticket.reporter == viewer} or {@code viewer} is in {@code ticket.watchers}.
 * Covered on both sides by {@code TicketSpecificationsTest.customerCanAlsoSeeTicketsTheyWatch}
 * and {@code TicketsControllerTest.watcherGainVisibilityForCustomer}.
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public TicketService(TicketRepository ticketRepository,
                         TicketEventRepository ticketEventRepository,
                         UserRepository userRepository,
                         CustomerRepository customerRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketEventRepository = ticketEventRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Ticket createHumanTicket(CreateTicketRequest request, User reporter) {
        Customer customer = reporter.getCustomer();
        List<Tag> tags = request.tags().stream()
                .map(input -> Tag.of(input.key(), input.value()))
                .toList();
        Ticket ticket = Ticket.createHuman(
                customer,
                reporter,
                request.title(),
                request.description(),
                request.severity(),
                tags);
        return ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> findTickets(User viewer,
                                    com.tracetick.domain.TicketState state,
                                    com.tracetick.domain.Severity severity,
                                    Long assigneeUserId,
                                    String tagKey,
                                    String tagValue,
                                    String search,
                                    Pageable pageable) {
        Specification<Ticket> spec = Specification.where(TicketSpecifications.visibleTo(viewer))
                .and(TicketSpecifications.hasState(state))
                .and(TicketSpecifications.hasSeverity(severity))
                .and(TicketSpecifications.hasAssignee(assigneeUserId))
                .and(TicketSpecifications.hasTag(tagKey, tagValue))
                .and(TicketSpecifications.searchFreeText(search));
        return ticketRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public TicketDetail loadDetail(Long ticketId, User viewer) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureVisible(ticket, viewer);
        List<TicketEvent> events = ticketEventRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
        return new TicketDetail(ticket, events);
    }

    @Transactional
    public Ticket update(Long ticketId, UpdateTicketRequest request, User actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureVisible(ticket, actor);
        applyUpdate(ticket, request, actor);
        return ticket;
    }

    @Transactional
    public TicketEvent addComment(Long ticketId, CommentRequest request, User actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureVisible(ticket, actor);
        return ticket.appendComment(actor, request.body());
    }

    @Transactional
    public Ticket addTag(Long ticketId, String key, String value, User actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureVisible(ticket, actor);
        Tag tag = Tag.of(key, value);
        ticket.addTag(tag, actor);
        return ticket;
    }

    @Transactional
    public Ticket removeTag(Long ticketId, String key, User actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureVisible(ticket, actor);
        ticket.removeTag(key, actor);
        return ticket;
    }

    @Transactional
    public Ticket addWatcher(Long ticketId, Long userId, User actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureVisible(ticket, actor);
        User watcher = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watcher user not found"));
        ticket.addWatcher(watcher, actor);
        return ticket;
    }

    @Transactional
    public Ticket removeWatcher(Long ticketId, Long userId, User actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureVisible(ticket, actor);
        User watcher = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watcher user not found"));
        ticket.removeWatcher(watcher, actor);
        return ticket;
    }

    private void applyUpdate(Ticket ticket, UpdateTicketRequest request, User actor) {
        if (request.severity() != null) {
            ticket.changeSeverity(request.severity(), actor);
        }
        if (Boolean.TRUE.equals(request.unassign())) {
            ticket.unassign(actor);
        } else if (request.assigneeUserId() != null) {
            User assignee = userRepository.findById(request.assigneeUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found"));
            if (assignee.getRole() != Role.TECHNICIAN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee must have TECHNICIAN role");
            }
            ticket.assign(assignee, actor);
        }
        if (request.state() != null) {
            try {
                ticket.transitionTo(request.state(), actor);
            } catch (InvalidTicketStateException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
            }
        }
    }

    private void ensureVisible(Ticket ticket, User viewer) {
        if (viewer.getRole() == Role.TECHNICIAN) {
            return;
        }
        Long viewerId = viewer.getId();
        if (ticket.getReporter() != null && viewerId.equals(ticket.getReporter().getId())) {
            return;
        }
        boolean isWatcher = ticket.getWatchers().stream()
                .anyMatch(w -> w.getId() != null && w.getId().equals(viewerId));
        if (isWatcher) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
    }

    public record TicketDetail(Ticket ticket, List<TicketEvent> events) {
    }
}
