import { TestBed } from '@angular/core/testing';
import { ProblemAlert } from './problem-alert';

describe('ProblemAlert', () => {
  it('renders status, detail, hint and each 400 field error', async () => {
    const fixture = TestBed.createComponent(ProblemAlert);
    fixture.componentRef.setInput('problem', {
      status: 400,
      title: 'Bad Request',
      detail: 'Validation failed',
      errors: { incidentDate: 'must be a date in the past or in the present' },
    });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('400 Bad Request');
    expect(el.textContent).toContain('Some fields need fixing.');
    expect(el.querySelector('li')?.textContent).toContain('incidentDate');
  });

  it('renders nothing without a problem', async () => {
    const fixture = TestBed.createComponent(ProblemAlert);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')).toBeNull();
  });
});
