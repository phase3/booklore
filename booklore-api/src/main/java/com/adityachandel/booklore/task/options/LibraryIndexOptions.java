package com.adityachandel.booklore.task.options;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Options for the INDEX_LIBRARY task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryIndexOptions {
    private Long libraryId;
}

