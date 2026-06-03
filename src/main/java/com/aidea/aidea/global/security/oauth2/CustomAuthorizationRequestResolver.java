package com.aidea.aidea.global.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.HashMap;
import java.util.Map;

public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String INVITE_TOKEN_PARAM = "invite_token";

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(defaultResolver.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(defaultResolver.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest oauthRequest, HttpServletRequest request) {
        if (oauthRequest == null) return null;

        String inviteToken = request.getParameter(INVITE_TOKEN_PARAM);
        if (inviteToken == null || inviteToken.isBlank()) return oauthRequest;

        Map<String, Object> additionalParameters = new HashMap<>(oauthRequest.getAdditionalParameters());
        additionalParameters.put(INVITE_TOKEN_PARAM, inviteToken);

        return OAuth2AuthorizationRequest.from(oauthRequest)
                .additionalParameters(additionalParameters)
                .build();
    }
}
