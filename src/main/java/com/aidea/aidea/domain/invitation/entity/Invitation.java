package com.aidea.aidea.domain.invitation.entity;

import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Invitation {

    @Id
    private String id;

    @Column(nullable = false)
    private String teamspaceId;

    @Column(nullable = false)
    private Long inviterUserId;

    private Long resourceId;

    @Column(nullable = false, length = 255)
    private String inviteeEmail;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @Builder
    public Invitation(String teamspaceId, String inviteeEmail, Long inviterId, MemberRole role) {
        this.id = UUID.randomUUID().toString();
        this.token = UUID.randomUUID().toString();
        this.teamspaceId = teamspaceId;
        this.inviteeEmail = inviteeEmail;
        this.inviterUserId = inviterId;
        this.role = role;
        this.status = InvitationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusHours(48);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
    }
}
