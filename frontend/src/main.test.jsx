
import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm, RequestForm, ApprovalsInbox } from './main.jsx';

describe('LoginForm', () => {
  it('submits email and password to the login handler', async () => {
    const onLogin = vi.fn().mockResolvedValue(undefined);

    render(<LoginForm onLogin={onLogin} />);

    await userEvent.clear(screen.getByLabelText('Email'));
    await userEvent.type(
      screen.getByLabelText('Email'),
      'employee@example.com'
    );

    await userEvent.clear(screen.getByLabelText('Password'));
    await userEvent.type(
      screen.getByLabelText('Password'),
      'secret-password'
    );

    await userEvent.click(
      screen.getByRole('button', { name: /sign in/i })
    );

    await waitFor(() => {
      expect(onLogin).toHaveBeenCalledWith(
        'employee@example.com',
        'secret-password'
      );
    });
  });
});

describe('RequestForm', () => {
  it('disables submit while the request is pending and sends an idempotency key', async () => {
    let finish;

    const onSubmit = vi.fn(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        })
    );

    render(
      <RequestForm
        resources={[
          {
            id: 9,
            name: 'payments-repo',
            sensitivity: 'HIGH',
          },
        ]}
        onSubmit={onSubmit}
      />
    );

    await userEvent.type(
      screen.getByLabelText('Business justification'),
      'Need access for incident review'
    );

    await userEvent.click(
      screen.getByRole('button', { name: /submit request/i })
    );

    expect(
      screen.getByRole('button', { name: /submitting/i })
    ).toBeDisabled();

    expect(onSubmit).toHaveBeenCalledTimes(1);

    expect(onSubmit.mock.calls[0][0].idempotencyKey).toMatch(
      /^[0-9a-f-]{36}$/i
    );

    finish();

    // The form clears its justification after successful submission.
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /submit request/i })
      ).toBeDisabled();
    });

    // Enter a new justification to enable the button again.
    await userEvent.type(
      screen.getByLabelText('Business justification'),
      'Need access for incident review'
    );

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /submit request/i })
      ).toBeEnabled();
    });
  });
});

describe('ApprovalsInbox', () => {
  it('requires a comment before rejecting', async () => {
    const onReject = vi.fn();

    render(
      <ApprovalsInbox
        rows={[
          {
            id: 12,
            requester: 'Rahul',
            resource: 'payments-repo',
            duration_days: 7,
            reason: 'Incident response',
          },
        ]}
        onApprove={vi.fn()}
        onReject={onReject}
      />
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Reject' })
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'rejection comment is required'
    );

    expect(onReject).not.toHaveBeenCalled();

    await userEvent.type(
      screen.getByLabelText('Rejection comment for request 12'),
      'Insufficient justification'
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Reject' })
    );

    await waitFor(() => {
      expect(onReject).toHaveBeenCalledWith(
        12,
        'Insufficient justification'
      );
    });
  });
});
