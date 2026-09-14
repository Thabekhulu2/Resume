import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NotificationService } from '../../core/notification.service';
import { NotificationRow } from '../../core/models';

@Component({
  selector: 'app-candidate-notifications',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './candidate-notifications.html',
})
export class CandidateNotifications implements OnInit {
  private readonly notificationService = inject(NotificationService);

  notifications = signal<NotificationRow[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.notificationService.mine().subscribe((notifications) => {
      this.notifications.set(notifications);
      this.loading.set(false);
    });
  }

  markRead(notification: NotificationRow): void {
    this.notificationService.markRead(notification.id).subscribe(() => this.load());
  }
}
