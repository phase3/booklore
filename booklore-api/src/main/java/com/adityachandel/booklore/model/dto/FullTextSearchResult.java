package com.adityachandel.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single search result from full-text search.
 * May include chapter information if the book was indexed with chapter support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullTextSearchResult {
    private Long bookId;
    private Long libraryId;
    private String title;
    private String authors;
    private float score;
    private List<String> highlights;
    
    // Chapter information (may be null if book doesn't have chapter-level indexing)
    private Integer chapterIndex;
    private String chapterTitle;
    private String chapterHref;
}
