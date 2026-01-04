package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.FullTextSearchResponse;
import com.adityachandel.booklore.model.dto.LibraryIndexStatus;
import com.adityachandel.booklore.service.fulltext.FullTextSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for full-text search operations.
 */
@RestController
@RequestMapping("/api/v1/search/fulltext")
@RequiredArgsConstructor
@Tag(name = "Full-Text Search", description = "Endpoints for searching within book content")
public class FullTextSearchController {

    private final FullTextSearchService fullTextSearchService;

    @Operation(summary = "Search within books", 
               description = "Performs a full-text search across indexed library books")
    @ApiResponse(responseCode = "200", description = "Search results returned successfully")
    @GetMapping
    public ResponseEntity<FullTextSearchResponse> search(
            @Parameter(description = "Search query") 
            @RequestParam String query,
            @Parameter(description = "Library IDs to search (optional, searches all indexed libraries if not specified)")
            @RequestParam(required = false) List<Long> libraryIds,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Results per page")
            @RequestParam(defaultValue = "20") int pageSize) {
        
        return ResponseEntity.ok(fullTextSearchService.search(query, libraryIds, page, pageSize));
    }

    @Operation(summary = "Get index status for a library",
               description = "Returns the full-text search index status for a specific library")
    @ApiResponse(responseCode = "200", description = "Index status returned successfully")
    @GetMapping("/status/{libraryId}")
    public ResponseEntity<LibraryIndexStatus> getIndexStatus(
            @Parameter(description = "Library ID") @PathVariable Long libraryId) {
        
        LibraryIndexStatus status = fullTextSearchService.getIndexStatus(libraryId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Get index status for all libraries",
               description = "Returns the full-text search index status for all libraries")
    @ApiResponse(responseCode = "200", description = "Index statuses returned successfully")
    @GetMapping("/status")
    public ResponseEntity<List<LibraryIndexStatus>> getAllIndexStatuses() {
        return ResponseEntity.ok(fullTextSearchService.getAllIndexStatuses());
    }

    @Operation(summary = "Get indexed libraries",
               description = "Returns list of libraries that have been indexed for full-text search")
    @ApiResponse(responseCode = "200", description = "Indexed libraries returned successfully")
    @GetMapping("/indexed-libraries")
    public ResponseEntity<List<LibraryIndexStatus>> getIndexedLibraries() {
        return ResponseEntity.ok(fullTextSearchService.getIndexedLibraries());
    }

    @Operation(summary = "Check if full-text search is available",
               description = "Returns whether any libraries have been indexed for full-text search")
    @ApiResponse(responseCode = "200", description = "Availability status returned successfully")
    @GetMapping("/available")
    public ResponseEntity<Boolean> isFullTextSearchAvailable() {
        return ResponseEntity.ok(fullTextSearchService.hasIndexedLibraries());
    }
}

