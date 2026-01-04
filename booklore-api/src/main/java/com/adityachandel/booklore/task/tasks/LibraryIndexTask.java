package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.ChapterContent;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryIndexStatusEntity;
import com.adityachandel.booklore.model.enums.IndexStatus;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.model.enums.UserPermission;
import com.adityachandel.booklore.model.websocket.TaskProgressPayload;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryIndexStatusRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fulltext.LuceneIndexService;
import com.adityachandel.booklore.service.fulltext.extractor.TextExtractorFactory;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.task.options.LibraryIndexOptions;
import com.adityachandel.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task for indexing a library's books for full-text search.
 * Extracts text content from PDFs and EPUBs and builds a Lucene index.
 * Supports chapter-level indexing for EPUB files.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LibraryIndexTask implements Task {

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final LibraryIndexStatusRepository indexStatusRepository;
    private final TextExtractorFactory textExtractorFactory;
    private final LuceneIndexService luceneIndexService;
    private final NotificationService notificationService;

    private static final long MIN_NOTIFICATION_INTERVAL_MS = 500;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!UserPermission.CAN_ACCESS_TASK_MANAGER.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_ACCESS_TASK_MANAGER);
        }
    }

    @Override
    @Transactional
    public TaskCreateResponse execute(TaskCreateRequest request) {
        LibraryIndexOptions options = request.getOptions(LibraryIndexOptions.class);
        if (options == null || options.getLibraryId() == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Library ID is required for indexing");
        }

        Long libraryId = options.getLibraryId();
        String taskId = request.getTaskId();

        TaskCreateResponse.TaskCreateResponseBuilder responseBuilder = TaskCreateResponse.builder()
                .taskId(taskId)
                .taskType(TaskType.INDEX_LIBRARY);

        long startTime = System.currentTimeMillis();
        log.info("INDEX_LIBRARY: Starting indexing for library {}", libraryId);

        // Validate library exists
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        // Initialize or update index status
        LibraryIndexStatusEntity statusEntity = indexStatusRepository.findById(libraryId)
                .orElse(LibraryIndexStatusEntity.builder()
                        .libraryId(libraryId)
                        .build());
        
        statusEntity.setStatus(IndexStatus.IN_PROGRESS);
        statusEntity.setErrorMessage(null);
        indexStatusRepository.save(statusEntity);

        long lastNotificationTime = 0;
        lastNotificationTime = sendProgress(taskId, 0, 
                "Starting full-text indexing for library: " + library.getName(), 
                TaskStatus.IN_PROGRESS, lastNotificationTime, true);

        try {
            // Get all books in the library that support text extraction
            List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryId(libraryId);
            List<BookEntity> indexableBooks = books.stream()
                    .filter(b -> textExtractorFactory.isSupported(b.getBookType()))
                    .toList();

            int totalBooks = indexableBooks.size();
            statusEntity.setTotalBookCount(totalBooks);
            indexStatusRepository.save(statusEntity);

            lastNotificationTime = sendProgress(taskId, 5,
                    String.format("Found %d indexable books (PDF/EPUB) out of %d total", totalBooks, books.size()),
                    TaskStatus.IN_PROGRESS, lastNotificationTime, false);

            if (totalBooks == 0) {
                statusEntity.setStatus(IndexStatus.COMPLETE);
                statusEntity.setLastIndexedAt(Instant.now());
                statusEntity.setIndexedBookCount(0);
                indexStatusRepository.save(statusEntity);
                
                sendProgress(taskId, 100, "No indexable books found in library", 
                        TaskStatus.COMPLETED, lastNotificationTime, true);
                
                return responseBuilder.status(TaskStatus.COMPLETED).build();
            }

            // Clear existing index and create new one
            lastNotificationTime = sendProgress(taskId, 10,
                    "Clearing existing index...",
                    TaskStatus.IN_PROGRESS, lastNotificationTime, false);
            
            luceneIndexService.clearIndex(libraryId);

            // Index each book
            int indexedCount = 0;
            int failedCount = 0;
            int chapterIndexedCount = 0;

            for (int i = 0; i < indexableBooks.size(); i++) {
                BookEntity book = indexableBooks.get(i);
                
                try {
                    String filePath = FileUtils.getBookFullPath(book);
                    File file = new File(filePath);
                    
                    if (!file.exists()) {
                        log.warn("Book file not found: {}", filePath);
                        failedCount++;
                        continue;
                    }

                    String title = book.getMetadata() != null ? book.getMetadata().getTitle() : book.getFileName();
                    String authors = book.getMetadata() != null && book.getMetadata().getAuthors() != null
                            ? book.getMetadata().getAuthors().stream()
                                    .map(a -> a.getName())
                                    .collect(Collectors.joining(", "))
                            : "";

                    // Try chapter-level indexing first for supported formats
                    if (textExtractorFactory.supportsChapterExtraction(book.getBookType())) {
                        List<ChapterContent> chapters = textExtractorFactory.extractChapters(book.getBookType(), file);
                        
                        if (!chapters.isEmpty()) {
                            int chaptersIndexed = luceneIndexService.indexBookChapters(
                                    libraryId, book.getId(), title, authors, chapters);
                            
                            if (chaptersIndexed > 0) {
                                indexedCount++;
                                chapterIndexedCount += chaptersIndexed;
                                log.debug("Indexed {} chapters for book: {}", chaptersIndexed, book.getFileName());
                                continue;
                            }
                        }
                    }

                    // Fall back to whole-book indexing
                    String text = textExtractorFactory.extractText(book.getBookType(), file);
                    
                    if (text != null && !text.isBlank()) {
                        luceneIndexService.indexBook(libraryId, book.getId(), title, authors, text);
                        indexedCount++;
                    } else {
                        log.debug("No text extracted from book: {} (might be image-only)", book.getFileName());
                        failedCount++;
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to index book {}: {}", book.getId(), e.getMessage());
                    failedCount++;
                }

                // Update progress
                int progress = 10 + ((i + 1) * 85 / totalBooks);
                if ((i + 1) % 5 == 0 || i == totalBooks - 1) {
                    String progressMessage = chapterIndexedCount > 0
                            ? String.format("Indexing: %d/%d books (%d indexed with %d chapters, %d skipped)", 
                                    i + 1, totalBooks, indexedCount, chapterIndexedCount, failedCount)
                            : String.format("Indexing: %d/%d books (%d indexed, %d skipped)", 
                                    i + 1, totalBooks, indexedCount, failedCount);
                    
                    lastNotificationTime = sendProgress(taskId, progress, progressMessage,
                            TaskStatus.IN_PROGRESS, lastNotificationTime, false);
                }
            }

            // Commit the index
            luceneIndexService.commit(libraryId);
            luceneIndexService.closeWriter(libraryId);

            // Update status
            statusEntity.setStatus(IndexStatus.COMPLETE);
            statusEntity.setLastIndexedAt(Instant.now());
            statusEntity.setIndexedBookCount(indexedCount);
            indexStatusRepository.save(statusEntity);

            long duration = System.currentTimeMillis() - startTime;
            String completionMessage = chapterIndexedCount > 0
                    ? String.format("Indexing complete: %d books indexed (%d chapters), %d skipped in %d ms",
                            indexedCount, chapterIndexedCount, failedCount, duration)
                    : String.format("Indexing complete: %d books indexed, %d skipped in %d ms",
                            indexedCount, failedCount, duration);
            
            log.info("INDEX_LIBRARY: {}", completionMessage);
            sendProgress(taskId, 100, completionMessage, TaskStatus.COMPLETED, lastNotificationTime, true);

            return responseBuilder.status(TaskStatus.COMPLETED).build();

        } catch (Exception e) {
            log.error("INDEX_LIBRARY: Failed to index library {}: {}", libraryId, e.getMessage(), e);
            
            statusEntity.setStatus(IndexStatus.FAILED);
            statusEntity.setErrorMessage(e.getMessage());
            indexStatusRepository.save(statusEntity);

            sendProgress(taskId, 0, "Indexing failed: " + e.getMessage(), 
                    TaskStatus.FAILED, lastNotificationTime, true);

            return responseBuilder.status(TaskStatus.FAILED).build();
        }
    }

    private long sendProgress(String taskId, int progress, String message, TaskStatus status, 
                              long lastNotificationTime, boolean force) {
        long currentTime = System.currentTimeMillis();

        if (force || (currentTime - lastNotificationTime) >= MIN_NOTIFICATION_INTERVAL_MS) {
            try {
                TaskProgressPayload payload = TaskProgressPayload.builder()
                        .taskId(taskId)
                        .taskType(TaskType.INDEX_LIBRARY)
                        .message(message)
                        .progress(progress)
                        .taskStatus(status)
                        .build();

                notificationService.sendMessage(Topic.TASK_PROGRESS, payload);
                return currentTime;
            } catch (Exception e) {
                log.error("Failed to send task progress notification: {}", e.getMessage());
            }
        }

        return lastNotificationTime;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.INDEX_LIBRARY;
    }
}
