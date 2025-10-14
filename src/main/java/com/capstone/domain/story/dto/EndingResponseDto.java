package com.capstone.domain.story.dto;

import com.capstone.domain.job.dto.JobRecommendationDto;
import lombok.Builder;

import java.util.List;

@Builder
public record EndingResponseDto(
        String narration,
        List<JobRecommendationDto> recommendations
) {
}
