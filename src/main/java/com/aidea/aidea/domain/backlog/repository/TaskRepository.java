package com.aidea.aidea.domain.backlog.repository;

import com.aidea.aidea.domain.backlog.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStoryIdOrderByPositionAsc(Long storyId);

    List<Task> findByStoryIdAndIdIn(Long storyId, List<Long> ids);
}
