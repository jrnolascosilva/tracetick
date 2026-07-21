import type { EventType, TicketEvent } from '@/lib/types';

interface EventPresentation {
  label: string;
  render: (payload: TicketEvent['payload']) => React.ReactNode;
}

const COMMENT_PRESENTATION: EventPresentation = {
  label: 'Comment',
  render: (payload) => <p className="ticket-detail-timeline-comment">{asText(payload.body) ?? ''}</p>,
};

const STATE_CHANGE_PRESENTATION: EventPresentation = {
  label: 'State change',
  render: (payload) => <p>{asText(payload.from)} → {asText(payload.to)}</p>,
};

const ASSIGNMENT_CHANGE_PRESENTATION: EventPresentation = {
  label: 'Assignee changed',
  render: (payload) => {
    const from = payload.from_user_id;
    const to = payload.to_user_id;
    return (
      <p>
        {from == null ? 'unassigned' : `#${asText(from)}`}
        {' → '}
        {to == null ? 'unassigned' : `#${asText(to)}`}
      </p>
    );
  },
};

const SEVERITY_CHANGE_PRESENTATION: EventPresentation = {
  label: 'Severity changed',
  render: (payload) => <p>{asText(payload.from)} → {asText(payload.to)}</p>,
};

const TAG_CHANGE_PRESENTATION: EventPresentation = {
  label: 'Tag changed',
  render: (payload) => (
    <p>
      {asText(payload.action)} <code>{asText(payload.key)}:{asText(payload.value)}</code>
    </p>
  ),
};

const WATCHER_CHANGE_PRESENTATION: EventPresentation = {
  label: 'Watcher changed',
  render: (payload) => <p>{asText(payload.action)} user #{asText(payload.user_id)}</p>,
};

const REFIRE_PRESENTATION: EventPresentation = {
  label: 'Refire',
  render: () => <p>Alert refired</p>,
};

const ATTACHMENT_PRESENTATION: EventPresentation = {
  label: 'Attachment added',
  render: (payload) => <p>Added <code>{asText(payload.name) ?? asText(payload.filename) ?? 'attachment'}</code></p>,
};

const EVENT_PRESENTATIONS: Record<EventType, EventPresentation> = {
  COMMENT: COMMENT_PRESENTATION,
  STATE_CHANGE: STATE_CHANGE_PRESENTATION,
  ASSIGNMENT_CHANGE: ASSIGNMENT_CHANGE_PRESENTATION,
  SEVERITY_CHANGE: SEVERITY_CHANGE_PRESENTATION,
  TAG_CHANGE: TAG_CHANGE_PRESENTATION,
  WATCHER_CHANGE: WATCHER_CHANGE_PRESENTATION,
  REFIRE: REFIRE_PRESENTATION,
  ATTACHMENT_ADDED: ATTACHMENT_PRESENTATION,
};

export function eventPresentation(type: EventType): EventPresentation {
  return EVENT_PRESENTATIONS[type];
}

function asText(value: unknown): string | null {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') return value;
  if (typeof value === 'number') return String(value);
  return JSON.stringify(value);
}