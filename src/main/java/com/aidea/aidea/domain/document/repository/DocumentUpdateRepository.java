package com.aidea.aidea.domain.document.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidea.aidea.domain.document.entity.DocumentUpdate;

public interface DocumentUpdateRepository extends JpaRepository<DocumentUpdate, Long> {
    List<DocumentUpdate> findByDocIdOrderById(String docId);
}