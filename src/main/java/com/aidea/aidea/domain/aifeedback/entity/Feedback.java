package com.aidea.aidea.domain.aifeedback.entity;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.aifeedback.converter.QuestionsConverter;
import com.aidea.aidea.domain.aifeedback.converter.AnswersConverter;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    private String id;   //UUID 문자열

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "additional_request", columnDefinition = "TEXT")
    private String additionalRequest;

    @Column(name = "original_markdown", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String originalMarkdown;

    @Column(name = "revised_markdown", columnDefinition = "MEDIUMTEXT")
    private String revisedMarkdown;

    @Column(name = "questions", columnDefinition = "JSON")
    @Convert(converter = QuestionsConverter.class)
    private List<Question> questions;

    @Column(name = "answers", columnDefinition = "JSON")
    @Convert(converter = AnswersConverter.class)
    private List<Answer> answers;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Feedback create(String id, Document document, User requestedBy, String originalMarkdown, String additionalRequest) {
        Feedback f = new Feedback();

        f.id = id;
        f.document = document;
        f.requestedBy = requestedBy;
        f.originalMarkdown = originalMarkdown;
        f.additionalRequest = additionalRequest;
        f.status = FeedbackStatus.PENDING;
        f.createdAt = LocalDateTime.now();
        return f;
    }
}
