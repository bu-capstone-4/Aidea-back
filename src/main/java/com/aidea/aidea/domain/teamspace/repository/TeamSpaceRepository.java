package com.aidea.aidea.domain.teamspace.repository;

import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamSpaceRepository extends JpaRepository<TeamSpace, String> {
}