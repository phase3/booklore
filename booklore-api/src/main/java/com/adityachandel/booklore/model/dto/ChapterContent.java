package com.adityachandel.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents content from a single chapter/section of a book.
 * Used for chapter-level full-text indexing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContent {
    
    /**
     * Index of the chapter in the spine/reading order (0-based)
     */
    private int chapterIndex;
    
    /**
     * Title of the chapter (extracted from TOC or heading)
     */
    private String chapterTitle;
    
    /**
     * The href/path to this chapter in the book (used for navigation)
     */
    private String chapterHref;
    
    /**
     * Text content of the chapter
     */
    private String content;
}

