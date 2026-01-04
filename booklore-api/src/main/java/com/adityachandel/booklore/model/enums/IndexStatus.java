package com.adityachandel.booklore.model.enums;

/**
 * Status of full-text search indexing for a library.
 */
public enum IndexStatus {
    NONE,           // Never indexed
    IN_PROGRESS,    // Currently indexing
    COMPLETE,       // Successfully indexed
    FAILED          // Indexing failed
}

