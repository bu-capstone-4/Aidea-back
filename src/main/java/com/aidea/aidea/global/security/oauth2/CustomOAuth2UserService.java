package com.aidea.aidea.global.security.oauth2;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        OAuth2UserInfo userInfo = new OAuth2UserInfo(oAuth2User.getAttributes());

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = userInfo.getId();
        String githubLogin = userInfo.getLogin();
        String name = userInfo.getName();
        String profileImageUrl = userInfo.getProfileImageUrl();
        String githubAccessToken = userRequest.getAccessToken().getTokenValue();

        // GitHub 이메일 비공개 사용자 대응: noreply 이메일 fallback
        String email = userInfo.getEmail();
        if (email == null) {
            email = providerId + "+noreply@users.noreply.github.com";
        }

        final String finalEmail = email;

        // providerId 기반으로 사용자 조회 (이메일보다 안정적인 고유 식별자)
        userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existingUser -> {
                    existingUser.updateOAuthInfo(name, profileImageUrl, githubLogin, githubAccessToken);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(finalEmail)
                            .name(name)
                            .profileImageUrl(profileImageUrl)
                            .provider(provider)
                            .providerId(providerId)
                            .githubLogin(githubLogin)
                            .githubAccessToken(githubAccessToken)
                            .build();
                    return userRepository.save(newUser);
                });

        return oAuth2User;
    }
}
