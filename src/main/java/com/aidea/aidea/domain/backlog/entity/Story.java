package com.aidea.aidea.domain.backlog.entity;

import com.aidea.aidea.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "stories",
    uniqueConstraints = @UniqueConstraint(columnNames = {"teamspace_id", "number"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "number", nullable = false)
    private Long number;

    @Column(name = "teamspace_id", nullable = false, length = 100)
    private String teamspaceId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoryStatus status = StoryStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    private LocalDate dueDate;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoryEpic> storyEpics = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static Story create(Long number, String teamspaceId, String title, String body,
                               Priority priority, User assignee, User reporter,
                               LocalDate dueDate, int position) {
        Story s = new Story();
        s.number = number;
        s.teamspaceId = teamspaceId;
        s.title = title;
        s.body = body;
        s.priority = priority;
        s.assignee = assignee;
        s.reporter = reporter;
        s.dueDate = dueDate;
        s.position = position;
        s.status = StoryStatus.OPEN;
        return s;
    }

    public void update(String title, String body, StoryStatus status,
                       Priority priority, User assignee, LocalDate dueDate) {
        this.title = title;
        this.body = body;
        this.priority = priority;
        this.assignee = assignee;
        this.dueDate = dueDate;
        if (this.status != status) {
            this.status = status;
            if (status == StoryStatus.DONE || status == StoryStatus.CLOSED) {
                this.closedAt = LocalDateTime.now();
            }
        }
    }

    public void updateStatus(StoryStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            if (newStatus == StoryStatus.DONE || newStatus == StoryStatus.CLOSED) {
                this.closedAt = LocalDateTime.now();
            }
        }
    }

    public void updatePosition(int position) {
        this.position = position;
    }
}
