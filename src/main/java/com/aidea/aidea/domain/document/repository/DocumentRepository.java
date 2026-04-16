package com.aidea.aidea.domain.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidea.aidea.domain.document.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String>{

}
