import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';

import { apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { describeApiError } from '@/lib/errors';
import type {
  IngestionConfiguration,
  IngestionConfigurationWithSecret,
  Severity,
} from '@/lib/types';

const INGESTION_CONFIGS_QUERY_KEY = ['ingestion-configurations'] as const;

const TAG_KEY_PATTERN = /^[a-z0-9_-]{1,32}$/;

const SEVERITY_OPTIONS: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

interface TagDraft {
  key: string;
  value: string;
}

interface RevealState {
  name: string;
  webhookUrl: string;
  hmacSecret: string;
}

type CopyNotice =
  | { kind: 'copied'; label: string }
  | { kind: 'error'; label: string };

function emptyDraft(): TagDraft {
  return { key: '', value: '' };
}

function tagDraftError(draft: TagDraft): string | null {
  const hasKey = draft.key.trim() !== '';
  const hasValue = draft.value.trim() !== '';
  if (!hasKey && !hasValue) return null;
  if (!hasKey) return 'Tag key is required when a value is set.';
  if (!TAG_KEY_PATTERN.test(draft.key)) {
    return 'Tag key must match [a-z0-9_-]{1,32}.';
  }
  return null;
}

function cleanTagDrafts(drafts: TagDraft[]): Record<string, string> {
  const cleaned: Record<string, string> = {};
  for (const draft of drafts) {
    const key = draft.key.trim();
    const value = draft.value.trim();
    if (key !== '' && value !== '') {
      cleaned[key] = value;
    }
  }
  return cleaned;
}

function tagsToDrafts(tags: Record<string, string>): TagDraft[] {
  return Object.entries(tags).map(([key, value]) => ({ key, value }));
}

function formatTags(tags: Record<string, string>): string {
  const entries = Object.entries(tags);
  if (entries.length === 0) return '—';
  return entries.map(([k, v]) => `${k}:${v}`).join(', ');
}

async function copyToClipboard(text: string): Promise<void> {
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  throw new Error('Clipboard is not available in this browser.');
}

export function IngestionConfigurationsPage() {
  const auth = useAuth();
  const queryClient = useQueryClient();

  const listQuery = useQuery({
    queryKey: INGESTION_CONFIGS_QUERY_KEY,
    queryFn: apiClient.listIngestionConfigurations,
    enabled: auth.status === 'authenticated' && auth.user?.role === 'TECHNICIAN',
  });

  const createMutation = useMutation({
    mutationFn: apiClient.createIngestionConfiguration,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: INGESTION_CONFIGS_QUERY_KEY });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: {
      id: number;
      body: Parameters<typeof apiClient.updateIngestionConfiguration>[1];
    }) => apiClient.updateIngestionConfiguration(id, body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: INGESTION_CONFIGS_QUERY_KEY });
    },
  });

  const [reveal, setReveal] = useState<RevealState | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [copyNotice, setCopyNotice] = useState<CopyNotice | null>(null);

  useEffect(() => () => {
    setReveal(null);
    setEditingId(null);
    setCopyNotice(null);
  }, []);

  if (auth.status !== 'authenticated' || auth.user?.role !== 'TECHNICIAN') {
    return null;
  }

  const configs = listQuery.data ?? [];

  function showRevealFromCreate(created: IngestionConfigurationWithSecret) {
    setReveal({
      name: created.name,
      webhookUrl: created.webhookUrl,
      hmacSecret: created.hmacSecret,
    });
  }

  function showRevealFromRotate(updated: IngestionConfigurationWithSecret) {
    setReveal({
      name: updated.name,
      webhookUrl: updated.webhookUrl,
      hmacSecret: updated.hmacSecret,
    });
  }

  async function handleCopy(label: string, text: string) {
    try {
      await copyToClipboard(text);
      setCopyNotice({ kind: 'copied', label });
    } catch {
      setCopyNotice({ kind: 'error', label });
    }
  }

  const listError = listQuery.isError
    ? describeApiError(listQuery.error, 'Unable to load ingestion configurations.')
    : null;
  const createError = createMutation.isError
    ? describeApiError(createMutation.error, 'Unable to create the ingestion configuration.')
    : null;
  const updateError = updateMutation.isError
    ? describeApiError(updateMutation.error, 'Unable to update the ingestion configuration.')
    : null;

  return (
    <section className="ingestion-configurations-page">
      <h2>Ingestion configurations</h2>

      {reveal && (
        <RevealPanel
          reveal={reveal}
          onDismiss={() => setReveal(null)}
          onCopy={(label, text) => void handleCopy(label, text)}
        />
      )}

      {copyNotice && (
        <p className="ingestion-configurations-copy-state" role="status">
          {copyNotice.kind === 'copied'
            ? `${copyNotice.label} copied to clipboard.`
            : `Could not copy ${copyNotice.label}.`}
        </p>
      )}

      {listError && (
        <p role="alert" className="ingestion-configurations-error">{listError}</p>
      )}
      {listQuery.isPending && <p>Loading configurations…</p>}

      {listQuery.data && (
        <ConfigurationTable
          configs={configs}
          editingId={editingId}
          onEdit={setEditingId}
          onCopy={(label, text) => void handleCopy(label, text)}
        />
      )}

      {editingId !== null && listQuery.data && (
        <EditPanel
          config={configs.find((c) => c.id === editingId)!}
          busy={updateMutation.isPending}
          error={updateError}
          onCancel={() => setEditingId(null)}
          onSave={async (body) => {
            try {
              const updated = await updateMutation.mutateAsync({ id: editingId, body });
              setEditingId(null);
              return updated;
            } catch {
              // Error is surfaced via updateError in the parent; nothing to do here.
              throw new Error('update failed');
            }
          }}
          onRotate={async () => {
            try {
              const updated = await updateMutation.mutateAsync({
                id: editingId,
                body: { rotateSecret: true },
              });
              showRevealFromRotate(updated);
              setEditingId(null);
            } catch {
              // Error is surfaced via updateError in the parent; nothing to do here.
            }
          }}
        />
      )}

      <h3>Add ingestion configuration</h3>
      <CreateForm
        busy={createMutation.isPending}
        error={createError}
        onSubmit={async (body) => {
          try {
            const created = await createMutation.mutateAsync(body);
            showRevealFromCreate(created);
          } catch {
            // Error is surfaced via createError in the parent; nothing to do here.
          }
        }}
      />
    </section>
  );
}

