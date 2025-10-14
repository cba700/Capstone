package com.capstone.domain.job.repository;

import com.capstone.domain.job.entity.JobRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, Long> {
}
