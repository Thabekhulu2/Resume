import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { NotificationRow } from './models';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(ApiService);

  mine() {
    return this.api.get<NotificationRow[]>('/notifications/mine');
  }

  markRead(id: string) {
    return this.api.patch<void>(`/notifications/${id}/read`);
  }
}
