package com.aidea.aidea.domain.auth.dto.response;

import com.aidea.aidea.domain.auth.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "사용자 정보 응답")
public class UserResponse {

    @Schema(description = "사용자 ID", example = "1")
    private Long id;

    @Schema(description = "이메일", example = "user@example.com")
    private String email;

    @Schema(description = "이름", example = "홍길동")
    private String name;

    @Schema(description = "프로필 이미지 URL")
    private String profileImageUrl;

    @Schema(description = "OAuth 제공자", example = "github")
    private String provider;

    @Schema(description = "GitHub 사용자명", example = "kang-min-seok")
    private String githubLogin;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImageUrl(user.getProfileImageUrl())
                .provider(user.getProvider())
                .githubLogin(user.getGithubLogin())
                .build();
    }
}
