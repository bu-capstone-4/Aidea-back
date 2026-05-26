package com.aidea.aidea.domain.backlog.repository;

import com.aidea.aidea.domain.backlog.entity.Epic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EpicRepository extends JpaRepository<Epic, Long> {

    @Query("SELECT e FROM Epic e JOIN FETCH e.createdBy LEFT JOIN FETCH e.assignee WHERE e.teamspaceId = :teamspaceId ORDER BY e.position ASC, e.createdAt ASC")
    List<Epic> findAllWithCreatorByTeamspaceId(@Param("teamspaceId") String teamspaceId);

    @Query("SELECT COALESCE(MAX(e.number), 0) FROM Epic e WHERE e.teamspaceId = :teamspaceId")
    Long findMaxNumberByTeamspaceId(@Param("teamspaceId") String teamspaceId);

    List<Epic> findByTeamspaceIdAndIdIn(String teamspaceId, List<Long> ids);

    @Query("""
        SELECT se.epic.id,
               COUNT(se.story.id),
               SUM(CASE WHEN se.story.status IN (
                   com.aidea.aidea.domain.backlog.entity.StoryStatus.DONE,
                   com.aidea.aidea.domain.backlog.entity.StoryStatus.CLOSED
               ) THEN 1 ELSE 0 END)
        FROM StoryEpic se
        WHERE se.epic.teamspaceId = :teamspaceId
        GROUP BY se.epic.id
    """)
    List<Object[]> findStoryCountsByTeamspaceId(@Param("teamspaceId") String teamspaceId);
}
