package com.capstone.domain.story.dto;

import com.capstone.domain.story.entity.StoryPage;
import com.capstone.domain.story.entity.StoryStatus;
import lombok.Builder;
import org.springframework.util.StringUtils;

import java.util.List;

@Builder
public record StoryPageResponseDto(
        Long storyId,
        Integer step,
        String themeName,
        String narration,
        String imageUrl,
        boolean hasChoice,
        List<ChoiceResponseDto> choices,
        String storyTitle,
        boolean completion,
        boolean hasNext
) {
    public static StoryPageResponseDto from(StoryPage page, List<ChoiceResponseDto> choices) {
        return StoryPageResponseDto.builder()
                .storyId(page.getStory().getId())
                .step(page.getStep())
                .themeName(page.getStory().getTheme().getName())
                .narration(page.getNarration())
                .imageUrl(page.getImageUrl())
                .hasChoice(page.getHasChoice())
                .choices(choices)
                .storyTitle(StringUtils.hasText(page.getStory().getTitle()) ? page.getStory().getTitle() : null)
                .completion(!page.getHasChoice() && page.getStory().getStatus() == StoryStatus.COMPLETED)
                .hasNext(false) // 기본값, 컨트롤러에서 별도 설정
                .build();
    }
    
    public static StoryPageResponseDto fromWithNext(StoryPage page, List<ChoiceResponseDto> choices, boolean hasNext) {
        return StoryPageResponseDto.builder()
                .storyId(page.getStory().getId())
                .step(page.getStep())
                .themeName(page.getStory().getTheme().getName())
                .narration(page.getNarration())
                .imageUrl(page.getImageUrl())
                .hasChoice(page.getHasChoice())
                .choices(choices)
                .storyTitle(StringUtils.hasText(page.getStory().getTitle()) ? page.getStory().getTitle() : null)
                .completion(!page.getHasChoice() && page.getStory().getStatus() == StoryStatus.COMPLETED)
                .hasNext(hasNext)
                .build();
    }
}
