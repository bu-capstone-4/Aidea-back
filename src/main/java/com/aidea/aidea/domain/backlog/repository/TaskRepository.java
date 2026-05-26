package com.aidea.aidea.domain.backlog.repository;

import com.aidea.aidea.domain.backlog.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStoryIdOrderByPositionAsc(Long storyId);

    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.assignee WHERE t.story.id = :storyId ORDER BY t.position ASC")
    List<Task> findWithAssigneeByStoryId(@Param("storyId") Long storyId);

    List<Task> findByStoryIdAndIdIn(Long storyId, List<Long> ids);

    @Query("""
        SELECT t.story.id, COUNT(t), SUM(CASE WHEN t.isCompleted = true THEN 1 ELSE 0 END)
        FROM Task t
        WHERE t.story.teamspaceId = :teamspaceId
        GROUP BY t.story.id
    """)
    List<Object[]> findTaskCountsByTeamspaceId(@Param("teamspaceId") String teamspaceId);
}
