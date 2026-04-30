package com.aidea.aidea.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users") // 테이블 이름 지정
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 빈 생성자 자동 생성(JPA)
@AllArgsConstructor // 모든 필드를 받는 생성자 자동 생성
@Builder // 객체 만들 때 사용 패턴
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //DB가 자동으로
    private Long id;

    @Column(nullable = false, unique = true, length = 255) //비어있으면 안 됨(필수), 같은 이메일 중복 안 됨, 최대 255글자
    private String email;

    @Column(length = 100)
    private String name;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 50)
    private String provider; //github - (어디서 로그인했는지 기록)

    @Column(length = 255)
    private String providerId; //github이 부여한 고유 ID

    @Column(columnDefinition = "TEXT")
    private String refreshToken; //JWT 갱신 토큰 (TEXT 타입)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist // DB에 처음 저장될 때 자동 실행(가입 시간 기록)
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate //DB에 수정될 때 자동 실행(수정 시간 갱신)
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    //정보 바꾸는 방법(로그인할 때마다 최신 정보로 업데이트)
    public void updateOAuthInfo(String name, String profileImageUrl) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

}
