package com.adityachandel.booklore.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notification sent when the full-text search index changes.
 * Used to signal the frontend to clear/refresh search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexChangedNotification {
    
    public enum ChangeType {
        BOOK_ADDED,
        BOOK_REMOVED,
        LIBRARY_REINDEXED
    }
    
    private Long libraryId;
    private Long bookId;
    private ChangeType changeType;
}

