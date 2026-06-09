package com.aidea.aidea.domain.draft.entity;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.draft.converter.DraftAnswersConverter;
import com.aidea.aidea.domain.draft.converter.DraftQuestionsConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Draft {

    @Id
    private String id;  //UUID

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Setter
    @Enumerated(EnumType.STRING)
    private DraftStatus status;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String content; //Ai 생성 마크다운 (성공 시)

    @Setter
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Setter
    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "idea_context", columnDefinition = "MEDIUMTEXT")
    private String ideaContext;

    @Setter
    @Column(name = "questions", columnDefinition = "JSON")
    @Convert(converter = DraftQuestionsConverter.class)
    private List<DraftQuestion> questions;

    @Setter
    @Column(name = "answers", columnDefinition = "JSON")
    @Convert(converter = DraftAnswersConverter.class)
    private List<DraftAnswer> answers;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static Draft create(String id, Document document, String ideaContext) {
        Draft draft = new Draft();
        draft.id = id;
        draft.document = document;
        draft.status = DraftStatus.PENDING;
        draft.ideaContext = ideaContext;
        draft.createdAt = LocalDateTime.now();
        return draft;
    }
}