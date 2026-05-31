package com.aidea.aidea.domain.backlog.entity;

import com.aidea.aidea.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    private Long number;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EpicStatus status = EpicStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private IssueType issueType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    private LocalDate dueDate;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int position = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = EpicStatus.OPEN;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static Epic create(String teamspaceId, Long number, String name, String color, String description,
                              Priority priority, IssueType issueType, User assignee, User createdBy,
                              LocalDate dueDate, int position) {
        Epic e = new Epic();
        e.teamspaceId = teamspaceId;
        e.number = number;
        e.name = name;
        e.color = color;
        e.description = description;
        e.status = EpicStatus.OPEN;
        e.priority = priority;
        e.issueType = issueType;
        e.assignee = assignee;
        e.createdBy = createdBy;
        e.dueDate = dueDate;
        e.position = position;
        return e;
    }

    public void update(String name, String color, String description,
                       Priority priority, IssueType issueType, User assignee, LocalDate dueDate) {
        this.name = name;
        this.color = color;
        this.description = description;
        this.priority = priority;
        this.issueType = issueType;
        this.assignee = assignee;
        this.dueDate = dueDate;
    }

    public void updateStatus(EpicStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            if (newStatus == EpicStatus.DONE || newStatus == EpicStatus.CLOSED) {
                this.closedAt = LocalDateTime.now();
            }
        }
    }

    public void updatePosition(int position) {
        this.position = position;
    }
}
