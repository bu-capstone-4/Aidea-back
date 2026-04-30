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
//로그인 성공 후 뭐 할지
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    //DB에서 유저를 찾거나 저장해야 됨
    private final UserRepository userRepository;

    @Override
    //GitHub이 정보 줄 때 자동 호출
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        //GitHub에서 사용자 정보 받기
        OAuth2User oAuth2User = super.loadUser(userRequest);

        //OAuth2UserInfo로 정보 정리
        OAuth2UserInfo userInfo = new OAuth2UserInfo(oAuth2User.getAttributes());

        String name = userInfo.getName();
        String email = userInfo.getEmail();
        String profileImageUrl = userInfo.getProfileImageUrl();
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = userInfo.getId();

        //기존 회원인지 확인 -> 저장 or 업데이트
        //이메일로 DB 조회
        //→ 있으면: 이름/프로필 업데이트
        //→ 없으면: 새 유저 생성 (자동 회원가입)
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.updateOAuthInfo(name, profileImageUrl);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .profileImageUrl(profileImageUrl)
                            .provider(provider)
                            .providerId(providerId)
                            .build();
                    return userRepository.save(newUser);
                });

        return oAuth2User;

    }

}
