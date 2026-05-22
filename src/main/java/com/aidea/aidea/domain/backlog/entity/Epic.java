package com.aidea.aidea.domain.backlog.entity;

import com.aidea.aidea.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "epics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Epic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String teamspaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static Epic create(String teamspaceId, String name, String color, String description, User createdBy) {
        Epic e = new Epic();
        e.teamspaceId = teamspaceId;
        e.name = name;
        e.color = color;
        e.description = description;
        e.createdBy = createdBy;
        return e;
    }

    public void update(String name, String color, String description) {
        this.name = name;
        this.color = color;
        this.description = description;
    }
}
