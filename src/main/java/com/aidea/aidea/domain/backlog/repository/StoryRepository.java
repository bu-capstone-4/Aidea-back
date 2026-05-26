package com.aidea.aidea.domain.backlog.repository;

import com.aidea.aidea.domain.backlog.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    @Query("SELECT COALESCE(MAX(s.number), 0) FROM Story s WHERE s.teamspaceId = :teamspaceId")
    Long findMaxNumberByTeamspaceId(@Param("teamspaceId") String teamspaceId);

    @Query("""
        SELECT DISTINCT s FROM Story s
        LEFT JOIN FETCH s.assignee
        LEFT JOIN FETCH s.reporter
        LEFT JOIN FETCH s.storyEpics se
        LEFT JOIN FETCH se.epic
        WHERE s.teamspaceId = :teamspaceId
        ORDER BY s.position ASC
    """)
    List<Story> findAllWithRelationsByTeamspaceId(@Param("teamspaceId") String teamspaceId);

    @Query("""
        SELECT s FROM Story s
        LEFT JOIN FETCH s.assignee
        LEFT JOIN FETCH s.reporter
        LEFT JOIN FETCH s.storyEpics se
        LEFT JOIN FETCH se.epic
        WHERE s.id = :storyId AND s.teamspaceId = :teamspaceId
    """)
    Optional<Story> findDetailByIdAndTeamspaceId(
        @Param("storyId") Long storyId,
        @Param("teamspaceId") String teamspaceId
    );

    List<Story> findByTeamspaceIdAndIdIn(String teamspaceId, List<Long> ids);
}
