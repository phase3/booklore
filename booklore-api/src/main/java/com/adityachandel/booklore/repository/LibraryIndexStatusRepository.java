package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.LibraryIndexStatusEntity;
import com.adityachandel.booklore.model.enums.IndexStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LibraryIndexStatusRepository extends JpaRepository<LibraryIndexStatusEntity, Long> {
    
    List<LibraryIndexStatusEntity> findByStatus(IndexStatus status);
    
    List<LibraryIndexStatusEntity> findByLibraryIdIn(List<Long> libraryIds);
}

