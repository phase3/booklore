package com.adityachandel.booklore.service.fulltext;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.ChapterContent;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryIndexStatusEntity;
import com.adityachandel.booklore.model.enums.IndexStatus;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryIndexStatusRepository;
import com.adityachandel.booklore.service.fulltext.extractor.TextExtractorFactory;
import com.adityachandel.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for incrementally indexing and removing individual books from the full-text search index.
 * This complements the full library re-index provided by LibraryIndexTask.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookIndexingService {

    private final LuceneIndexService luceneIndexService;
    private final LibraryIndexStatusRepository indexStatusRepository;
    private final TextExtractorFactory textExtractorFactory;
    private final BookRepository bookRepository;

    /**
     * Checks if the library has an active (COMPLETE) index.
     *
     * @param libraryId the library ID
     * @return true if the library has been indexed and is in COMPLETE status
     */
    public boolean isLibraryIndexed(long libraryId) {
        return indexStatusRepository.findById(libraryId)
                .map(status -> status.getStatus() == IndexStatus.COMPLETE)
                .orElse(false);
    }

    /**
     * Indexes a single book by its DTO if the library has an active index.
     * This is a convenience method that fetches the entity and delegates to indexSingleBook(BookEntity).
     *
     * @param book the book DTO
     * @return true if the book was indexed, false if skipped or not found
     */
    public boolean indexSingleBook(Book book) {
        if (book == null || book.getId() == null) {
            return false;
        }

        // Quick check if library is indexed before fetching the entity
        if (!isLibraryIndexed(book.getLibraryId())) {
            log.debug("Library {} is not indexed - skipping indexing for book {}", book.getLibraryId(), book.getId());
            return false;
        }

        Optional<BookEntity> entityOpt = bookRepository.findById(book.getId());
        if (entityOpt.isEmpty()) {
            log.warn("Book entity not found for ID {} - cannot index", book.getId());
            return false;
        }

        return indexSingleBook(entityOpt.get());
    }

    /**
     * Indexes a single book if the library has an active index.
     * This is called when a new book is added to the library.
     *
     * @param book the book entity to index
     * @return true if the book was indexed, false if skipped (library not indexed or unsupported format)
     */
    public boolean indexSingleBook(BookEntity book) {
        if (book == null) {
            return false;
        }

        long libraryId = book.getLibrary().getId();

        // Check if the library has an active index
        if (!isLibraryIndexed(libraryId)) {
            log.debug("Library {} is not indexed - skipping indexing for book {}", libraryId, book.getId());
            return false;
        }

        // Check if the book type supports text extraction
        if (!textExtractorFactory.isSupported(book.getBookType())) {
            log.debug("Book type {} not supported for indexing - skipping book {}", book.getBookType(), book.getId());
            return false;
        }

        try {
            String filePath = FileUtils.getBookFullPath(book);
            File file = new File(filePath);

            if (!file.exists()) {
                log.warn("Book file not found for indexing: {}", filePath);
                return false;
            }

            String title = book.getMetadata() != null ? book.getMetadata().getTitle() : book.getFileName();
            String authors = extractAuthorsString(book);

            boolean indexed = false;

            // Try chapter-level indexing first for supported formats
            if (textExtractorFactory.supportsChapterExtraction(book.getBookType())) {
                List<ChapterContent> chapters = textExtractorFactory.extractChapters(book.getBookType(), file);

                if (!chapters.isEmpty()) {
                    int chaptersIndexed = luceneIndexService.indexBookChapters(
                            libraryId, book.getId(), title, authors, chapters);

                    if (chaptersIndexed > 0) {
                        luceneIndexService.commit(libraryId);
                        indexed = true;
                        log.info("Indexed {} chapters for book {} in library {}", chaptersIndexed, book.getId(), libraryId);
                    }
                }
            }

            // Fall back to whole-book indexing if chapter indexing didn't work
            if (!indexed) {
                String text = textExtractorFactory.extractText(book.getBookType(), file);

                if (text != null && !text.isBlank()) {
                    luceneIndexService.indexBook(libraryId, book.getId(), title, authors, text);
                    luceneIndexService.commit(libraryId);
                    indexed = true;
                    log.info("Indexed book {} in library {}", book.getId(), libraryId);
                } else {
                    log.debug("No text extracted from book {} - might be image-only", book.getId());
                }
            }

            // Update the index status count if successfully indexed
            if (indexed) {
                incrementIndexedBookCount(libraryId);
            }

            return indexed;

        } catch (Exception e) {
            log.error("Failed to index book {}: {}", book.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Removes a book from the index.
     * This is called when a book is deleted from the library.
     *
     * @param libraryId the library ID
     * @param bookId the book ID to remove
     * @return true if the book was removed from the index
     */
    public boolean removeBookFromIndex(long libraryId, long bookId) {
        // Check if the library has an index
        if (!luceneIndexService.indexExists(libraryId)) {
            log.debug("No index exists for library {} - skipping removal of book {}", libraryId, bookId);
            return false;
        }

        try {
            boolean removed = luceneIndexService.removeBookFromIndex(libraryId, bookId);

            if (removed) {
                decrementIndexedBookCount(libraryId);
            }

            return removed;

        } catch (Exception e) {
            log.error("Failed to remove book {} from library {} index: {}", bookId, libraryId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts the authors as a comma-separated string from a book entity.
     */
    private String extractAuthorsString(BookEntity book) {
        if (book.getMetadata() == null || book.getMetadata().getAuthors() == null) {
            return "";
        }
        return book.getMetadata().getAuthors().stream()
                .map(a -> a.getName())
                .collect(Collectors.joining(", "));
    }

    /**
     * Increments the indexed book count for a library.
     */
    @Transactional
    public void incrementIndexedBookCount(long libraryId) {
        Optional<LibraryIndexStatusEntity> statusOpt = indexStatusRepository.findById(libraryId);
        if (statusOpt.isPresent()) {
            LibraryIndexStatusEntity status = statusOpt.get();
            status.setIndexedBookCount(status.getIndexedBookCount() + 1);
            status.setTotalBookCount(status.getTotalBookCount() + 1);
            indexStatusRepository.save(status);
            log.debug("Incremented index count for library {} to {}", libraryId, status.getIndexedBookCount());
        }
    }

    /**
     * Decrements the indexed book count for a library.
     */
    @Transactional
    public void decrementIndexedBookCount(long libraryId) {
        Optional<LibraryIndexStatusEntity> statusOpt = indexStatusRepository.findById(libraryId);
        if (statusOpt.isPresent()) {
            LibraryIndexStatusEntity status = statusOpt.get();
            int newIndexedCount = Math.max(0, status.getIndexedBookCount() - 1);
            int newTotalCount = Math.max(0, status.getTotalBookCount() - 1);
            status.setIndexedBookCount(newIndexedCount);
            status.setTotalBookCount(newTotalCount);
            indexStatusRepository.save(status);
            log.debug("Decremented index count for library {} to {}", libraryId, newIndexedCount);
        }
    }
}

