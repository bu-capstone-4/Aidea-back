package com.aidea.aidea.domain.backlog.entity;

import com.aidea.aidea.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;

    @Column(length = 100)
    private String teamspaceId;

    private Long number;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private boolean isCompleted = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StoryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private IssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(length = 100)
    private String sprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    private LocalDate dueDate;

    @Column(nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static Task create(Story story, String title, IssueType issueType, User assignee, int position, User createdBy) {
        Task t = new Task();
        t.story = story;
        t.teamspaceId = story.getTeamspaceId();
        t.title = title;
        t.issueType = issueType;
        t.assignee = assignee;
        t.position = position;
        t.createdBy = createdBy;
        t.isCompleted = false;
        return t;
    }

    public static Task createStandalone(String teamspaceId, Long number, String title,
                                        StoryStatus status, Priority priority, IssueType issueType,
                                        String sprint, User assignee, User reporter,
                                        LocalDate dueDate, int position) {
        Task t = new Task();
        t.teamspaceId = teamspaceId;
        t.number = number;
        t.title = title;
        t.status = status != null ? status : StoryStatus.OPEN;
        t.priority = priority;
        t.issueType = issueType;
        t.sprint = sprint;
        t.assignee = assignee;
        t.reporter = reporter;
        t.dueDate = dueDate;
        t.position = position;
        t.createdBy = reporter;
        t.isCompleted = false;
        return t;
    }

    public void update(String title, IssueType issueType, User assignee) {
        this.title = title;
        this.issueType = issueType;
        this.assignee = assignee;
    }

    public void updateStandalone(String title, StoryStatus status, Priority priority, IssueType issueType,
                                 String sprint, User assignee, LocalDate dueDate) {
        this.title = title;
        if (status != null) this.status = status;
        this.priority = priority;
        this.issueType = issueType;
        this.sprint = sprint;
        this.assignee = assignee;
        this.dueDate = dueDate;
    }

    public void updateStandaloneStatus(StoryStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            if (newStatus == StoryStatus.DONE || newStatus == StoryStatus.CLOSED) {
                this.closedAt = LocalDateTime.now();
            }
        }
    }

    public void toggleCompleted() {
        this.isCompleted = !this.isCompleted;
    }

    public void updatePosition(int position) {
        this.position = position;
    }

    public boolean isStandalone() {
        return this.story == null;
    }
}
