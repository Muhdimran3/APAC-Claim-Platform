import { HttpErrorResponse } from '@angular/common/http';

/** RFC 7807 problem details as returned by the backend's GlobalExceptionHandler. */
export interface Problem {
  status: number;
  title: string;
  detail: string;
  /** 400 validation: field name -> message */
  errors: Record<string, string>;
}

const FALLBACK_TITLES: Record<number, string> = {
  0: 'Backend unreachable',
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
  415: 'Unsupported Media Type',
  422: 'Unprocessable Content',
  500: 'Server Error',
};

/** Normalises any HTTP failure into a Problem, so every screen renders errors the same way. */
export function toProblem(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, title: 'Unexpected error', detail: String(error), errors: {} };
  }
  if (error.status === 0) {
    return {
      status: 0,
      title: FALLBACK_TITLES[0],
      detail: 'No response from the API. Is the backend running on http://localhost:8080?',
      errors: {},
    };
  }
  const body = (error.error ?? {}) as Partial<Problem> & Record<string, unknown>;
  const errors =
    body.errors && typeof body.errors === 'object' ? (body.errors as Record<string, string>) : {};
  return {
    status: error.status,
    title: typeof body.title === 'string' ? body.title : (FALLBACK_TITLES[error.status] ?? 'Error'),
    detail: typeof body.detail === 'string' ? body.detail : error.message,
    errors,
  };
}

/** One-line hint per status so users know what kind of failure it was. */
export function problemHint(status: number): string {
  switch (status) {
    case 400:
      return 'Some fields need fixing.';
    case 401:
      return 'The selected user is not known to the backend, so no role could be resolved.';
    case 403:
      return 'Your role or assignment does not allow this.';
    case 404:
      return 'Not found, or not yours to see.';
    case 409:
      return 'The claim is not in a state that allows this (or it changed). Reload and retry.';
    case 422:
      return 'The request breaks a business rule.';
    default:
      return '';
  }
}
