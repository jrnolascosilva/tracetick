package com.tracetick.persistence;

import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /**
     * Finds a ticket with the given fingerprint whose state is one of the supplied states.
     * Used by the ingest pipeline for dedup per ADR-0004.
     */
    @Query("select ticket from Ticket ticket "
            + "where ticket.fingerprint = :fingerprint "
            + "and ticket.state in :states "
            + "order by ticket.createdAt desc")
    Optional<Ticket> findFirstByFingerprintAndStateIn(@Param("fingerprint") String fingerprint,
                                                       @Param("states") Collection<TicketState> states);
}
