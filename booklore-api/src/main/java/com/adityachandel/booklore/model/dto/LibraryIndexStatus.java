package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.IndexStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for library full-text search index status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryIndexStatus {
    private Long libraryId;
    private String libraryName;
    private IndexStatus status;
    private Instant lastIndexedAt;
    private Integer indexedBookCount;
    private Integer totalBookCount;
    private String errorMessage;
}

