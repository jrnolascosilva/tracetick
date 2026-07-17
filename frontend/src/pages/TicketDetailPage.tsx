import { useParams } from 'react-router-dom';

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  return (
    <section>
      <h2>Ticket {id}</h2>
      <p>Detail view lands in T6.</p>
    </section>
  );
}