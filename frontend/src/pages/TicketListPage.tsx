import { useQuery } from '@tanstack/react-query';
import { useCallback, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { StateBadge, SeverityBadge } from '@/components/TicketBadges';
import { apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { describeApiError } from '@/lib/errors';
import type { Severity, Ticket, TicketState } from '@/lib/types';

const TICKETS_QUERY_KEY = ['tickets'] as const;
const DEFAULT_PAGE_SIZE = 50;

type SortKey = 'severity' | 'state' | 'createdAt';
type SortDirection = 'asc' | 'desc';

interface SortState {
  key: SortKey;
  direction: SortDirection;
}

const SORT_LABELS: Record<SortKey, string> = {
  severity: 'Severity',
  state: 'State',
  createdAt: 'Age',
};

const DEFAULT_SORT: SortState = { key: 'createdAt', direction: 'desc' };

export function TicketListPage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchInput, setSearchInput] = useState(() => searchParams.get('search') ?? '');

  const stateFilter = (searchParams.get('state') as TicketState | null) ?? null;
  const severityFilter = (searchParams.get('severity') as Severity | null) ?? null;
  const tagFilter = searchParams.get('tag') ?? '';
  const searchFilter = searchParams.get('search') ?? '';
  const page = parsePage(searchParams.get('page'));
  const sort = readSort(searchParams.get('sort'));

  const queryParams = useMemo(
    () => ({
      state: stateFilter ?? undefined,
      severity: severityFilter ?? undefined,
      tag: tagFilter || undefined,
      search: searchFilter || undefined,
      sort: `${sort.key},${sort.direction}`,
      page,
      size: DEFAULT_PAGE_SIZE,
    }),
    [stateFilter, severityFilter, tagFilter, searchFilter, sort, page],
  );

  const ticketsQuery = useQuery({
    queryKey: [...TICKETS_QUERY_KEY, queryParams] as const,
    queryFn: () => apiClient.listTickets(queryParams),
    enabled: auth.status === 'authenticated',
  });

  const updateParams = useCallback(
    (mutate: (next: URLSearchParams) => void) => {
      const next = new URLSearchParams(searchParams);
      mutate(next);
      if (!next.get('page')) next.set('page', '0');
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const setFilter = useCallback(
    (key: string, value: string | null) => {
      updateParams((next) => {
        if (value) next.set(key, value);
        else next.delete(key);
        next.set('page', '0');
      });
    },
    [updateParams],
  );

  const setSort = useCallback(
    (next: SortState) => {
      updateParams((params) => {
        params.set('sort', `${next.key},${next.direction}`);
      });
    },
    [updateParams],
  );

  const setPage = useCallback(
    (next: number) => {
      updateParams((params) => {
        if (next <= 0) params.delete('page');
        else params.set('page', String(next));
      });
    },
    [updateParams],
  );

  const submitSearch = useCallback(
    (event: React.FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      setFilter('search', searchInput.trim() || null);
    },
    [searchInput, setFilter],
  );

  if (auth.status !== 'authenticated') {
    return null;
  }

  const totalPages =
    ticketsQuery.data && ticketsQuery.data.size > 0
      ? Math.max(1, Math.ceil(ticketsQuery.data.total / ticketsQuery.data.size))
      : 1;

  const errorMessage = ticketsQuery.isError ? describeApiError(ticketsQuery.error, 'Unable to load tickets.') : null;
  const items = ticketsQuery.data?.items ?? [];

  return (
    <section className="ticket-list-page">
      <header className="ticket-list-header">
        <h2>Tickets</h2>
      </header>

      <form className="ticket-list-search" onSubmit={submitSearch} role="search">
        <label>
          Search
          <input
            type="search"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Title or description"
          />
        </label>
        <button type="submit">Search</button>
      </form>

      <div className="ticket-list-toolbar">
        <FilterSelect
          label="State"
          value={stateFilter ?? ''}
          onChange={(value) => setFilter('state', value || null)}
          options={[
            { value: '', label: 'Any' },
            { value: 'OPEN', label: 'Open' },
            { value: 'IN_PROGRESS', label: 'In progress' },
            { value: 'RESOLVED', label: 'Resolved' },
            { value: 'CLOSED', label: 'Closed' },
          ]}
        />
        <FilterSelect
          label="Severity"
          value={severityFilter ?? ''}
          onChange={(value) => setFilter('severity', value || null)}
          options={[
            { value: '', label: 'Any' },
            { value: 'CRITICAL', label: 'Critical' },
            { value: 'HIGH', label: 'High' },
            { value: 'MEDIUM', label: 'Medium' },
            { value: 'LOW', label: 'Low' },
          ]}
        />
        <label className="ticket-list-toolbar-tag">
          Tag
          <input
            type="text"
            value={tagFilter}
            onChange={(event) => setFilter('tag', event.target.value || null)}
            placeholder="key:value"
          />
        </label>
        <SortControl value={sort} onChange={setSort} />
      </div>

      {errorMessage && (
        <p role="alert" className="ticket-list-error">{errorMessage}</p>
      )}

      {ticketsQuery.isPending && <p>Loading tickets…</p>}

      {ticketsQuery.data && (
        <>
          <TicketTable tickets={items} />
          <Pagination
            page={page}
            totalPages={totalPages}
            totalItems={ticketsQuery.data.total}
            onChange={setPage}
          />
          {items.length === 0 && ticketsQuery.data.total === 0 && (
            <p className="ticket-list-empty">No tickets match your filters.</p>
          )}
        </>
      )}
    </section>
  );
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <label className="ticket-list-toolbar-filter">
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
    </label>
  );
}

function SortControl({
  value,
  onChange,
}: {
  value: SortState;
  onChange: (next: SortState) => void;
}) {
  return (
    <fieldset className="ticket-list-sort">
      <legend>Sort</legend>
      {(Object.keys(SORT_LABELS) as SortKey[]).map((key) => (
        <label key={key}>
          <input
            type="radio"
            name="ticket-sort"
            checked={value.key === key}
            onChange={() => onChange({ key, direction: value.direction })}
          />
          {SORT_LABELS[key]}
        </label>
      ))}
      <button
        type="button"
        className="ticket-list-sort-toggle"
        onClick={() => onChange({ key: value.key, direction: value.direction === 'asc' ? 'desc' : 'asc' })}
        aria-label={`Sort ${value.direction === 'asc' ? 'descending' : 'ascending'}`}
      >
        {value.direction === 'asc' ? '↑' : '↓'}
      </button>
    </fieldset>
  );
}

function TicketTable({ tickets }: { tickets: Ticket[] }) {
  if (tickets.length === 0) {
    return null;
  }
  return (
    <table className="ticket-table">
      <thead>
        <tr>
          <th>Severity</th>
          <th>State</th>
          <th>Title</th>
          <th>Reporter</th>
          <th>Assignee</th>
          <th>Tags</th>
          <th>Opened</th>
        </tr>
      </thead>
      <tbody>
        {tickets.map((ticket) => (
          <TicketRow key={ticket.id} ticket={ticket} />
        ))}
      </tbody>
    </table>
  );
}

function TicketRow({ ticket }: { ticket: Ticket }) {
  return (
    <tr>
      <td><SeverityBadge severity={ticket.severity} /></td>
      <td><StateBadge state={ticket.state} /></td>
      <td>
        <Link to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
      </td>
      <td>{ticket.reporterUserId ?? '—'}</td>
      <td>{ticket.assigneeUserId ?? '—'}</td>
      <td className="ticket-table-tags">
        {ticket.tags.length === 0
          ? <span className="ticket-table-muted">—</span>
          : ticket.tags.map((tag) => (
              <span key={`${tag.key}:${tag.value}`} className="ticket-tag">{tag.key}:{tag.value}</span>
            ))}
      </td>
      <td><time dateTime={ticket.createdAt}>{formatAge(ticket.createdAt)}</time></td>
    </tr>
  );
}

function Pagination({
  page,
  totalPages,
  totalItems,
  onChange,
}: {
  page: number;
  totalPages: number;
  totalItems: number;
  onChange: (next: number) => void;
}) {
  const hasPrev = page > 0;
  const hasNext = page + 1 < totalPages;
  return (
    <nav className="ticket-list-pagination" aria-label="Pagination">
      <button type="button" disabled={!hasPrev} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span>Page {page + 1} of {totalPages} ({totalItems} tickets)</span>
      <button type="button" disabled={!hasNext} onClick={() => onChange(page + 1)}>
        Next
      </button>
    </nav>
  );
}

function readSort(sortParam: string | null): SortState {
  if (!sortParam) return DEFAULT_SORT;
  const [key, direction] = sortParam.split(',');
  if (key === 'severity' || key === 'state' || key === 'createdAt') {
    return { key, direction: direction === 'asc' ? 'asc' : 'desc' };
  }
  return DEFAULT_SORT;
}

function parsePage(raw: string | null): number {
  if (!raw) return 0;
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
}

function formatAge(iso: string): string {
  const created = new Date(iso).getTime();
  if (!Number.isFinite(created)) return iso;
  const diffMs = Date.now() - created;
  if (diffMs < 0) return 'just now';
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo ago`;
  const years = Math.floor(days / 365);
  return `${years}y ago`;
}