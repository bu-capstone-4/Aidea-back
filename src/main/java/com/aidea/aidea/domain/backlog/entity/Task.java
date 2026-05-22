package com.aidea.aidea.domain.backlog.entity;

import com.aidea.aidea.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private boolean isCompleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(nullable = false)
    private int position;

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

    public static Task create(Story story, String title, User assignee, int position, User createdBy) {
        Task t = new Task();
        t.story = story;
        t.title = title;
        t.assignee = assignee;
        t.position = position;
        t.createdBy = createdBy;
        t.isCompleted = false;
        return t;
    }

    public void update(String title, boolean isCompleted, User assignee) {
        this.title = title;
        this.isCompleted = isCompleted;
        this.assignee = assignee;
    }
}
