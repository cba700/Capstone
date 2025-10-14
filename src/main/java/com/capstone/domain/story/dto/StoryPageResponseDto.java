package com.capstone.domain.story.dto;

import com.capstone.domain.story.entity.StoryPage;
import lombok.Builder;

import java.util.List;

@Builder
public record StoryPageResponseDto(
        Long storyId,
        Integer step,
        String narration,
        List<ChoiceResponseDto> choices
) {
    public static StoryPageResponseDto from(StoryPage page, List<ChoiceResponseDto> choices) {
        return StoryPageResponseDto.builder()
                .storyId(page.getStory().getId())
                .step(page.getStep())
                .narration(page.getNarration())
                .choices(choices)
                .build();
    }
}
