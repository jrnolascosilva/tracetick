import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { UserAdminForm } from '@/components/UserAdminForm';
import { UserTable } from '@/components/UserTable';
import { apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { describeApiError } from '@/lib/errors';
import type { Role, User } from '@/lib/types';

const USERS_QUERY_KEY = ['users'] as const;

export function UserAdminPage() {
  const auth = useAuth();
  const queryClient = useQueryClient();

  const usersQuery = useQuery({
    queryKey: USERS_QUERY_KEY,
    queryFn: apiClient.listUsers,
    enabled: auth.status === 'authenticated' && auth.user?.role === 'TECHNICIAN',
  });

  const createMutation = useMutation({
    mutationFn: apiClient.createUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY }),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { role?: Role; active?: boolean } }) =>
      apiClient.updateUser(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY }),
  });

  const [editing, setEditing] = useState<User | null>(null);

  useEffect(() => () => setEditing(null), []);

  if (auth.status !== 'authenticated' || auth.user?.role !== 'TECHNICIAN') {
    return null;
  }

  return (
    <section className="user-admin-page">
      <h2>Users</h2>
      {usersQuery.isError && (
        <p role="alert" className="user-admin-error">
          Failed to load users: {describeApiError(usersQuery.error)}
        </p>
      )}
      {usersQuery.isPending && <p>Loading users…</p>}
      {usersQuery.data && (
        <UserTable
          users={usersQuery.data}
          currentUserId={auth.user.id}
          onEdit={setEditing}
          onSave={async (user, body) => {
            await updateMutation.mutateAsync({ id: user.id, body });
            setEditing(null);
          }}
          busyUserId={
            updateMutation.isPending && updateMutation.variables ? updateMutation.variables.id : null
          }
        />
      )}

      <h3>Invite user</h3>
      <UserAdminForm
        busy={createMutation.isPending}
        error={createMutation.isError ? describeApiError(createMutation.error) : null}
        onSubmit={async (email, password, role) => {
          await createMutation.mutateAsync({ email, password, role });
        }}
      />

      {editing && (
        <EditPanel
          user={editing}
          busy={updateMutation.isPending}
          onCancel={() => setEditing(null)}
          onSubmit={async (body) => {
            await updateMutation.mutateAsync({ id: editing.id, body });
            setEditing(null);
          }}
        />
      )}
    </section>
  );
}

function EditPanel({
  user,
  busy,
  onCancel,
  onSubmit,
}: {
  user: User;
  busy: boolean;
  onCancel: () => void;
  onSubmit: (body: { role?: Role; active?: boolean }) => Promise<void>;
}) {
  const [role, setRole] = useState<Role>(user.role);
  const [active, setActive] = useState<boolean>(user.active);

  return (
    <section className="user-admin-edit">
      <h3>Edit {user.email}</h3>
      <label>
        Role
        <select value={role} onChange={(event) => setRole(event.target.value as Role)}>
          <option value="CUSTOMER">CUSTOMER</option>
          <option value="TECHNICIAN">TECHNICIAN</option>
        </select>
      </label>
      <label className="user-admin-edit-active">
        <input
          type="checkbox"
          checked={active}
          onChange={(event) => setActive(event.target.checked)}
        />
        Active
      </label>
      <div className="user-admin-edit-actions">
        <button type="button" onClick={onCancel}>Cancel</button>
        <button type="button" disabled={busy} onClick={() => void onSubmit({ role, active })}>
          Save
        </button>
      </div>
    </section>
  );
}
