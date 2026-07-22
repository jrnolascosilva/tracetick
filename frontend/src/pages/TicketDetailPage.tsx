import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';

import { StateBadge, SeverityBadge } from '@/components/TicketBadges';
import { eventPresentation } from '@/components/TimelinePresentation';
import { ApiError, apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { describeApiError } from '@/lib/errors';
import type { TicketEvent as TicketEventModel } from '@/lib/types';

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const auth = useAuth();
  const ticketId = id ? Number.parseInt(id, 10) : Number.NaN;

  const detailQuery = useQuery({
    queryKey: ['tickets', ticketId] as const,
    queryFn: () => apiClient.getTicket(ticketId),
    enabled: auth.status === 'authenticated' && Number.isFinite(ticketId),
  });

  if (auth.status !== 'authenticated') {
    return null;
  }

  if (!Number.isFinite(ticketId)) {
    return (
      <section className="ticket-detail-page">
        <p role="alert">Invalid ticket id.</p>
        <Link to="/tickets">Back to tickets</Link>
      </section>
    );
  }

  if (detailQuery.isPending) {
    return (
      <section className="ticket-detail-page">
        <p>Loading ticket…</p>
      </section>
    );
  }

  if (detailQuery.isError) {
    return (
      <section className="ticket-detail-page">
        <h2>Ticket #{ticketId}</h2>
        <p role="alert">{describeDetailError(detailQuery.error)}</p>
        <Link to="/tickets">Back to tickets</Link>
      </section>
    );
  }

  const { ticket, events, watcherIds } = detailQuery.data;

  return (
    <section className="ticket-detail-page">
      <header className="ticket-detail-header">
        <Link to="/tickets" className="ticket-detail-back">← Back to tickets</Link>
        <h2>{ticket.title}</h2>
        <div className="ticket-detail-meta">
          <SeverityBadge severity={ticket.severity} />
          <StateBadge state={ticket.state} />
        </div>
      </header>

      <div className="ticket-detail-grid">
        <section className="ticket-detail-main">
          <h3>Description</h3>
          <p className="ticket-detail-description">{ticket.description || '—'}</p>
        </section>

        <aside className="ticket-detail-sidebar">
          <dl className="ticket-detail-properties">
            <div>
              <dt>Assignee</dt>
              <dd>{ticket.assigneeUserId ?? '—'}</dd>
            </div>
            <div>
              <dt>Tags</dt>
              <dd>
                {ticket.tags.length === 0
                  ? <span className="ticket-detail-muted">None</span>
                  : ticket.tags.map((tag) => (
                      <span key={`${tag.key}:${tag.value}`} className="ticket-tag">
                        {tag.key}:{tag.value}
                      </span>
                    ))}
              </dd>
            </div>
            <div>
              <dt>Watchers</dt>
              <dd>
                {watcherIds.length === 0
                  ? <span className="ticket-detail-muted">None</span>
                  : watcherIds.join(', ')}
              </dd>
            </div>
          </dl>
        </aside>
      </div>

      <section className="ticket-detail-timeline">
        <h3>Timeline</h3>
        {events.length === 0
          ? <p className="ticket-detail-muted">No activity yet.</p>
          : (
            <ol className="ticket-detail-timeline-list">
              {events.map((event) => (
                <TimelineEntry key={event.id} event={event} />
              ))}
            </ol>
          )}
      </section>
    </section>
  );
}

function TimelineEntry({ event }: { event: TicketEventModel }) {
  const presentation = eventPresentation(event.type);
  return (
    <li className={`ticket-detail-timeline-entry ticket-detail-timeline-${event.type.toLowerCase()}`}>
      <div className="ticket-detail-timeline-meta">
        <span className="ticket-detail-timeline-type">{presentation.label}</span>
        <span className="ticket-detail-timeline-actor">
          {event.actorUserId ? `user #${event.actorUserId}` : 'system'}
        </span>
        <time dateTime={event.createdAt}>{formatDateTime(event.createdAt)}</time>
      </div>
      <div className="ticket-detail-timeline-body">
        {presentation.render(event.payload)}
      </div>
    </li>
  );
}

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString();
}

function describeDetailError(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return 'Ticket not found, or you do not have access to it.';
  }
  return describeApiError(error, 'Unable to load the ticket.');
}