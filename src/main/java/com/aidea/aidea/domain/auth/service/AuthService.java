package com.aidea.aidea.domain.auth.service;

import com.aidea.aidea.domain.auth.dto.response.UserResponse;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        return jwtTokenProvider.createAccessToken(userId);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateRefreshToken(null);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }
}
