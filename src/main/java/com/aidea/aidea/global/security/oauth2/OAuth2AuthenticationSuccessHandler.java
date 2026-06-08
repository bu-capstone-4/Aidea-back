package com.aidea.aidea.global.security.oauth2;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.invitation.service.InvitationService;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.security.jwt.JwtTokenProvider;
import com.aidea.aidea.global.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final CookieUtils cookieUtils;
    private final InvitationService invitationService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2User oAuth2User = oauthToken.getPrincipal();
            String provider = oauthToken.getAuthorizedClientRegistrationId();

            Object rawId = oAuth2User.getAttribute("id");
            log.debug("[AUTH] oauth2 callback provider={} rawIdType={}",
                    provider, rawId != null ? rawId.getClass().getName() : "null");

            if (rawId == null) {
                throw new IllegalStateException("GitHub OAuth2 응답에 id 속성이 없습니다.");
            }
            String providerId = String.valueOf(rawId);

            User user = userRepository.findByProviderAndProviderId(provider, providerId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            Long userId = user.getId();
            if (userId == null) {
                throw new IllegalStateException("DB에서 조회한 User의 id가 null입니다. providerId=" + providerId);
            }

            String accessToken = jwtTokenProvider.createAccessToken(userId);
            String refreshToken = jwtTokenProvider.createRefreshToken(userId);

            user.updateRefreshToken(refreshToken);
            userRepository.save(user);

            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.createAccessTokenCookie(accessToken).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.createRefreshTokenCookie(refreshToken).toString());

            log.info("[AUTH] oauth2 login userId={} provider={}", userId, provider);

            // 초대 링크로 진입한 경우 자동 수락 처리
            String pendingInviteToken = (String) request.getAttribute(CustomAuthorizationRequestResolver.INVITE_TOKEN_PARAM);
            if (pendingInviteToken != null) {
                try {
                    String docId = invitationService.acceptInvitation(pendingInviteToken, userId);
                    String target = (docId != null) ? "/main/" + docId : "/";
                    getRedirectStrategy().sendRedirect(request, response, frontendUrl + target);
                    return;
                } catch (CustomException ex) {
                    log.warn("[AUTH] 초대 자동 수락 실패 token={} reason={}", pendingInviteToken, ex.getMessage());
                    getRedirectStrategy().sendRedirect(request, response,
                            frontendUrl + "/?error=" + ex.getErrorCode().getCode());
                    return;
                } catch (Exception ex) {
                    log.warn("[AUTH] 초대 자동 수락 실패 token={} reason={}", pendingInviteToken, ex.getMessage());
                    getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/?error=INTERNAL_ERROR");
                    return;
                }
            }

            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/");
        } catch (Exception e) {
            log.error("[AUTH] oauth2 login failed reason={}", e.getMessage(), e);
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=server");
        }
    }
}
