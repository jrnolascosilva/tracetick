import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { apiClient } from '@/lib/apiClient';
import { describeApiError } from '@/lib/errors';
import type { Severity, Tag } from '@/lib/types';

const TAG_KEY_PATTERN = /^[a-z0-9_-]{1,32}$/;

interface TagDraft {
  key: string;
  value: string;
}

function emptyDraft(): TagDraft {
  return { key: '', value: '' };
}

function tagDraftError(draft: TagDraft): string | null {
  const hasKey = draft.key.trim() !== '';
  const hasValue = draft.value.trim() !== '';
  if (!hasKey && !hasValue) {
    return null;
  }
  if (!hasKey) {
    return 'Tag key is required when a value is set.';
  }
  if (!TAG_KEY_PATTERN.test(draft.key)) {
    return 'Tag key must match [a-z0-9_-]{1,32}.';
  }
  return null;
}

function cleanTagDrafts(drafts: TagDraft[]): Tag[] {
  return drafts
    .filter((draft) => draft.key.trim() !== '' && draft.value.trim() !== '')
    .map((draft) => ({ key: draft.key.trim(), value: draft.value.trim() }));
}

export function NewTicketPage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [tagDrafts, setTagDrafts] = useState<TagDraft[]>([]);
  const [serverError, setServerError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: apiClient.createTicket,
  });

  const draftErrors = tagDrafts.map(tagDraftError);
  const hasInvalidTag = draftErrors.some((error) => error !== null);
  const submitting = createMutation.isPending;
  const error =
    serverError ??
    (createMutation.isError ? describeApiError(createMutation.error, 'Unable to create the ticket.') : null);

  function updateDraft(index: number, patch: Partial<TagDraft>) {
    setTagDrafts((current) =>
      current.map((draft, i) => (i === index ? { ...draft, ...patch } : draft)),
    );
  }

  function addDraft() {
    setTagDrafts((current) => [...current, emptyDraft()]);
  }

  function removeDraft(index: number) {
    setTagDrafts((current) => current.filter((_, i) => i !== index));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (hasInvalidTag || submitting) {
      return;
    }
    setServerError(null);
    try {
      const ticket = await createMutation.mutateAsync({
        title,
        description,
        severity,
        tags: cleanTagDrafts(tagDrafts),
      });
      navigate(`/tickets/${ticket.id}`);
    } catch (err) {
      setServerError(describeApiError(err, 'Unable to create the ticket.'));
    }
  }

  return (
    <section className="new-ticket-page">
      <h2>New ticket</h2>
      <form className="new-ticket-form" onSubmit={handleSubmit} noValidate>
        <label>
          Title
          <input
            type="text"
            name="title"
            required
            value={title}
            onChange={(event) => setTitle(event.target.value)}
          />
        </label>
        <label>
          Description
          <textarea
            name="description"
            required
            rows={6}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </label>
        <label>
          Severity
          <select
            name="severity"
            value={severity}
            onChange={(event) => setSeverity(event.target.value as Severity)}
          >
            <option value="CRITICAL">CRITICAL</option>
            <option value="HIGH">HIGH</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="LOW">LOW</option>
          </select>
        </label>

        <fieldset className="new-ticket-tags">
          <legend>Tags</legend>
          {tagDrafts.map((draft, index) => {
            const draftError = draftErrors[index];
            return (
              <div key={index} className="new-ticket-tag-row">
                <label>
                  Tag key {index + 1}
                  <input
                    type="text"
                    value={draft.key}
                    onChange={(event) => updateDraft(index, { key: event.target.value })}
                    placeholder="service"
                    aria-invalid={draftError !== null}
                  />
                </label>
                <label>
                  Tag value {index + 1}
                  <input
                    type="text"
                    value={draft.value}
                    onChange={(event) => updateDraft(index, { value: event.target.value })}
                    placeholder="api"
                  />
                </label>
                <button
                  type="button"
                  aria-label="Remove tag"
                  onClick={() => removeDraft(index)}
                >
                  Remove
                </button>
                {draftError && (
                  <p role="alert" className="new-ticket-tag-error">{draftError}</p>
                )}
              </div>
            );
          })}
          <button type="button" onClick={addDraft}>Add tag</button>
        </fieldset>

        {error && <p role="alert" className="new-ticket-error">{error}</p>}
        <button type="submit" disabled={submitting || hasInvalidTag}>
          {submitting ? 'Creating…' : 'Create ticket'}
        </button>
      </form>
    </section>
  );
}