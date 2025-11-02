package com.capstone.domain.story.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TitleResponseDto {
    private String title; // AI가 선정한 최종 제목
    private String reason; // 선정한 이유
}
