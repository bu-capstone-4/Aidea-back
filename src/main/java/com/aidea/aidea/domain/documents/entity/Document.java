package com.aidea.aidea.domain.documents.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Document {

    @Id
    private String docId;

    private LocalDateTime updatedAt;
}