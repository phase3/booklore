package com.adityachandel.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response wrapper for full-text search results with pagination info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullTextSearchResponse {
    private String query;
    private List<FullTextSearchResult> results;
    private int page;
    private int pageSize;
    private long totalHits;
    private int totalPages;
}

