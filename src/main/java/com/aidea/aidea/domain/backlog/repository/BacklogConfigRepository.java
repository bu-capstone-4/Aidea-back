package com.aidea.aidea.domain.backlog.repository;

import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacklogConfigRepository extends JpaRepository<BacklogConfig, String> {
}
