package com.capstone.domain.job.repository;

import com.capstone.domain.job.entity.JobRecommendation;
import com.capstone.domain.story.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, Long> {
    List<JobRecommendation> findByStory(Story story);
}
