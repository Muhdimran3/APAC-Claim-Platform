import { Pipe, PipeTransform } from '@angular/core';
import { formatDate, formatDateTime, formatMoney, formatMoneyCompact } from '../core/format';

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency: string, compact = false): string {
    if (compact && amount !== null && amount !== undefined) return formatMoneyCompact(amount, currency);
    return formatMoney(amount, currency);
  }
}

@Pipe({ name: 'day' })
export class DayPipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return formatDate(iso);
  }
}

@Pipe({ name: 'when' })
export class WhenPipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return formatDateTime(iso);
  }
}
