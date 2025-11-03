package com.capstone.domain.story.dto;

import lombok.Builder;

@Builder
public record ChoiceResponseDto(
        Long choiceId,
        String text,
        String jobName
) {
}
