package com.capstone.domain.story.repository;

import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.entity.StorySelectLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorySelectLogRepository extends JpaRepository<StorySelectLog, Long> {
    List<StorySelectLog> findByStoryOrderByStepAsc(Story story);
}
