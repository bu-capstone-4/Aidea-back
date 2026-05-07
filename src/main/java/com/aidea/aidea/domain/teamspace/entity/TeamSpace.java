package com.aidea.aidea.domain.teamspace.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import com.aidea.aidea.domain.documents.entity.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teamspace")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpace {

    @Id
    @Column(name = "teamspace_id", nullable = false, length = 100)
    private String teamspaceId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TeamSpaceStatus status;

    // Document가 teamspaceId (String)으로만 관리되므로 양방향 매핑 제거
    // 대신 읽기 전용 조회용 매핑으로 변경
    @OneToMany
    @JoinColumn(name = "teamspace_id", referencedColumnName = "teamspace_id", insertable = false, updatable = false)
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}