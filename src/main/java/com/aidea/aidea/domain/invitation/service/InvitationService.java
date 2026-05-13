package com.aidea.aidea.domain.invitation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aidea.aidea.domain.invitation.entity.Invitation;
import com.aidea.aidea.domain.invitation.entity.InvitationStatus;
import com.aidea.aidea.domain.invitation.repository.InvitationRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MailService mailService;

    @Value("${app.base-url}")
    private String baseUrl;

    // 초대 발송
    public void sendInvitation(Long inviterId, Long resourceId, String inviteeEmail) {
        // 중복 초대 방지
        invitationRepository
            .findByInviteeEmailAndResourceIdAndStatus(inviteeEmail, resourceId, InvitationStatus.PENDING)
            .ifPresent(i -> { throw new IllegalStateException("이미 초대가 진행 중입니다."); });

        Invitation invitation = Invitation.builder()
            .inviteeEmail(inviteeEmail)
            .inviterId(inviterId)
            .resourceId(resourceId)
            .build();

        invitationRepository.save(invitation);

        String inviteLink = baseUrl + "/invitations/accept?token=" + invitation.getToken();
        mailService.sendInvitationMail(inviteeEmail, inviteLink);
    }

    // 초대 수락
    public void acceptInvitation(String token, Long userId) {
        Invitation invitation = invitationRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 링크입니다."));

        if (invitation.isExpired()) {
            throw new IllegalStateException("만료된 초대 링크입니다.");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 초대입니다.");
        }

        invitation.accept();
        // TODO: 실제 리소스에 유저 추가하는 로직 (ex. 프로젝트 멤버 추가)
    }
}