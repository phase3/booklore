export interface FullTextSearchResult {
  bookId: number;
  libraryId: number;
  title: string;
  authors: string;
  score: number;
  highlights: string[];
  // Chapter information (may be null if book doesn't have chapter-level indexing)
  chapterIndex?: number;
  chapterTitle?: string;
  chapterHref?: string;
}

export interface FullTextSearchResponse {
  query: string;
  results: FullTextSearchResult[];
  page: number;
  pageSize: number;
  totalHits: number;
  totalPages: number;
}

export type IndexStatus = 'NONE' | 'IN_PROGRESS' | 'COMPLETE' | 'FAILED';

export interface LibraryIndexStatus {
  libraryId: number;
  libraryName: string;
  status: IndexStatus;
  lastIndexedAt?: string;
  indexedBookCount: number;
  totalBookCount: number;
  errorMessage?: string;
}

/**
 * Groups search results by book for better display.
 */
export interface GroupedSearchResult {
  bookId: number;
  libraryId: number;
  title: string;
  authors: string;
  maxScore: number;
  chapters: ChapterSearchResult[];
}

export interface ChapterSearchResult {
  chapterIndex?: number;
  chapterTitle?: string;
  chapterHref?: string;
  score: number;
  highlights: string[];
}
