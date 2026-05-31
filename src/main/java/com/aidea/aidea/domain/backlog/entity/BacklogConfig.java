package com.aidea.aidea.domain.backlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "backlog_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BacklogConfig {

    @Id
    @Column(length = 100)
    private String teamspaceId;

    /** 프론트엔드 / 백엔드 구분 (FE-001, BE-001 형식) */
    @Column(nullable = false)
    private boolean feBeEnabled = false;

    /** 에픽 이슈 유형 활성화 */
    @Column(nullable = false)
    private boolean epicEnabled = false;

    /** 스토리 이슈 유형 활성화 */
    @Column(nullable = false)
    private boolean storyEnabled = false;

    /** 우선순위 추가 필드 활성화 */
    @Column(nullable = false)
    private boolean priorityEnabled = false;

    /** 스프린트 추가 필드 활성화 */
    @Column(nullable = false)
    private boolean sprintEnabled = false;

    /** 마감일 추가 필드 활성화 */
    @Column(nullable = false)
    private boolean dueDateEnabled = false;

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

    public static BacklogConfig create(String teamspaceId, boolean feBeEnabled,
                                       boolean epicEnabled, boolean storyEnabled,
                                       boolean priorityEnabled, boolean sprintEnabled,
                                       boolean dueDateEnabled) {
        BacklogConfig c = new BacklogConfig();
        c.teamspaceId = teamspaceId;
        c.feBeEnabled = feBeEnabled;
        c.epicEnabled = epicEnabled;
        c.storyEnabled = storyEnabled;
        c.priorityEnabled = priorityEnabled;
        c.sprintEnabled = sprintEnabled;
        c.dueDateEnabled = dueDateEnabled;
        return c;
    }

    public void update(boolean feBeEnabled, boolean epicEnabled, boolean storyEnabled,
                       boolean priorityEnabled, boolean sprintEnabled, boolean dueDateEnabled) {
        this.feBeEnabled = feBeEnabled;
        this.epicEnabled = epicEnabled;
        this.storyEnabled = storyEnabled;
        this.priorityEnabled = priorityEnabled;
        this.sprintEnabled = sprintEnabled;
        this.dueDateEnabled = dueDateEnabled;
    }
}
