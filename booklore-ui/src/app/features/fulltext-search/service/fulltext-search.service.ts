import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {FullTextSearchResponse, LibraryIndexStatus} from '../model/fulltext-search.model';

@Injectable({
  providedIn: 'root'
})
export class FulltextSearchService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/search/fulltext`;
  private http = inject(HttpClient);

  /**
   * Performs a full-text search across indexed libraries.
   */
  search(query: string, libraryIds?: number[], page: number = 0, pageSize: number = 20): Observable<FullTextSearchResponse> {
    let params = new HttpParams()
      .set('query', query)
      .set('page', page.toString())
      .set('pageSize', pageSize.toString());

    if (libraryIds && libraryIds.length > 0) {
      params = params.set('libraryIds', libraryIds.join(','));
    }

    return this.http.get<FullTextSearchResponse>(this.url, {params});
  }

  /**
   * Gets the index status for a specific library.
   */
  getIndexStatus(libraryId: number): Observable<LibraryIndexStatus> {
    return this.http.get<LibraryIndexStatus>(`${this.url}/status/${libraryId}`);
  }

  /**
   * Gets the index status for all libraries.
   */
  getAllIndexStatuses(): Observable<LibraryIndexStatus[]> {
    return this.http.get<LibraryIndexStatus[]>(`${this.url}/status`);
  }

  /**
   * Gets the list of libraries that have been indexed.
   */
  getIndexedLibraries(): Observable<LibraryIndexStatus[]> {
    return this.http.get<LibraryIndexStatus[]>(`${this.url}/indexed-libraries`);
  }

  /**
   * Checks if any libraries have been indexed for full-text search.
   */
  isFullTextSearchAvailable(): Observable<boolean> {
    return this.http.get<boolean>(`${this.url}/available`);
  }
}

