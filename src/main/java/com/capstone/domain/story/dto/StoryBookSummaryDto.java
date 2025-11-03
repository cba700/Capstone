package com.capstone.domain.story.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record StoryBookSummaryDto(
        Long storyId,
        String title,
        String summary,
        LocalDateTime completedAt
) {
}
