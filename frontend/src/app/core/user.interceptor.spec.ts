import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CurrentUser } from './current-user';
import { userInterceptor } from './user.interceptor';

describe('userInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let user: CurrentUser;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([userInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    user = TestBed.inject(CurrentUser);
  });

  afterEach(() => backend.verify());

  it('sends the selected username as X-User on API calls', () => {
    user.select('olivia');
    http.get('/api/claims').subscribe();
    expect(backend.expectOne('/api/claims').request.headers.get('X-User')).toBe('olivia');
  });

  it('follows a change of user immediately', () => {
    user.select('maya');
    http.get('/api/me').subscribe();
    expect(backend.expectOne('/api/me').request.headers.get('X-User')).toBe('maya');
  });

  it('does not add identity to non-API requests', () => {
    http.get('/assets/logo.svg').subscribe();
    expect(backend.expectOne('/assets/logo.svg').request.headers.has('X-User')).toBe(false);
  });

  it('never puts a role into the request body', () => {
    user.select('olivia');
    http.post('/api/claims/1/assign', {}).subscribe();
    const req = backend.expectOne('/api/claims/1/assign').request;
    expect(JSON.stringify(req.body)).not.toContain('role');
    expect(req.headers.keys()).not.toContain('X-Role');
  });
});
