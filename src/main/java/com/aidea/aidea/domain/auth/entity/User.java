package com.aidea.aidea.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // GitHub 이메일 비공개 사용자 대응: nullable 허용
    @Column(unique = true, length = 255)
    private String email;

    @Column(length = 100)
    private String name;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 50, nullable = false)
    private String provider;

    // GitHub 숫자 ID - 진정한 고유 식별자
    @Column(nullable = false, unique = true, length = 255)
    private String providerId;

    // GitHub 사용자명 (kang-min-seok 형태) - API URL 구성에 사용
    @Column(length = 100, nullable = false)
    private String githubLogin;

    // GitHub API 호출용 토큰
    @Column(columnDefinition = "TEXT")
    private String githubAccessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateOAuthInfo(String name, String profileImageUrl,
                                String githubLogin, String githubAccessToken) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.githubLogin = githubLogin;
        this.githubAccessToken = githubAccessToken;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
