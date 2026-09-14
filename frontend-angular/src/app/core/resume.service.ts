import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class ResumeService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  upload(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.api.postForm<{ id: string; filename: string }>('/resumes', formData);
  }

  // Resume downloads are auth-protected (Phase 3 scopes them to the caller),
  // so this must go through HttpClient (interceptor attaches the JWT) as a
  // blob rather than a plain <a href>, which would hit the API with no
  // Authorization header at all and get a 401/403.
  //
  // The id is a real storage path (role/ownerId/uuid__filename) -- each
  // segment must be encoded individually so a space in the original
  // filename doesn't break, while the "/" separators stay literal (Spring's
  // {*id} capture expects real path segments, not a %2F-encoded blob).
  downloadBlob(id: string) {
    const encodedPath = id.split('/').map(encodeURIComponent).join('/');
    return this.http.get(`${environment.apiUrl}/resumes/${encodedPath}`, { responseType: 'blob' });
  }
}
