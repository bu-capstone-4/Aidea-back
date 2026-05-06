package com.aidea.aidea.domain.teamspace.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import com.aidea.aidea.domain.document.entity.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity // JPA 엔티티 선언
@Table(name = "teamspace") // 테이블명 지정
@Getter // getter 자동 생성
@NoArgsConstructor // 기본 생성자 생성
@AllArgsConstructor // 전체 생성자 생성
@Builder // 빌더 패턴 적용
public class TeamSpace {

    @Id // PK 설정
    @Column(name = "teamspace_id", nullable = false, length = 100) // String PK 컬럼
    private String teamspaceId; // 팀스페이스 ID

    @Column(name = "name", nullable = false, length = 50) // 이름 길이 제한 50
    private String name; // 팀스페이스 이름

    @Enumerated(EnumType.STRING) // enum을 문자열로 저장
    @Column(name = "status", nullable = false)
    private TeamSpaceStatus status; // 상태 (CREATING, CREATED)

    @OneToMany(mappedBy = "teamSpace", cascade = CascadeType.ALL, orphanRemoval = true) // Document와 1:N 관계
    @Builder.Default
    private List<Document> documents = new ArrayList<>(); // 문서 리스트

    // @OneToMany(mappedBy = "teamSpace", cascade = CascadeType.ALL, orphanRemoval = true) // Member와 1:N 관계
    // private List<TeamMember> members = new ArrayList<>(); // 멤버 리스트
    // 회원 엔티티 만들어지면 가져오기

    @CreationTimestamp // 생성 시간 자동 저장
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // 생성일시
}