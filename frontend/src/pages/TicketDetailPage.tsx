import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';

import { StateBadge, SeverityBadge } from '@/components/TicketBadges';
import { eventPresentation } from '@/components/TimelinePresentation';
import { ApiError, apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { describeApiError } from '@/lib/errors';
import type { TicketDetail, TicketEvent as TicketEventModel, User } from '@/lib/types';

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
  const canComment = canCommentOnTicket(auth.user, ticket.reporterUserId, watcherIds);

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

      {canComment && <CommentForm ticketId={ticketId} />}
    </section>
  );
}

function canCommentOnTicket(
  user: User | null,
  reporterUserId: number | null,
  watcherIds: number[],
): boolean {
  if (!user) return false;
  if (user.role === 'TECHNICIAN') return true;
  if (reporterUserId != null && reporterUserId === user.id) return true;
  return watcherIds.includes(user.id);
}

function CommentForm({ ticketId }: { ticketId: number }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);

  const detailQueryKey = ['tickets', ticketId] as const;

  const commentMutation = useMutation({
    mutationFn: (body: string) => apiClient.addComment(ticketId, body),
    onSuccess: (event) => {
      appendEventToCache(queryClient, detailQueryKey, event);
      setDraft('');
      setServerError(null);
    },
  });

  const submitting = commentMutation.isPending;
  const error =
    serverError ??
    (commentMutation.isError
      ? describeApiError(commentMutation.error, 'Unable to post the comment.')
      : null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const body = draft.trim();
    if (!body || submitting) {
      return;
    }
    setServerError(null);
    try {
      await commentMutation.mutateAsync(body);
    } catch (err) {
      setServerError(describeApiError(err, 'Unable to post the comment.'));
    }
  }

  return (
    <form className="ticket-detail-comment-form" onSubmit={handleSubmit}>
      <label>
        Add a comment
        <textarea
          name="comment"
          rows={3}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          disabled={submitting}
          placeholder="Share an update with the team…"
        />
      </label>
      {error && <p role="alert" className="ticket-detail-comment-error">{error}</p>}
      <button type="submit" disabled={submitting || draft.trim() === ''}>
        {submitting ? 'Posting…' : 'Post comment'}
      </button>
    </form>
  );
}

function appendEventToCache(
  queryClient: ReturnType<typeof useQueryClient>,
  queryKey: readonly ['tickets', number],
  event: TicketEventModel,
): void {
  queryClient.setQueryData<TicketDetail | undefined>(queryKey, (current) => {
    if (!current) {
      return current;
    }
    if (current.events.some((existing) => existing.id === event.id)) {
      return current;
    }
    const merged = [...current.events, event];
    merged.sort(compareEventsByCreatedAt);
    return { ...current, events: merged };
  });
}

function compareEventsByCreatedAt(a: TicketEventModel, b: TicketEventModel): number {
  if (a.createdAt === b.createdAt) {
    return a.id - b.id;
  }
  return a.createdAt < b.createdAt ? -1 : 1;
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