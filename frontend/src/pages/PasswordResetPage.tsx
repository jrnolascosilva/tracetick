import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { ApiError, apiClient } from '@/lib/apiClient';

type Phase = 'request' | 'confirm' | 'success';

const PASSWORD_MIN = 8;
const PASSWORD_MAX = 255;

export function PasswordResetPage() {
  const [searchParams] = useSearchParams();
  const tokenFromQuery = searchParams.get('token') ?? '';
  const nextPath = searchParams.get('next');
  const [phase, setPhase] = useState<Phase>(tokenFromQuery ? 'confirm' : 'request');
  const [email, setEmail] = useState('');
  const [token, setToken] = useState(tokenFromQuery);
  const [newPassword, setNewPassword] = useState('');
  const [confirmedPassword, setConfirmedPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loginPath = nextPath
    ? `/login?next=${encodeURIComponent(nextPath)}`
    : '/login';

  async function requestReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!email.trim()) {
      setError('Email is required.');
      return;
    }
    setSubmitting(true);
    try {
      const response = await apiClient.requestPasswordReset({ email: email.trim() });
      setToken(response.token);
      setPhase('confirm');
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  async function confirmReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!token.trim()) {
      setError('Reset token is required.');
      return;
    }
    if (newPassword.length < PASSWORD_MIN || newPassword.length > PASSWORD_MAX) {
      setError(`Password must be ${PASSWORD_MIN}–${PASSWORD_MAX} characters.`);
      return;
    }
    if (newPassword !== confirmedPassword) {
      setError('Passwords do not match.');
      return;
    }
    setSubmitting(true);
    try {
      await apiClient.confirmPasswordReset({
        token: token.trim(),
        new_password: newPassword,
      });
      setPhase('success');
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  if (phase === 'success') {
    return (
      <section className="password-reset-page password-reset-success">
        <h2>Password updated</h2>
        <p>You can now sign in with your new password.</p>
        <Link to={loginPath}>Go to sign in</Link>
      </section>
    );
  }

  if (phase === 'request') {
    return (
      <section className="password-reset-page">
        <h2>Reset your password</h2>
        <p>Enter your email address to create a one-time reset token.</p>
        <form onSubmit={requestReset} noValidate>
          <label>
            Email
            <input
              type="email"
              name="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
          {error && <p className="login-error" role="alert">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? 'Continuing…' : 'Continue'}
          </button>
        </form>
        <p className="password-reset-link"><Link to={loginPath}>Back to sign in</Link></p>
      </section>
    );
  }

  return (
    <section className="password-reset-page">
      <h2>Choose a new password</h2>
      <form onSubmit={confirmReset} noValidate>
        <label>
          Reset token
          <input
            name="token"
            value={token}
            onChange={(event) => setToken(event.target.value)}
            required
          />
        </label>
        <label>
          New password
          <input
            type="password"
            name="new_password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            required
            minLength={PASSWORD_MIN}
            maxLength={PASSWORD_MAX}
          />
        </label>
        <label>
          Confirm new password
          <input
            type="password"
            name="confirmed_password"
            autoComplete="new-password"
            value={confirmedPassword}
            onChange={(event) => setConfirmedPassword(event.target.value)}
            required
            minLength={PASSWORD_MIN}
            maxLength={PASSWORD_MAX}
          />
        </label>
        {error && <p className="login-error" role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Resetting…' : 'Reset password'}
        </button>
      </form>
      <p className="password-reset-link"><Link to="/password-reset">Request another token</Link></p>
    </section>
  );
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 410) {
      return 'This reset token has expired.';
    }
    if (error.status === 409) {
      return 'This reset token has already been used.';
    }
    if (error.status === 400) {
      return 'The reset token is invalid.';
    }
  }
  return 'Unable to reset the password. Try again.';
}