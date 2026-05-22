package com.aidea.aidea.domain.backlog.repository;

import com.aidea.aidea.domain.backlog.entity.Epic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EpicRepository extends JpaRepository<Epic, Long> {

    List<Epic> findByTeamspaceIdOrderByCreatedAtAsc(String teamspaceId);

    @Query("SELECT e FROM Epic e JOIN FETCH e.createdBy WHERE e.teamspaceId = :teamspaceId ORDER BY e.createdAt ASC")
    List<Epic> findAllWithCreatorByTeamspaceId(@Param("teamspaceId") String teamspaceId);
}
