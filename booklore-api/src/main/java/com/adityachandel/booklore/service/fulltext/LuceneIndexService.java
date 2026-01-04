package com.adityachandel.booklore.service.fulltext;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.dto.ChapterContent;
import com.adityachandel.booklore.model.dto.FullTextSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing Lucene indexes for full-text search.
 * Provides per-library indexes stored at {pathConfig}/indexes/{libraryId}/
 * Supports both book-level and chapter-level indexing.
 */
@Slf4j
@Service
public class LuceneIndexService {

    private static final String INDEXES_DIR = "indexes";
    private static final String FIELD_BOOK_ID = "bookId";
    private static final String FIELD_LIBRARY_ID = "libraryId";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_AUTHORS = "authors";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_CHAPTER_INDEX = "chapterIndex";
    private static final String FIELD_CHAPTER_TITLE = "chapterTitle";
    private static final String FIELD_CHAPTER_HREF = "chapterHref";
    private static final String FIELD_HAS_CHAPTERS = "hasChapters";

    private final AppProperties appProperties;
    private final Analyzer analyzer;
    private final Map<Long, IndexWriter> writerCache;

    public LuceneIndexService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.analyzer = new StandardAnalyzer();
        this.writerCache = new ConcurrentHashMap<>();
    }

    /**
     * Gets the path to the index directory for a library.
     */
    public Path getIndexPath(long libraryId) {
        return Paths.get(appProperties.getPathConfig(), INDEXES_DIR, String.valueOf(libraryId));
    }

    /**
     * Checks if an index exists for the given library.
     */
    public boolean indexExists(long libraryId) {
        Path indexPath = getIndexPath(libraryId);
        if (!Files.exists(indexPath)) {
            return false;
        }
        try (Directory dir = FSDirectory.open(indexPath)) {
            return DirectoryReader.indexExists(dir);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Creates or opens an index for the given library.
     */
    public IndexWriter getIndexWriter(long libraryId) throws IOException {
        return writerCache.computeIfAbsent(libraryId, id -> {
            try {
                Path indexPath = getIndexPath(id);
                Files.createDirectories(indexPath);
                
                Directory dir = FSDirectory.open(indexPath);
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                
                return new IndexWriter(dir, config);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create index writer for library " + id, e);
            }
        });
    }

    /**
     * Clears the index for a library (used before re-indexing).
     */
    public void clearIndex(long libraryId) throws IOException {
        closeWriter(libraryId);
        
        Path indexPath = getIndexPath(libraryId);
        if (Files.exists(indexPath)) {
            // Delete all files in the index directory
            Files.walk(indexPath)
                    .sorted((a, b) -> b.compareTo(a)) // reverse order to delete files before directories
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete index file: {}", path, e);
                        }
                    });
        }
        
        log.info("Cleared index for library {}", libraryId);
    }

    /**
     * Indexes a book's content as a single document (for books without chapter support).
     */
    public void indexBook(long libraryId, long bookId, String title, String authors, String content) throws IOException {
        if (content == null || content.isBlank()) {
            log.debug("Skipping book {} - no content to index", bookId);
            return;
        }

        IndexWriter writer = getIndexWriter(libraryId);
        
        // First, delete any existing documents for this book
        writer.deleteDocuments(new Term(FIELD_BOOK_ID, String.valueOf(bookId)));
        
        // Create new document
        Document doc = new Document();
        doc.add(new StringField(FIELD_BOOK_ID, String.valueOf(bookId), Field.Store.YES));
        doc.add(new StringField(FIELD_LIBRARY_ID, String.valueOf(libraryId), Field.Store.YES));
        doc.add(new StoredField(FIELD_TITLE, title != null ? title : ""));
        doc.add(new StoredField(FIELD_AUTHORS, authors != null ? authors : ""));
        doc.add(new TextField(FIELD_CONTENT, content, Field.Store.YES));
        doc.add(new StringField(FIELD_HAS_CHAPTERS, "false", Field.Store.YES));
        
        writer.addDocument(doc);
        log.debug("Indexed book {} for library {}", bookId, libraryId);
    }

    /**
     * Indexes a book's chapters as separate documents (for chapter-level search).
     * 
     * @param libraryId the library ID
     * @param bookId the book ID
     * @param title the book title
     * @param authors the book authors
     * @param chapters list of chapter contents to index
     * @return number of chapters indexed
     */
    public int indexBookChapters(long libraryId, long bookId, String title, String authors, 
                                  List<ChapterContent> chapters) throws IOException {
        if (chapters == null || chapters.isEmpty()) {
            log.debug("Skipping book {} - no chapters to index", bookId);
            return 0;
        }

        IndexWriter writer = getIndexWriter(libraryId);
        
        // First, delete any existing documents for this book
        writer.deleteDocuments(new Term(FIELD_BOOK_ID, String.valueOf(bookId)));
        
        int indexedCount = 0;
        for (ChapterContent chapter : chapters) {
            if (chapter.getContent() == null || chapter.getContent().isBlank()) {
                continue;
            }
            
            // Create new document for this chapter
            Document doc = new Document();
            doc.add(new StringField(FIELD_BOOK_ID, String.valueOf(bookId), Field.Store.YES));
            doc.add(new StringField(FIELD_LIBRARY_ID, String.valueOf(libraryId), Field.Store.YES));
            doc.add(new StoredField(FIELD_TITLE, title != null ? title : ""));
            doc.add(new StoredField(FIELD_AUTHORS, authors != null ? authors : ""));
            doc.add(new TextField(FIELD_CONTENT, chapter.getContent(), Field.Store.YES));
            doc.add(new StringField(FIELD_HAS_CHAPTERS, "true", Field.Store.YES));
            
            // Chapter-specific fields
            doc.add(new StoredField(FIELD_CHAPTER_INDEX, chapter.getChapterIndex()));
            doc.add(new StoredField(FIELD_CHAPTER_TITLE, chapter.getChapterTitle() != null ? chapter.getChapterTitle() : ""));
            doc.add(new StoredField(FIELD_CHAPTER_HREF, chapter.getChapterHref() != null ? chapter.getChapterHref() : ""));
            
            writer.addDocument(doc);
            indexedCount++;
        }
        
        log.debug("Indexed {} chapters for book {} in library {}", indexedCount, bookId, libraryId);
        return indexedCount;
    }

    /**
     * Commits pending changes to the index.
     */
    public void commit(long libraryId) throws IOException {
        IndexWriter writer = writerCache.get(libraryId);
        if (writer != null) {
            writer.commit();
            log.debug("Committed index changes for library {}", libraryId);
        }
    }

    /**
     * Closes the index writer for a library.
     */
    public void closeWriter(long libraryId) {
        IndexWriter writer = writerCache.remove(libraryId);
        if (writer != null) {
            try {
                writer.close();
                log.debug("Closed index writer for library {}", libraryId);
            } catch (IOException e) {
                log.error("Failed to close index writer for library {}", libraryId, e);
            }
        }
    }

    /**
     * Searches across one or more library indexes.
     * Returns results with chapter information when available.
     * Supports Lucene query syntax including:
     * - Boolean operators: AND, OR, NOT (or &&, ||, !)
     * - Phrase search: "exact phrase"
     * - Wildcards: * (multiple chars), ? (single char)
     * - Fuzzy search: term~
     * - Proximity search: "term1 term2"~10
     */
    public List<FullTextSearchResult> search(List<Long> libraryIds, String queryString, int page, int pageSize) 
            throws IOException, ParseException {
        
        if (libraryIds == null || libraryIds.isEmpty() || queryString == null || queryString.isBlank()) {
            return List.of();
        }

        List<FullTextSearchResult> results = new ArrayList<>();
        
        // Collect all readers for the requested libraries
        List<IndexReader> readers = new ArrayList<>();
        for (Long libraryId : libraryIds) {
            if (!indexExists(libraryId)) {
                log.debug("Index does not exist for library {}", libraryId);
                continue;
            }
            
            try {
                Path indexPath = getIndexPath(libraryId);
                Directory dir = FSDirectory.open(indexPath);
                readers.add(DirectoryReader.open(dir));
            } catch (IOException e) {
                log.warn("Failed to open index for library {}: {}", libraryId, e.getMessage());
            }
        }
        
        if (readers.isEmpty()) {
            return List.of();
        }

        try {
            IndexReader combinedReader = readers.size() == 1 
                    ? readers.get(0) 
                    : new MultiReader(readers.toArray(new IndexReader[0]));
            
            IndexSearcher searcher = new IndexSearcher(combinedReader);
            
            QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
            parser.setAllowLeadingWildcard(true);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            Query query = parseQuery(parser, queryString);
            
            int start = page * pageSize;
            int numHits = start + pageSize;
            
            TopDocs topDocs = searcher.search(query, numHits);
            
            // Set up highlighter
            QueryScorer scorer = new QueryScorer(query);
            Fragmenter fragmenter = new SimpleSpanFragmenter(scorer, 150);
            Highlighter highlighter = new Highlighter(
                    new SimpleHTMLFormatter("<mark>", "</mark>"), 
                    scorer
            );
            highlighter.setTextFragmenter(fragmenter);
            
            ScoreDoc[] hits = topDocs.scoreDocs;
            int end = Math.min(start + pageSize, hits.length);
            
            for (int i = start; i < end; i++) {
                Document doc = searcher.storedFields().document(hits[i].doc);
                
                String content = doc.get(FIELD_CONTENT);
                List<String> highlights = new ArrayList<>();
                
                if (content != null) {
                    try {
                        String[] fragments = highlighter.getBestFragments(
                                analyzer, FIELD_CONTENT, content, 3);
                        
                        for (String fragment : fragments) {
                            if (fragment != null && !fragment.isBlank()) {
                                highlights.add(fragment.trim());
                            }
                        }
                    } catch (InvalidTokenOffsetsException e) {
                        log.debug("Failed to generate highlights for book {}", doc.get(FIELD_BOOK_ID));
                    }
                }
                
                // Build result with chapter info if available
                FullTextSearchResult.FullTextSearchResultBuilder resultBuilder = FullTextSearchResult.builder()
                        .bookId(Long.parseLong(doc.get(FIELD_BOOK_ID)))
                        .libraryId(Long.parseLong(doc.get(FIELD_LIBRARY_ID)))
                        .title(doc.get(FIELD_TITLE))
                        .authors(doc.get(FIELD_AUTHORS))
                        .score(hits[i].score)
                        .highlights(highlights);
                
                // Add chapter info if this is a chapter-indexed document
                String hasChapters = doc.get(FIELD_HAS_CHAPTERS);
                if ("true".equals(hasChapters)) {
                    IndexableField chapterIndexField = doc.getField(FIELD_CHAPTER_INDEX);
                    if (chapterIndexField != null) {
                        resultBuilder.chapterIndex(chapterIndexField.numericValue().intValue());
                    }
                    resultBuilder.chapterTitle(doc.get(FIELD_CHAPTER_TITLE));
                    resultBuilder.chapterHref(doc.get(FIELD_CHAPTER_HREF));
                }
                
                results.add(resultBuilder.build());
            }
            
            combinedReader.close();
            
        } finally {
            for (IndexReader reader : readers) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.warn("Failed to close index reader", e);
                }
            }
        }
        
        return results;
    }

    /**
     * Gets the total number of hits for a search query.
     */
    public long getTotalHits(List<Long> libraryIds, String queryString) throws IOException, ParseException {
        if (libraryIds == null || libraryIds.isEmpty() || queryString == null || queryString.isBlank()) {
            return 0;
        }

        List<IndexReader> readers = new ArrayList<>();
        for (Long libraryId : libraryIds) {
            if (!indexExists(libraryId)) {
                continue;
            }
            
            try {
                Path indexPath = getIndexPath(libraryId);
                Directory dir = FSDirectory.open(indexPath);
                readers.add(DirectoryReader.open(dir));
            } catch (IOException e) {
                log.warn("Failed to open index for library {}: {}", libraryId, e.getMessage());
            }
        }
        
        if (readers.isEmpty()) {
            return 0;
        }

        try {
            IndexReader combinedReader = readers.size() == 1 
                    ? readers.get(0) 
                    : new MultiReader(readers.toArray(new IndexReader[0]));
            
            IndexSearcher searcher = new IndexSearcher(combinedReader);
            
            QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
            parser.setAllowLeadingWildcard(true);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            Query query = parseQuery(parser, queryString);
            
            return searcher.count(query);
            
        } finally {
            for (IndexReader reader : readers) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.warn("Failed to close index reader", e);
                }
            }
        }
    }

    /**
     * Parses the query string with Lucene query syntax support.
     * If the query contains syntax errors, falls back to a simple escaped query.
     */
    private Query parseQuery(QueryParser parser, String queryString) throws ParseException {
        try {
            // Try to parse with full Lucene query syntax
            return parser.parse(queryString);
        } catch (ParseException e) {
            // If parsing fails (e.g., unbalanced quotes), fall back to escaped query
            log.debug("Query syntax error for '{}', falling back to simple search: {}", queryString, e.getMessage());
            return parser.parse(QueryParser.escape(queryString));
        }
    }

    /**
     * Gets the number of documents in a library's index.
     */
    public int getIndexedDocumentCount(long libraryId) {
        if (!indexExists(libraryId)) {
            return 0;
        }
        
        try {
            Path indexPath = getIndexPath(libraryId);
            try (Directory dir = FSDirectory.open(indexPath);
                 IndexReader reader = DirectoryReader.open(dir)) {
                return reader.numDocs();
            }
        } catch (IOException e) {
            log.warn("Failed to get document count for library {}: {}", libraryId, e.getMessage());
            return 0;
        }
    }
}
