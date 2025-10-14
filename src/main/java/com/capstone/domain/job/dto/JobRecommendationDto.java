package com.capstone.domain.job.dto;

import com.capstone.domain.job.entity.JobRecommendation;
import lombok.Builder;

@Builder
public record JobRecommendationDto(
        Long recommendationId,
        String jobName,
        String themeWorld
) {
    public static JobRecommendationDto from(JobRecommendation entity) {
        return JobRecommendationDto.builder()
                .recommendationId(entity.getId())
                .jobName(entity.getJob().getName())
                .themeWorld(entity.getThemeWorld())
                .build();
    }
}
