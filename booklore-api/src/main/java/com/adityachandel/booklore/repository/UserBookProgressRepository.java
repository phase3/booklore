package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.dto.CompletionTimelineDto;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserBookProgressRepository extends JpaRepository<UserBookProgressEntity, Long> {

    Optional<UserBookProgressEntity> findByUserIdAndBookId(Long userId, Long bookId);

    List<UserBookProgressEntity> findByUserIdAndBookIdIn(Long userId, Set<Long> bookIds);

    @Query("""
        SELECT ubp FROM UserBookProgressEntity ubp
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN (
              SELECT ksb.bookId FROM KoboSnapshotBookEntity ksb
              WHERE ksb.snapshot.id = :snapshotId
          )
          AND (
              (ubp.readStatusModifiedTime IS NOT NULL AND (
                  ubp.koboStatusSentTime IS NULL
                  OR ubp.readStatusModifiedTime > ubp.koboStatusSentTime
              ))
              OR
              (ubp.koboProgressReceivedTime IS NOT NULL AND (
                  ubp.koboProgressSentTime IS NULL
                  OR ubp.koboProgressReceivedTime > ubp.koboProgressSentTime
              ))
          )
    """)
    List<UserBookProgressEntity> findAllBooksNeedingKoboSync(
            @Param("userId") Long userId,
            @Param("snapshotId") String snapshotId
    );

    @Query(value = """
            SELECT
                CAST(strftime('%Y', datetime(COALESCE(date_finished, read_status_modified_time, last_read_time) / 1000, 'unixepoch')) AS INTEGER) as year,
                CAST(strftime('%m', datetime(COALESCE(date_finished, read_status_modified_time, last_read_time) / 1000, 'unixepoch')) AS INTEGER) as month,
                read_status as readStatus,
                COUNT(*) as bookCount
            FROM user_book_progress
            WHERE user_id = :userId
            AND read_status IS NOT NULL
            AND read_status NOT IN ('UNSET', 'UNREAD')
            AND COALESCE(date_finished, read_status_modified_time, last_read_time) IS NOT NULL
            AND CAST(strftime('%Y', datetime(COALESCE(date_finished, read_status_modified_time, last_read_time) / 1000, 'unixepoch')) AS INTEGER) = :year
            GROUP BY year, month, read_status
            ORDER BY year DESC, month DESC
            """, nativeQuery = true)
    List<CompletionTimelineDto> findCompletionTimelineByUser(@Param("userId") Long userId, @Param("year") int year);

    @Modifying
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.readStatus = :readStatus,
            ubp.readStatusModifiedTime = :modifiedTime,
            ubp.dateFinished = :dateFinished
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkUpdateReadStatus(
            @Param("userId") Long userId,
            @Param("bookIds") List<Long> bookIds,
            @Param("readStatus") com.adityachandel.booklore.model.enums.ReadStatus readStatus,
            @Param("modifiedTime") java.time.Instant modifiedTime,
            @Param("dateFinished") java.time.Instant dateFinished
    );

    @Query("""
        SELECT ubp.book.id FROM UserBookProgressEntity ubp
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    Set<Long> findExistingProgressBookIds(@Param("userId") Long userId, @Param("bookIds") Set<Long> bookIds);

    @Modifying
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.readStatus = NULL,
            ubp.readStatusModifiedTime = :modifiedTime,
            ubp.lastReadTime = NULL,
            ubp.dateFinished = NULL,
            ubp.pdfProgress = NULL,
            ubp.pdfProgressPercent = NULL,
            ubp.epubProgress = NULL,
            ubp.epubProgressPercent = NULL,
            ubp.cbxProgress = NULL,
            ubp.cbxProgressPercent = NULL
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkResetBookloreProgress(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds, @Param("modifiedTime") java.time.Instant modifiedTime);

    @Modifying
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.koreaderProgress = NULL,
            ubp.koreaderProgressPercent = NULL,
            ubp.koreaderDeviceId = NULL,
            ubp.koreaderDevice = NULL,
            ubp.koreaderLastSyncTime = NULL
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkResetKoreaderProgress(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds);

    @Modifying
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.koboProgressPercent = NULL,
            ubp.koboLocation = NULL,
            ubp.koboLocationType = NULL,
            ubp.koboLocationSource = NULL,
            ubp.koboProgressReceivedTime = NULL
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkResetKoboProgress(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds);

    @Modifying
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.personalRating = :rating
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkUpdatePersonalRating(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds, @Param("rating") Integer rating);
}
