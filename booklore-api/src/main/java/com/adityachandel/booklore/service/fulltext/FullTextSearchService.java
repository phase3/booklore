package com.adityachandel.booklore.service.fulltext;

import com.adityachandel.booklore.model.dto.FullTextSearchResponse;
import com.adityachandel.booklore.model.dto.FullTextSearchResult;
import com.adityachandel.booklore.model.dto.LibraryIndexStatus;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryIndexStatusEntity;
import com.adityachandel.booklore.model.enums.IndexStatus;
import com.adityachandel.booklore.repository.LibraryIndexStatusRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for performing full-text searches across library indexes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullTextSearchService {

    private final LuceneIndexService luceneIndexService;
    private final LibraryIndexStatusRepository indexStatusRepository;
    private final LibraryRepository libraryRepository;

    /**
     * Performs a full-text search across the specified libraries.
     *
     * @param query      the search query
     * @param libraryIds list of library IDs to search (searches all indexed if empty)
     * @param page       page number (0-based)
     * @param pageSize   number of results per page
     * @return search response with results and pagination info
     */
    @Transactional(readOnly = true)
    public FullTextSearchResponse search(String query, List<Long> libraryIds, int page, int pageSize) {
        if (query == null || query.isBlank()) {
            return FullTextSearchResponse.builder()
                    .query(query)
                    .results(List.of())
                    .page(page)
                    .pageSize(pageSize)
                    .totalHits(0)
                    .totalPages(0)
                    .build();
        }

        // If no libraries specified, search all indexed libraries
        List<Long> searchLibraryIds = libraryIds;
        if (searchLibraryIds == null || searchLibraryIds.isEmpty()) {
            searchLibraryIds = indexStatusRepository.findByStatus(IndexStatus.COMPLETE).stream()
                    .map(LibraryIndexStatusEntity::getLibraryId)
                    .toList();
        } else {
            // Filter to only indexed libraries
            searchLibraryIds = indexStatusRepository.findByLibraryIdIn(libraryIds).stream()
                    .filter(s -> s.getStatus() == IndexStatus.COMPLETE)
                    .map(LibraryIndexStatusEntity::getLibraryId)
                    .toList();
        }

        if (searchLibraryIds.isEmpty()) {
            log.debug("No indexed libraries available for search");
            return FullTextSearchResponse.builder()
                    .query(query)
                    .results(List.of())
                    .page(page)
                    .pageSize(pageSize)
                    .totalHits(0)
                    .totalPages(0)
                    .build();
        }

        try {
            List<FullTextSearchResult> results = luceneIndexService.search(searchLibraryIds, query, page, pageSize);
            long totalHits = luceneIndexService.getTotalHits(searchLibraryIds, query);
            int totalPages = (int) Math.ceil((double) totalHits / pageSize);

            return FullTextSearchResponse.builder()
                    .query(query)
                    .results(results)
                    .page(page)
                    .pageSize(pageSize)
                    .totalHits(totalHits)
                    .totalPages(totalPages)
                    .build();

        } catch (IOException | ParseException e) {
            log.error("Search failed for query '{}': {}", query, e.getMessage(), e);
            return FullTextSearchResponse.builder()
                    .query(query)
                    .results(List.of())
                    .page(page)
                    .pageSize(pageSize)
                    .totalHits(0)
                    .totalPages(0)
                    .build();
        }
    }

    /**
     * Gets the index status for a specific library.
     */
    @Transactional(readOnly = true)
    public LibraryIndexStatus getIndexStatus(Long libraryId) {
        Optional<LibraryIndexStatusEntity> statusOpt = indexStatusRepository.findById(libraryId);
        Optional<LibraryEntity> libraryOpt = libraryRepository.findById(libraryId);

        if (libraryOpt.isEmpty()) {
            return null;
        }

        LibraryEntity library = libraryOpt.get();
        
        if (statusOpt.isEmpty()) {
            return LibraryIndexStatus.builder()
                    .libraryId(libraryId)
                    .libraryName(library.getName())
                    .status(IndexStatus.NONE)
                    .indexedBookCount(0)
                    .totalBookCount(0)
                    .build();
        }

        LibraryIndexStatusEntity status = statusOpt.get();
        return LibraryIndexStatus.builder()
                .libraryId(libraryId)
                .libraryName(library.getName())
                .status(status.getStatus())
                .lastIndexedAt(status.getLastIndexedAt())
                .indexedBookCount(status.getIndexedBookCount())
                .totalBookCount(status.getTotalBookCount())
                .errorMessage(status.getErrorMessage())
                .build();
    }

    /**
     * Gets the index status for all libraries.
     */
    @Transactional(readOnly = true)
    public List<LibraryIndexStatus> getAllIndexStatuses() {
        List<LibraryEntity> libraries = libraryRepository.findAll();
        Map<Long, LibraryIndexStatusEntity> statusMap = indexStatusRepository.findAll().stream()
                .collect(Collectors.toMap(LibraryIndexStatusEntity::getLibraryId, s -> s));

        List<LibraryIndexStatus> result = new ArrayList<>();
        for (LibraryEntity library : libraries) {
            LibraryIndexStatusEntity status = statusMap.get(library.getId());
            
            result.add(LibraryIndexStatus.builder()
                    .libraryId(library.getId())
                    .libraryName(library.getName())
                    .status(status != null ? status.getStatus() : IndexStatus.NONE)
                    .lastIndexedAt(status != null ? status.getLastIndexedAt() : null)
                    .indexedBookCount(status != null ? status.getIndexedBookCount() : 0)
                    .totalBookCount(status != null ? status.getTotalBookCount() : 0)
                    .errorMessage(status != null ? status.getErrorMessage() : null)
                    .build());
        }
        
        return result;
    }

    /**
     * Gets the list of libraries that have been indexed.
     */
    @Transactional(readOnly = true)
    public List<LibraryIndexStatus> getIndexedLibraries() {
        return getAllIndexStatuses().stream()
                .filter(s -> s.getStatus() == IndexStatus.COMPLETE)
                .toList();
    }

    /**
     * Checks if any libraries have been indexed.
     */
    @Transactional(readOnly = true)
    public boolean hasIndexedLibraries() {
        return !indexStatusRepository.findByStatus(IndexStatus.COMPLETE).isEmpty();
    }
}

