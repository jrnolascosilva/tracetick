package com.tracetick.api;

import com.tracetick.api.dto.CommentRequest;
import com.tracetick.api.dto.CreateTicketRequest;
import com.tracetick.api.dto.EventDto;
import com.tracetick.api.dto.PageDto;
import com.tracetick.api.dto.TicketDetailDto;
import com.tracetick.api.dto.TicketDto;
import com.tracetick.api.dto.UpdateTicketRequest;
import com.tracetick.domain.Severity;
import com.tracetick.domain.TicketState;
import com.tracetick.domain.User;
import com.tracetick.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketService ticketService;
    private final UserRepository userRepository;

    public TicketsController(TicketService ticketService, UserRepository userRepository) {
        this.ticketService = ticketService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<TicketDto> create(@Valid @RequestBody CreateTicketRequest request) {
        User reporter = currentUser();
        var ticket = ticketService.createHumanTicket(request, reporter);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticket.getId()))
                .body(TicketDto.from(ticket));
    }

    @GetMapping
    public PageDto<TicketDto> list(@RequestParam(required = false) TicketState state,
                                   @RequestParam(required = false) Severity severity,
                                   @RequestParam(required = false) Long assignee,
                                   @RequestParam(name = "tag", required = false) String tag,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String[] tagParts = parseTag(tag);
        User viewer = currentUser();
        Page<com.tracetick.domain.Ticket> result = ticketService.findTickets(
                viewer, state, severity, assignee, tagParts[0], tagParts[1], search,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<TicketDto> items = result.getContent().stream().map(TicketDto::from).toList();
        return new PageDto<>(items, safePage, safeSize, result.getTotalElements());
    }

    @GetMapping("/{id}")
    public TicketDetailDto get(@PathVariable Long id) {
        var detail = ticketService.loadDetail(id, currentUser());
        var events = detail.events().stream().map(EventDto::from).toList();
        return new TicketDetailDto(
                TicketDto.from(detail.ticket()),
                events,
                List.of());
    }

    @PatchMapping("/{id}")
    public TicketDto update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
        return TicketDto.from(ticketService.update(id, request, currentUser()));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<EventDto> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        var event = ticketService.addComment(id, request, currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventDto.from(event));
    }

    private static String[] parseTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return new String[]{null, null};
        }
        int colon = tag.indexOf(':');
        if (colon <= 0 || colon == tag.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tag must be in 'key:value' format");
        }
        return new String[]{tag.substring(0, colon), tag.substring(colon + 1)};
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
