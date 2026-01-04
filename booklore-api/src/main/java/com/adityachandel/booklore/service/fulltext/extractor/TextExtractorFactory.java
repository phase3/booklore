package com.adityachandel.booklore.service.fulltext.extractor;

import com.adityachandel.booklore.model.dto.ChapterContent;
import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * Factory for selecting the appropriate text extractor based on book file type.
 */
@Slf4j
@Component
@AllArgsConstructor
public class TextExtractorFactory {

    private final PdfTextExtractor pdfTextExtractor;
    private final EpubTextExtractor epubTextExtractor;

    /**
     * Extracts text from a book file based on its type.
     * 
     * @param bookFileType the type of book file
     * @param file the book file
     * @return extracted text content, or null if extraction fails or type is unsupported
     */
    public String extractText(BookFileType bookFileType, File file) {
        if (bookFileType == null || file == null) {
            return null;
        }

        return switch (bookFileType) {
            case PDF -> pdfTextExtractor.extractText(file);
            case EPUB -> epubTextExtractor.extractText(file);
            case CBX, FB2 -> {
                log.debug("Text extraction not supported for file type: {}", bookFileType);
                yield null;
            }
        };
    }

    /**
     * Extracts chapters from a book file based on its type.
     * 
     * @param bookFileType the type of book file
     * @param file the book file
     * @return list of chapter contents, or empty list if extraction fails or type doesn't support chapters
     */
    public List<ChapterContent> extractChapters(BookFileType bookFileType, File file) {
        if (bookFileType == null || file == null) {
            return List.of();
        }

        return switch (bookFileType) {
            case EPUB -> epubTextExtractor.extractChapters(file);
            case PDF, CBX, FB2 -> {
                log.debug("Chapter extraction not supported for file type: {}", bookFileType);
                yield List.of();
            }
        };
    }

    /**
     * Checks if text extraction is supported for the given book file type.
     * 
     * @param bookFileType the type of book file
     * @return true if text extraction is supported
     */
    public boolean isSupported(BookFileType bookFileType) {
        return bookFileType == BookFileType.PDF || bookFileType == BookFileType.EPUB;
    }

    /**
     * Checks if chapter-level extraction is supported for the given book file type.
     * 
     * @param bookFileType the type of book file
     * @return true if chapter extraction is supported
     */
    public boolean supportsChapterExtraction(BookFileType bookFileType) {
        return bookFileType == BookFileType.EPUB;
    }
}
