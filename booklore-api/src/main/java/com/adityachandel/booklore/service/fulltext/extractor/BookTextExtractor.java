package com.adityachandel.booklore.service.fulltext.extractor;

import com.adityachandel.booklore.model.dto.ChapterContent;

import java.io.File;
import java.util.List;

/**
 * Interface for extracting text content from book files for full-text indexing.
 */
public interface BookTextExtractor {
    
    /**
     * Extracts all text content from a book file.
     * 
     * @param file the book file to extract text from
     * @return the extracted text content, or null if extraction fails
     */
    String extractText(File file);
    
    /**
     * Extracts text content organized by chapters/sections from a book file.
     * Each chapter includes its title, href (for navigation), and content.
     * 
     * @param file the book file to extract chapters from
     * @return list of chapter contents, or empty list if extraction fails or chapters aren't available
     */
    default List<ChapterContent> extractChapters(File file) {
        // Default implementation returns empty list (no chapter support)
        return List.of();
    }
    
    /**
     * Checks if this extractor supports chapter-level extraction.
     * 
     * @return true if extractChapters() will return meaningful chapter data
     */
    default boolean supportsChapterExtraction() {
        return false;
    }
    
    /**
     * Checks if this extractor supports the given file.
     * 
     * @param file the file to check
     * @return true if this extractor can process the file
     */
    boolean supports(File file);
}
