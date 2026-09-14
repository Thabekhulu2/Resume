import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  get<T>(path: string) {
    return this.http.get<T>(`${this.baseUrl}${path}`);
  }

  post<T>(path: string, body: unknown) {
    return this.http.post<T>(`${this.baseUrl}${path}`, body);
  }

  put<T>(path: string, body: unknown) {
    return this.http.put<T>(`${this.baseUrl}${path}`, body);
  }

  patch<T>(path: string, body: unknown = {}) {
    return this.http.patch<T>(`${this.baseUrl}${path}`, body);
  }

  delete<T>(path: string, body?: unknown) {
    return this.http.delete<T>(`${this.baseUrl}${path}`, body !== undefined ? { body } : {});
  }

  postForm<T>(path: string, formData: FormData) {
    return this.http.post<T>(`${this.baseUrl}${path}`, formData);
  }

  downloadUrl(path: string): string {
    return `${this.baseUrl}${path}`;
  }
}
