package com.aidea.aidea.global.security.oauth2;

import java.util.Map;

public class OAuth2UserInfo {

    //GitHub이 보내준 Map을 받아서 저장함
    private final Map<String, Object> attributes;

    //생성자
    public OAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    //GitHub 고유 ID 꺼내기
    public String getId() {
        return (String) attributes.get("id");
    }

    //이메일 꺼내기
    public String getEmail() {
        return (String) attributes.get("email");
    }

    //이름 꺼내기
    public String getName() {
        String name = (String) attributes.get("name");
        return name != null ? name : (String) attributes.get("login"); //GitHub는 name이 null일 수 있다. -> 그런 경우 login(Git Id)을 대신 쓰게...~
    }

    //프로필 사진 URL 꺼내기
    public String getProfileImageUrl() {
        return (String) attributes.get("avatar_url");
    }
}
