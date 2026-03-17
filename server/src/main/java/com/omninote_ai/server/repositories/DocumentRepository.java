package com.omninote_ai.server.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.omninote_ai.server.entity.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
}
