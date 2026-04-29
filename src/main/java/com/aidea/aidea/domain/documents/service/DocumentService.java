package com.aidea.aidea.domain.documents.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import com.aidea.aidea.domain.documents.dto.DocumentCreateRequest;
import com.aidea.aidea.domain.documents.dto.DocumentCreateResponse;
import com.aidea.aidea.domain.documents.dto.DocumentDetail;
import com.aidea.aidea.domain.documents.dto.DocumentSummary;
import com.aidea.aidea.domain.documents.dto.DocumentUpdateRequest;
import com.aidea.aidea.domain.documents.dto.DocumentUpdateResponse;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
// import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;

    /**
     * 유저의 팀스페이스 내 문서 목록 조회
     */
    public List<DocumentSummary> getDocuments(String teamspaceId) {

        List<Document> documents = documentRepository.findByTeamspaceId(teamspaceId);

        return documents.stream()
                .map(DocumentSummary::from)
                .toList();
    }

    // 문서 생성 
    public DocumentCreateResponse createDocument(DocumentCreateRequest request) {
        
        String title = request.getTitle();

        if (title == null || title.isBlank()) {
            title = request.getType().name();
        }

        Document document = Document.builder()
                .teamspaceId(request.getTeamspaceId())
                .type(request.getType())
                .title(title)
                .build();

        Document saved = documentRepository.save(document);

        return DocumentCreateResponse.from(saved);
        
    }

    // 문서 상세 조회
    public DocumentDetail getDocumentDetail(String documentId) {

    Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));

    return DocumentDetail.from(document);
}

    // 문서 제목 수정
    @Transactional
    public DocumentUpdateResponse updateTitle(String documentId, DocumentUpdateRequest request) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));

        document.setTitle(request.getTitle());

        return DocumentUpdateResponse.from(document);
    }

    // 문서 삭제
    @Transactional
    public void deleteDocument(String documentId) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));

        // IDEA 문서는 삭제 불가
        if (document.getType() == DocumentType.IDEA) {
            throw new IllegalStateException("아이디어 문서는 삭제할 수 없습니다.");
        }

        documentRepository.delete(document);
    }

    // 문서 다운로드
    public byte[] exportDocument(String documentId, String format) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));

        // 문서 변환 로직 (md or pdf)
        if ("md".equalsIgnoreCase(format)) {
            return convertToMarkdown(document);
        }

        if ("pdf".equalsIgnoreCase(format)) {
            return convertToPdf(document);
        }

        throw new IllegalArgumentException("지원하지 않는 포맷입니다.");
    }

    private byte[] convertToMarkdown(Document document) {

        String md = """
                # %s

                type: %s

                createdAt: %s
                """.formatted(
                document.getTitle(),
                document.getType(),
                document.getCreatedAt()
        );

        return md.getBytes();
    }

    // PDF 변환 로직
    public byte[] convertToPdf(Document document) {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // 1. PDF Writer 생성
            PdfWriter writer = new PdfWriter(baos);

            // 2. PDF Document 생성
            PdfDocument pdf = new PdfDocument(writer);

            // 3. Layout Document
            com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdf);

            // 4. 내용 작성
            doc.add(new Paragraph("Document Title: " + document.getTitle()));
            doc.add(new Paragraph("Type: " + document.getType()));
            doc.add(new Paragraph("Created At: " + document.getCreatedAt()));
            doc.add(new Paragraph("Updated At: " + document.getUpdatedAt()));

            // 5. 닫기 (중요)
            doc.close();

            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("PDF 생성 실패", e);
        }
    }
}
