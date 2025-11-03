package com.capstone.domain.story.dto;

import lombok.Builder;

@Builder
public record StoryBookDetailDto(
        Long storyId,
        String title,
        String summary,
        String contentHtml
) {
}
