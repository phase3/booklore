package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.JwtSecretEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JwtSecretRepository extends JpaRepository<JwtSecretEntity, Long> {

    Optional<JwtSecretEntity> findFirstByOrderByCreatedAtDesc();

    default Optional<String> findLatestSecret() {
        return findFirstByOrderByCreatedAtDesc().map(JwtSecretEntity::getSecret);
    }
}
