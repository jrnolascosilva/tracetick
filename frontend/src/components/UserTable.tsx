import type { User } from '@/lib/types';

interface UserTableProps {
  users: User[];
  currentUserId: number;
  onEdit: (user: User) => void;
  onSave: (user: User, body: { role?: User['role']; active?: boolean }) => Promise<void>;
  busyUserId: number | null;
}

export function UserTable({ users, currentUserId, onEdit, onSave, busyUserId }: UserTableProps) {
  return (
    <table className="user-table">
      <thead>
        <tr>
          <th>Email</th>
          <th>Role</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {users.map((user) => (
          <UserRow
            key={user.id}
            user={user}
            isCurrentUser={user.id === currentUserId}
            busy={busyUserId === user.id}
            onEdit={onEdit}
            onSave={onSave}
          />
        ))}
      </tbody>
    </table>
  );
}

interface UserRowProps {
  user: User;
  isCurrentUser: boolean;
  busy: boolean;
  onEdit: (user: User) => void;
  onSave: (user: User, body: { role?: User['role']; active?: boolean }) => Promise<void>;
}

function UserRow({ user, isCurrentUser, busy, onEdit, onSave }: UserRowProps) {
  return (
    <tr>
      <td>{user.email}</td>
      <td>{user.role}</td>
      <td>{user.active ? 'Active' : 'Inactive'}</td>
      <td>
        <button type="button" onClick={() => onEdit(user)}>Edit</button>
        <button
          type="button"
          disabled={busy}
          onClick={() => void onSave(user, { active: !user.active })}
        >
          {user.active ? 'Deactivate' : 'Activate'}
        </button>
        {isCurrentUser && <span className="user-table-self"> (you)</span>}
      </td>
    </tr>
  );
}
