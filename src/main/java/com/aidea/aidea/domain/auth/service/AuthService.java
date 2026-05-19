package com.aidea.aidea.domain.auth.service;

import com.aidea.aidea.domain.auth.dto.response.UserResponse;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("[AUTH] tokenRefresh failed reason=INVALID_REFRESH_TOKEN");
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        log.debug("[AUTH] tokenRefresh userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!refreshToken.equals(user.getRefreshToken())) {
            log.warn("[AUTH] tokenRefresh userId={} failed reason=REFRESH_TOKEN_MISMATCH", userId);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        log.info("[AUTH] tokenRefresh userId={} success", userId);
        return jwtTokenProvider.createAccessToken(userId);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateRefreshToken(null);
        userRepository.save(user);
        log.info("[AUTH] logout userId={}", userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        log.debug("[AUTH] getCurrentUser userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }
}
