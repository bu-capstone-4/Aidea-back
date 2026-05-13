package com.aidea.aidea.domain.teamspace.repository;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamSpaceRepository extends JpaRepository<TeamSpace, String> {

    List<TeamSpace> findByOwner(User owner);
}