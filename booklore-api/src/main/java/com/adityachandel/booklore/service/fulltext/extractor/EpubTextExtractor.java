package com.adityachandel.booklore.service.fulltext.extractor;

import com.adityachandel.booklore.model.dto.ChapterContent;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.MediaType;
import io.documentnode.epub4j.domain.MediaTypes;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.domain.SpineReference;
import io.documentnode.epub4j.domain.TOCReference;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts text content from EPUB files by parsing HTML content from spine items.
 * Uses epub4j for EPUB parsing and Jsoup for HTML text extraction.
 * Supports chapter-level extraction with titles and navigation hrefs.
 */
@Slf4j
@Component
public class EpubTextExtractor implements BookTextExtractor {

    private static final List<MediaType> MEDIA_TYPES = new ArrayList<>();
    
    static {
        MEDIA_TYPES.addAll(Arrays.asList(MediaTypes.mediaTypes));
        MEDIA_TYPES.add(null);
    }

    @Override
    public String extractText(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("Invalid EPUB file provided for text extraction: {}", file);
            return null;
        }

        StringBuilder textBuilder = new StringBuilder();

        try (ZipFile zip = new ZipFile(file)) {
            Book epub = new EpubReader().readEpubLazy(zip, "UTF-8", MEDIA_TYPES);
            
            // Extract text from spine items (the reading order)
            List<SpineReference> spineRefs = epub.getSpine().getSpineReferences();
            
            for (SpineReference spineRef : spineRefs) {
                Resource resource = spineRef.getResource();
                if (resource == null) continue;
                
                MediaType mediaType = resource.getMediaType();
                if (mediaType == null) continue;
                
                // Only process HTML/XHTML content
                String mimeType = mediaType.getName();
                if (!mimeType.contains("html") && !mimeType.contains("xml")) {
                    continue;
                }
                
                try {
                    byte[] data = resource.getData();
                    if (data == null || data.length == 0) continue;
                    
                    String html = new String(data, resource.getInputEncoding() != null 
                            ? resource.getInputEncoding() : "UTF-8");
                    
                    // Parse HTML and extract text
                    Document doc = Jsoup.parse(html);
                    String text = doc.body() != null ? doc.body().text() : doc.text();
                    
                    if (text != null && !text.isBlank()) {
                        textBuilder.append(text).append("\n\n");
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract text from EPUB resource {}: {}", 
                            resource.getHref(), e.getMessage());
                }
            }
            
            String result = textBuilder.toString().trim();
            
            if (result.isEmpty()) {
                log.debug("No text content found in EPUB: {}", file.getName());
                return null;
            }
            
            log.debug("Successfully extracted {} characters from EPUB: {}", result.length(), file.getName());
            return result;
            
        } catch (Exception e) {
            log.error("Failed to extract text from EPUB: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    @Override
    public List<ChapterContent> extractChapters(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("Invalid EPUB file provided for chapter extraction: {}", file);
            return List.of();
        }

        List<ChapterContent> chapters = new ArrayList<>();

        try (ZipFile zip = new ZipFile(file)) {
            Book epub = new EpubReader().readEpubLazy(zip, "UTF-8", MEDIA_TYPES);
            
            // Build a map from href to TOC title
            Map<String, String> hrefToTitle = buildTocTitleMap(epub);
            
            // Extract text from spine items (the reading order)
            List<SpineReference> spineRefs = epub.getSpine().getSpineReferences();
            
            for (int i = 0; i < spineRefs.size(); i++) {
                SpineReference spineRef = spineRefs.get(i);
                Resource resource = spineRef.getResource();
                if (resource == null) continue;
                
                MediaType mediaType = resource.getMediaType();
                if (mediaType == null) continue;
                
                // Only process HTML/XHTML content
                String mimeType = mediaType.getName();
                if (!mimeType.contains("html") && !mimeType.contains("xml")) {
                    continue;
                }
                
                try {
                    byte[] data = resource.getData();
                    if (data == null || data.length == 0) continue;
                    
                    String html = new String(data, resource.getInputEncoding() != null 
                            ? resource.getInputEncoding() : "UTF-8");
                    
                    // Parse HTML and extract text
                    Document doc = Jsoup.parse(html);
                    String text = doc.body() != null ? doc.body().text() : doc.text();
                    
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    
                    // Get href for this chapter
                    String href = resource.getHref();
                    
                    // Try to get title from TOC, otherwise extract from document
                    String chapterTitle = findChapterTitle(href, hrefToTitle, doc);
                    
                    chapters.add(ChapterContent.builder()
                            .chapterIndex(i)
                            .chapterTitle(chapterTitle)
                            .chapterHref(href)
                            .content(text)
                            .build());
                    
                } catch (Exception e) {
                    log.debug("Failed to extract chapter from EPUB resource {}: {}", 
                            resource.getHref(), e.getMessage());
                }
            }
            
            log.debug("Successfully extracted {} chapters from EPUB: {}", chapters.size(), file.getName());
            return chapters;
            
        } catch (Exception e) {
            log.error("Failed to extract chapters from EPUB: {}", file.getAbsolutePath(), e);
            return List.of();
        }
    }

    @Override
    public boolean supportsChapterExtraction() {
        return true;
    }

    @Override
    public boolean supports(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".epub");
    }

