package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.model.enums.IndexStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "library_index_status")
public class LibraryIndexStatusEntity {

    @Id
    @Column(name = "library_id")
    private Long libraryId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", insertable = false, updatable = false)
    private LibraryEntity library;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private IndexStatus status = IndexStatus.NONE;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "indexed_book_count", nullable = false)
    @Builder.Default
    private Integer indexedBookCount = 0;

    @Column(name = "total_book_count", nullable = false)
    @Builder.Default
    private Integer totalBookCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

