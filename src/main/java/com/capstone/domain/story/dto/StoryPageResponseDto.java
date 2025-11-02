package com.capstone.domain.story.dto;

import com.capstone.domain.story.entity.StoryPage;
import lombok.Builder;

import java.util.List;

@Builder
public record StoryPageResponseDto(
        Long storyId,
        Integer step,
        String themeName, // 테마 이름 필드 추가
        String narration,
        boolean hasChoice, // 선택지 유무 필드 추가
        List<ChoiceResponseDto> choices
) {
    public static StoryPageResponseDto from(StoryPage page, List<ChoiceResponseDto> choices) {
        return StoryPageResponseDto.builder()
                .storyId(page.getStory().getId())
                .step(page.getStep())
                .themeName(page.getStory().getTheme().getName())
                .narration(page.getNarration())
                .hasChoice(page.getHasChoice())
                .choices(choices)
                .build();
    }
}
