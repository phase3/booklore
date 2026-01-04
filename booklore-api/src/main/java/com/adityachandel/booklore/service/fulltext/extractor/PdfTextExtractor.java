package com.adityachandel.booklore.service.fulltext.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Extracts text content from PDF files using Apache PDFBox.
 * Note: Only works with text-based PDFs, not scanned/image PDFs.
 */
@Slf4j
@Component
public class PdfTextExtractor implements BookTextExtractor {

    @Override
    public String extractText(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("Invalid PDF file provided for text extraction: {}", file);
            return null;
        }

        try (RandomAccessReadBufferedFile randomAccessRead = new RandomAccessReadBufferedFile(file);
             PDDocument document = Loader.loadPDF(randomAccessRead)) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            
            String text = stripper.getText(document);
            
            if (text == null || text.isBlank()) {
                log.debug("No text content found in PDF: {} (might be scanned/image-only)", file.getName());
                return null;
            }
            
            log.debug("Successfully extracted {} characters from PDF: {}", text.length(), file.getName());
            return text;
            
        } catch (Exception e) {
            log.error("Failed to extract text from PDF: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    @Override
    public boolean supports(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf");
    }
}

