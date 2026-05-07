package com.aidea.aidea.domain.invitation.repository;

import com.aidea.aidea.domain.invitation.entity.Invitation;
import com.aidea.aidea.domain.invitation.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, String> {

    Optional<Invitation> findByToken(String token);

    Optional<Invitation> findByTeamspaceIdAndInviteeEmailAndStatus(
            String teamspaceId, String inviteeEmail, InvitationStatus status);

    List<Invitation> findByTeamspaceIdAndStatus(String teamspaceId, InvitationStatus status);
}
