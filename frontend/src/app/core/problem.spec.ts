import { HttpErrorResponse } from '@angular/common/http';
import { toProblem } from './problem';

describe('toProblem', () => {
  it('reads RFC 7807 bodies including 400 field errors', () => {
    const p = toProblem(new HttpErrorResponse({
      status: 400,
      error: { title: 'Bad Request', status: 400, detail: 'Validation failed', errors: { claimedAmount: 'must be greater than 0' } },
    }));
    expect(p).toEqual({
      status: 400,
      title: 'Bad Request',
      detail: 'Validation failed',
      errors: { claimedAmount: 'must be greater than 0' },
    });
  });

  it('keeps 409 and 422 details from the server', () => {
    const p = toProblem(new HttpErrorResponse({
      status: 409,
      error: { title: 'Conflict', detail: 'Cannot APPROVE a claim in status ASSIGNED (requires UNDER_ASSESSMENT)' },
    }));
    expect(p.status).toBe(409);
    expect(p.detail).toContain('UNDER_ASSESSMENT');
    expect(p.errors).toEqual({});
  });

  it('explains an unreachable backend (status 0)', () => {
    const p = toProblem(new HttpErrorResponse({ status: 0 }));
    expect(p.title).toBe('Backend unreachable');
    expect(p.detail).toContain('localhost:8080');
  });

  it('falls back to a standard title when the body is not JSON', () => {
    const p = toProblem(new HttpErrorResponse({ status: 403, error: '<html>' }));
    expect(p.title).toBe('Forbidden');
  });
});
