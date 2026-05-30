package com.aidea.aidea.domain.invitation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendInvitationMail(String to, String inviteLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(to);
        message.setSubject("[AIdea] 초대가 도착했습니다");
        message.setText("아래 링크를 클릭해 초대를 수락하세요.\n\n" + inviteLink + "\n\n링크는 48시간 후 만료됩니다.");
        mailSender.send(message);
    }
}