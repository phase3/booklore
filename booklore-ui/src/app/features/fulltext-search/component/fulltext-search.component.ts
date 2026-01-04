import {Component, inject, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {debounceTime, distinctUntilChanged, Subject, switchMap, tap} from 'rxjs';

import {InputTextModule} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {ProgressSpinner} from 'primeng/progressspinner';
import {PaginatorModule} from 'primeng/paginator';
import {MultiSelect} from 'primeng/multiselect';
import {Tag} from 'primeng/tag';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {Message} from 'primeng/message';
import {Accordion, AccordionContent, AccordionHeader, AccordionPanel} from 'primeng/accordion';
import {Tooltip} from 'primeng/tooltip';

import {FulltextSearchService} from '../service/fulltext-search.service';
import {
  ChapterSearchResult,
  FullTextSearchResponse,
  FullTextSearchResult,
  GroupedSearchResult,
  LibraryIndexStatus
} from '../model/fulltext-search.model';
import {UrlHelperService} from '../../../shared/service/url-helper.service';
import {BookService} from '../../book/service/book.service';

@Component({
  selector: 'app-fulltext-search',
  standalone: true,
  templateUrl: './fulltext-search.component.html',
  styleUrls: ['./fulltext-search.component.scss'],
  imports: [
    FormsModule,
    InputTextModule,
    Button,
    ProgressSpinner,
    PaginatorModule,
    MultiSelect,
    Tag,
    IconField,
    InputIcon,
    Message,
    Accordion,
    AccordionPanel,
    AccordionHeader,
    AccordionContent,
    Tooltip
  ]
})
export class FulltextSearchComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private searchService = inject(FulltextSearchService);
  private bookService = inject(BookService);
  protected urlHelper = inject(UrlHelperService);

  searchQuery = '';
  selectedLibraryIds: number[] = [];
  indexedLibraries: LibraryIndexStatus[] = [];
  
  searchResponse: FullTextSearchResponse | null = null;
  groupedResults: GroupedSearchResult[] = [];
  loading = false;
  initialLoading = true;
  showSearchTips = false;
  
  // Pagination
  page = 0;
  pageSize = 20;
  
  private searchTrigger$ = new Subject<{query: string, libraryIds: number[], page: number, pageSize: number}>();

  ngOnInit() {
    // Load indexed libraries
    this.searchService.getIndexedLibraries().subscribe({
      next: (libraries) => {
        this.indexedLibraries = libraries;
        this.initialLoading = false;
      },
      error: () => {
        this.initialLoading = false;
      }
    });

    // Set up search trigger with debounce
    this.searchTrigger$.pipe(
      debounceTime(300),
      distinctUntilChanged((prev, curr) => 
        prev.query === curr.query && 
        prev.page === curr.page && 
        prev.pageSize === curr.pageSize &&
        JSON.stringify(prev.libraryIds) === JSON.stringify(curr.libraryIds)
      ),
      tap(() => this.loading = true),
      switchMap((params) => this.searchService.search(
        params.query,
        params.libraryIds.length > 0 ? params.libraryIds : undefined,
        params.page,
        params.pageSize
      ))
    ).subscribe({
      next: (response) => {
        this.searchResponse = response;
        this.groupedResults = this.groupResultsByBook(response.results);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.searchResponse = null;
        this.groupedResults = [];
      }
    });

    // Handle initial query from route
    this.route.queryParams.subscribe(params => {
      if (params['q']) {
        this.searchQuery = params['q'];
        if (params['libraryIds']) {
          this.selectedLibraryIds = params['libraryIds'].split(',').map(Number);
        }
        if (params['page']) {
          this.page = parseInt(params['page'], 10);
        }
        this.triggerSearch();
      }
    });
  }

  onSearchSubmit() {
    this.page = 0;
    this.updateUrlAndSearch();
  }

  onLibraryFilterChange() {
    this.page = 0;
    this.updateUrlAndSearch();
  }

  onPageChange(event: any) {
    this.page = event.page;
    this.pageSize = event.rows;
    this.updateUrlAndSearch();
  }

  private updateUrlAndSearch() {
    const queryParams: any = {};
    
    if (this.searchQuery) {
      queryParams['q'] = this.searchQuery;
    }
    if (this.selectedLibraryIds.length > 0) {
      queryParams['libraryIds'] = this.selectedLibraryIds.join(',');
    }
    if (this.page > 0) {
      queryParams['page'] = this.page;
    }

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge'
    });

    this.triggerSearch();
  }

  private triggerSearch() {
    if (this.searchQuery.trim().length >= 2) {
      this.searchTrigger$.next({
        query: this.searchQuery,
        libraryIds: this.selectedLibraryIds,
        page: this.page,
        pageSize: this.pageSize
      });
    } else {
      this.searchResponse = null;
      this.groupedResults = [];
    }
  }

  /**
   * Groups search results by book, combining chapter-level results.
   */
  private groupResultsByBook(results: FullTextSearchResult[]): GroupedSearchResult[] {
    const bookMap = new Map<number, GroupedSearchResult>();
    
    for (const result of results) {
      let grouped = bookMap.get(result.bookId);
      
      if (!grouped) {
        grouped = {
          bookId: result.bookId,
          libraryId: result.libraryId,
          title: result.title,
          authors: result.authors,
          maxScore: result.score,
          chapters: []
        };
        bookMap.set(result.bookId, grouped);
      }
      
      // Update max score
      if (result.score > grouped.maxScore) {
        grouped.maxScore = result.score;
      }
      
      // Add chapter info
      const chapterResult: ChapterSearchResult = {
        chapterIndex: result.chapterIndex,
        chapterTitle: result.chapterTitle,
        chapterHref: result.chapterHref,
        score: result.score,
        highlights: result.highlights
      };
      
      grouped.chapters.push(chapterResult);
    }
    
    // Sort by max score descending and sort chapters within each book
    const groupedArray = Array.from(bookMap.values());
    groupedArray.sort((a, b) => b.maxScore - a.maxScore);
    
    for (const group of groupedArray) {
      // Sort chapters by index if available, otherwise by score
      group.chapters.sort((a, b) => {
        if (a.chapterIndex !== undefined && b.chapterIndex !== undefined) {
          return a.chapterIndex - b.chapterIndex;
        }
        return b.score - a.score;
      });
    }
    
    return groupedArray;
  }

  /**
   * Navigate to a book's details page.
   */
  navigateToBook(bookId: number) {
    this.router.navigate(['/book', bookId]);
  }

  /**
   * Navigate to a specific chapter in the book reader.
   * Fetches the book to determine the correct reader type (PDF/EPUB).
   */
  navigateToChapter(bookId: number, chapter: ChapterSearchResult) {
    if (!chapter.chapterHref) {
      this.router.navigate(['/book', bookId]);
      return;
    }

    // First check if book is in state
    let book = this.bookService.getBookByIdFromState(bookId);
    if (book) {
      this.openBookReader(book.id, book.bookType, chapter.chapterHref);
    } else {
      // Fetch book from API to get its type
      this.bookService.getBookByIdFromAPI(bookId, false).subscribe({
        next: (fetchedBook) => {
          this.openBookReader(fetchedBook.id, fetchedBook.bookType, chapter.chapterHref);
        },
        error: () => {
          // Fallback to book details page if fetch fails
          this.router.navigate(['/book', bookId]);
        }
      });
    }
  }

  /**
   * Opens the appropriate reader based on book type.
   */
  private openBookReader(bookId: number, bookType: string, chapterHref?: string) {
    let readerPath: string;
    
    switch (bookType) {
      case 'PDF':
        readerPath = 'pdf-reader';
        break;
      case 'EPUB':
        readerPath = 'epub-reader';
        break;
      case 'CBX':
        readerPath = 'cbx-reader';
        break;
      default:
        // Unknown type, go to book details
        this.router.navigate(['/book', bookId]);
        return;
    }

    const queryParams: any = {};
    if (chapterHref) {
      queryParams['chapter'] = chapterHref;
    }

    this.router.navigate([`/${readerPath}/book/${bookId}`], { queryParams });
  }

  getLibraryName(libraryId: number): string {
    const library = this.indexedLibraries.find(l => l.libraryId === libraryId);
    return library?.libraryName ?? `Library ${libraryId}`;
  }

  clearSearch() {
    this.searchQuery = '';
    this.searchResponse = null;
    this.groupedResults = [];
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {q: null, libraryIds: null, page: null},
      queryParamsHandling: 'merge'
    });
  }

  /**
   * Checks if any result in the grouped results has chapter information.
   */
  hasChapterInfo(group: GroupedSearchResult): boolean {
    return group.chapters.some(c => c.chapterTitle || c.chapterHref);
  }

  get hasIndexedLibraries(): boolean {
    return this.indexedLibraries.length > 0;
  }

  get libraryOptions() {
    return this.indexedLibraries.map(l => ({
      label: `${l.libraryName} (${l.indexedBookCount} books)`,
      value: l.libraryId
    }));
  }
}
