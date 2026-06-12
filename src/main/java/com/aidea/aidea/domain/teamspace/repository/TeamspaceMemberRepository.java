package com.aidea.aidea.domain.teamspace.repository;

import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamspaceMemberRepository extends JpaRepository<TeamspaceMember, Long> {
    Optional<TeamspaceMember> findByTeamspaceIdAndUserId(String teamspaceId, Long userId);
    List<TeamspaceMember> findByUserId(Long userId);
    void deleteAllByTeamspaceId(String teamspaceId);
    List<TeamspaceMember> findByTeamspaceId(String teamspaceId);
    long countByTeamspaceIdAndRole(String teamspaceId, MemberRole role);
}
