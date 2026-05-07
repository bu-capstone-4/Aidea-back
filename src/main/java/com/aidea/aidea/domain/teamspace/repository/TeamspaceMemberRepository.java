package com.aidea.aidea.domain.teamspace.repository;

import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamspaceMemberRepository extends JpaRepository<TeamspaceMember, Long> {
    Optional<TeamspaceMember> findByTeamspaceIdAndUserId(String teamspaceId, Long userId);
}