    /**
     * Builds a map from resource href to TOC title by traversing the table of contents.
     */
    private Map<String, String> buildTocTitleMap(Book epub) {
        Map<String, String> hrefToTitle = new HashMap<>();
        
        if (epub.getTableOfContents() != null) {
            List<TOCReference> tocRefs = epub.getTableOfContents().getTocReferences();
            if (tocRefs != null) {
                addTocReferencesToMap(tocRefs, hrefToTitle);
            }
        }
        
        return hrefToTitle;
    }

    /**
     * Recursively adds TOC references and their children to the href-to-title map.
     */
    private void addTocReferencesToMap(List<TOCReference> tocRefs, Map<String, String> hrefToTitle) {
        for (TOCReference tocRef : tocRefs) {
            if (tocRef.getResource() != null && tocRef.getTitle() != null) {
                String href = tocRef.getResource().getHref();
                // Handle hrefs with fragment identifiers
                String baseHref = href.contains("#") ? href.substring(0, href.indexOf("#")) : href;
                
                // Only set if not already set (prefer parent TOC entries)
                if (!hrefToTitle.containsKey(baseHref)) {
                    hrefToTitle.put(baseHref, tocRef.getTitle().trim());
                }
                // Also store the full href with fragment
                if (!hrefToTitle.containsKey(href)) {
                    hrefToTitle.put(href, tocRef.getTitle().trim());
                }
            }
            
            // Process children recursively
            if (tocRef.getChildren() != null && !tocRef.getChildren().isEmpty()) {
                addTocReferencesToMap(tocRef.getChildren(), hrefToTitle);
            }
        }
    }

    /**
     * Finds the chapter title using TOC mapping or by extracting from the document.
     */
    private String findChapterTitle(String href, Map<String, String> hrefToTitle, Document doc) {
        // First try exact match from TOC
        if (hrefToTitle.containsKey(href)) {
            return hrefToTitle.get(href);
        }
        
        // Try without fragment
        String baseHref = href.contains("#") ? href.substring(0, href.indexOf("#")) : href;
        if (hrefToTitle.containsKey(baseHref)) {
            return hrefToTitle.get(baseHref);
        }
        
        // Fall back to extracting from document headings
        return extractTitleFromDocument(doc, href);
    }

    /**
     * Extracts a chapter title from the document by looking for headings.
     */
    private String extractTitleFromDocument(Document doc, String href) {
        // Try heading tags in order of priority
        Elements h1Elements = doc.select("h1");
        if (!h1Elements.isEmpty()) {
            String title = h1Elements.first().text().trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        
        Elements h2Elements = doc.select("h2");
        if (!h2Elements.isEmpty()) {
            String title = h2Elements.first().text().trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        
        Elements h3Elements = doc.select("h3");
        if (!h3Elements.isEmpty()) {
            String title = h3Elements.first().text().trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        
        // Try title tag
        Element titleElement = doc.selectFirst("title");
        if (titleElement != null) {
            String title = titleElement.text().trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        
        // Generate a default title from the href
        String fileName = href;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        
        // Convert underscores/hyphens to spaces and capitalize
        fileName = fileName.replace("_", " ").replace("-", " ");
        if (!fileName.isEmpty()) {
            return Character.toUpperCase(fileName.charAt(0)) + fileName.substring(1);
        }
        
        return "Untitled Section";
    }
}
