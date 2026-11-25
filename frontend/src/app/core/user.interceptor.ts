import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CurrentUser } from './current-user';

export const USER_HEADER = 'X-User';

/**
 * Adds X-User to every /api call. This is the only place identity is sent. Request bodies
 * never carry a role, because the backend looks the role up from its own user table.
 */
export const userInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const username = inject(CurrentUser).username();
  return next(req.clone({ setHeaders: { [USER_HEADER]: username } }));
};