function ConfigurationTable({
  configs,
  editingId,
  onEdit,
  onCopy,
}: {
  configs: IngestionConfiguration[];
  editingId: number | null;
  onEdit: (id: number) => void;
  onCopy: (label: string, text: string) => void;
}) {
  if (configs.length === 0) {
    return <p className="ingestion-configurations-empty">No ingestion configurations yet.</p>;
  }
  return (
    <table className="ingestion-configurations-table">
      <thead>
        <tr>
          <th>Name</th>
          <th>Default severity</th>
          <th>Default assignee</th>
          <th>Default tags</th>
          <th>Webhook URL</th>
          <th>Active</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {configs.map((config) => (
          <tr key={config.id} aria-label={config.name}>
            <td>{config.name}</td>
            <td>{config.defaultSeverity}</td>
            <td>{config.defaultAssigneeUserId ?? '—'}</td>
            <td>{formatTags(config.defaultTags)}</td>
            <td>
              <code className="ingestion-configurations-webhook-url">{config.webhookUrl}</code>
              <button
                type="button"
                className="ingestion-configurations-copy-webhook"
                onClick={() => onCopy('Webhook URL', config.webhookUrl)}
              >
                Copy webhook URL
              </button>
            </td>
            <td>{config.active ? 'Active' : 'Inactive'}</td>
            <td>
              <button
                type="button"
                disabled={editingId === config.id}
                onClick={() => onEdit(config.id)}
              >
                Edit
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function RevealPanel({
  reveal,
  onDismiss,
  onCopy,
}: {
  reveal: RevealState;
  onDismiss: () => void;
  onCopy: (label: string, text: string) => void;
}) {
  return (
    <section
      className="ingestion-configurations-reveal"
      aria-label="New ingestion configuration credentials"
    >
      <h3>Save these credentials now</h3>
      <p>
        The HMAC secret below is shown <strong>once</strong>. Store it securely before dismissing —
        you will need it to sign webhook requests, and it cannot be recovered from the UI.
      </p>
      <dl>
        <dt>Name</dt>
        <dd>{reveal.name}</dd>
        <dt>Webhook URL</dt>
        <dd>
          <code>{reveal.webhookUrl}</code>
          <button type="button" onClick={() => onCopy('Webhook URL', reveal.webhookUrl)}>
            Copy webhook URL
          </button>
        </dd>
        <dt>HMAC secret</dt>
        <dd>
          <code>{reveal.hmacSecret}</code>
          <button type="button" onClick={() => onCopy('HMAC secret', reveal.hmacSecret)}>
            Copy HMAC secret
          </button>
        </dd>
      </dl>
      <button type="button" onClick={onDismiss}>I have saved it, dismiss</button>
    </section>
  );
}

function CreateForm({
  busy,
  error,
  onSubmit,
}: {
  busy: boolean;
  error: string | null;
  onSubmit: (body: {
    name: string;
    defaultSeverity?: Severity;
    defaultAssigneeUserId?: number | null;
    defaultTags?: Record<string, string>;
  }) => Promise<void>;
}) {
  const [name, setName] = useState('');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [assigneeId, setAssigneeId] = useState<string>('');
  const [tagDrafts, setTagDrafts] = useState<TagDraft[]>([]);

  const draftErrors = tagDrafts.map(tagDraftError);
  const hasInvalidTag = draftErrors.some((error) => error !== null);

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
    if (hasInvalidTag || busy) return;
    const body: Parameters<typeof onSubmit>[0] = {
      name,
      defaultSeverity: severity,
    };
    if (assigneeId.trim() !== '') {
      const parsed = Number.parseInt(assigneeId.trim(), 10);
      if (Number.isFinite(parsed)) {
        body.defaultAssigneeUserId = parsed;
      }
    }
    const tags = cleanTagDrafts(tagDrafts);
    if (Object.keys(tags).length > 0) {
      body.defaultTags = tags;
    }
    await onSubmit(body);
    setName('');
    setSeverity('MEDIUM');
    setAssigneeId('');
    setTagDrafts([]);
  }

  return (
    <form className="ingestion-configurations-create-form" onSubmit={handleSubmit} noValidate>
      <label>
        Name
        <input
          type="text"
          name="name"
          required
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      <label>
        Default severity
        <select
          name="defaultSeverity"
          value={severity}
          onChange={(event) => setSeverity(event.target.value as Severity)}
        >
          {SEVERITY_OPTIONS.map((option) => (
            <option key={option} value={option}>{option}</option>
          ))}
        </select>
      </label>
      <label>
        Default assignee user id
        <input
          type="number"
          name="defaultAssigneeUserId"
          min={1}
          value={assigneeId}
          onChange={(event) => setAssigneeId(event.target.value)}
        />
      </label>

      <fieldset className="ingestion-configurations-create-tags">
        <legend>Default tags</legend>
        {tagDrafts.map((draft, index) => {
          const draftError = draftErrors[index];
          return (
            <div key={index} className="ingestion-configurations-tag-row">
              <label>
                Tag key {index + 1}
                <input
                  type="text"
                  value={draft.key}
                  onChange={(event) => updateDraft(index, { key: event.target.value })}
                  aria-invalid={draftError !== null}
                />
              </label>
              <label>
                Tag value {index + 1}
                <input
                  type="text"
                  value={draft.value}
                  onChange={(event) => updateDraft(index, { value: event.target.value })}
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
                <p role="alert" className="ingestion-configurations-tag-error">{draftError}</p>
              )}
            </div>
          );
        })}
        <button type="button" onClick={addDraft}>Add tag</button>
      </fieldset>

      {error && (
        <p role="alert" className="ingestion-configurations-error">{error}</p>
      )}
      <button type="submit" disabled={busy || hasInvalidTag}>
        {busy ? 'Creating…' : 'Create configuration'}
      </button>
    </form>
  );
}

function EditPanel({
  config,
  busy,
  error,
  onCancel,
  onSave,
  onRotate,
}: {
  config: IngestionConfiguration;
  busy: boolean;
  error: string | null;
  onCancel: () => void;
  onSave: (body: {
    name?: string;
    defaultSeverity?: Severity;
    defaultAssigneeUserId?: number | null;
    defaultTags?: Record<string, string>;
    active?: boolean;
  }) => Promise<IngestionConfigurationWithSecret>;
  onRotate: () => Promise<void>;
}) {
  const [name, setName] = useState(config.name);
  const [severity, setSeverity] = useState<Severity>(config.defaultSeverity);
  const [assigneeId, setAssigneeId] = useState<string>(
    config.defaultAssigneeUserId === null ? '' : String(config.defaultAssigneeUserId),
  );
  const [tagDrafts, setTagDrafts] = useState<TagDraft[]>(tagsToDrafts(config.defaultTags));
  const [active, setActive] = useState<boolean>(config.active);

  const draftErrors = tagDrafts.map(tagDraftError);
  const hasInvalidTag = draftErrors.some((error) => error !== null);

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

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (hasInvalidTag || busy) return;
    const body: Parameters<typeof onSave>[0] = {
      name,
      defaultSeverity: severity,
      active,
    };
    if (assigneeId.trim() === '') {
      body.defaultAssigneeUserId = null;
    } else {
      const parsed = Number.parseInt(assigneeId.trim(), 10);
      if (Number.isFinite(parsed)) {
        body.defaultAssigneeUserId = parsed;
      }
    }
    const tags = cleanTagDrafts(tagDrafts);
    body.defaultTags = tags;
    await onSave(body);
  }

  return (
    <section className="ingestion-configurations-edit">
      <h3>Edit {config.name}</h3>
      <form onSubmit={handleSave} noValidate>
        <label>
          Name
          <input
            type="text"
            name="name"
            required
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <label>
          Default severity
          <select
            name="defaultSeverity"
            value={severity}
            onChange={(event) => setSeverity(event.target.value as Severity)}
          >
            {SEVERITY_OPTIONS.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
        </label>
        <label>
          Default assignee user id
          <input
            type="number"
            name="defaultAssigneeUserId"
            min={1}
            value={assigneeId}
            onChange={(event) => setAssigneeId(event.target.value)}
          />
        </label>
        <label className="ingestion-configurations-edit-active">
          <input
            type="checkbox"
            checked={active}
            onChange={(event) => setActive(event.target.checked)}
          />
          Active
        </label>

        <fieldset className="ingestion-configurations-edit-tags">
          <legend>Default tags</legend>
          {tagDrafts.map((draft, index) => {
            const draftError = draftErrors[index];
            return (
              <div key={index} className="ingestion-configurations-tag-row">
                <label>
                  Tag key {index + 1}
                  <input
                    type="text"
                    value={draft.key}
                    onChange={(event) => updateDraft(index, { key: event.target.value })}
                    aria-invalid={draftError !== null}
                  />
                </label>
                <label>
                  Tag value {index + 1}
                  <input
                    type="text"
                    value={draft.value}
                    onChange={(event) => updateDraft(index, { value: event.target.value })}
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
                  <p role="alert" className="ingestion-configurations-tag-error">{draftError}</p>
                )}
              </div>
            );
          })}
          <button type="button" onClick={addDraft}>Add tag</button>
        </fieldset>

        {error && (
          <p role="alert" className="ingestion-configurations-error">{error}</p>
        )}
        <div className="ingestion-configurations-edit-actions">
          <button type="button" onClick={onCancel} disabled={busy}>Cancel</button>
          <button
            type="button"
            onClick={() => void onRotate()}
            disabled={busy}
            className="ingestion-configurations-rotate"
          >
            Rotate secret
          </button>
          <button type="submit" disabled={busy || hasInvalidTag}>
            {busy ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </section>
  );
}
