import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { animate, style, transition, trigger } from '@angular/animations';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import {
  selectIsRateLimited,
  selectRetryAfterSeconds,
  selectRateLimitMessage
} from '../../store/rate-limit/rate-limit.selectors';

@Component({
  selector: 'app-rate-limit-toast',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rate-limit-toast.component.html',
  styleUrls: ['./rate-limit-toast.component.scss'],
  animations: [
    trigger('slideIn', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('300ms ease-out', style({ transform: 'translateX(0)', opacity: 1 })),
      ]),
      transition(':leave', [
        animate('300ms ease-in', style({ transform: 'translateX(100%)', opacity: 0 })),
      ]),
    ]),
  ],
})
export class RateLimitToastComponent {
  isRateLimited$: Observable<boolean>;
  retryAfterSeconds$: Observable<number>;
  message$: Observable<string>;
  
  constructor(private store: Store) {
    this.isRateLimited$ = this.store.select(selectIsRateLimited);
    this.retryAfterSeconds$ = this.store.select(selectRetryAfterSeconds);
    this.message$ = this.store.select(selectRateLimitMessage);
  }
}