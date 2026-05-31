package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.auth.entity.User;

public record UserResponse(
        Long id,
        String name,
        String githubLogin,
        String profileImageUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getGithubLogin(),
                user.getProfileImageUrl()
        );
    }
}
