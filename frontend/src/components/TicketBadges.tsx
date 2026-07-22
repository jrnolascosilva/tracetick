import type { Severity, TicketState } from '@/lib/types';

export function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span className={`ticket-severity ticket-severity-${severity.toLowerCase()}`}>
      {severity}
    </span>
  );
}

export function StateBadge({ state }: { state: TicketState }) {
  return (
    <span className={`ticket-state ticket-state-${state.toLowerCase().replace('_', '-')}`}>
      {state.replace('_', ' ')}
    </span>
  );
}