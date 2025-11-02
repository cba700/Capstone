package com.capstone.domain.story.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 2. 결과 생성 요청 DTO (스토리 중간 과정)
@Getter
@Builder
public class StoryNextStepRequestDto {
    // 필수 정보
    private String childName;
    private String childGender;
    private String previousChoice;
    private int currentStep;

    // 추가 정보
    private String friendName;
    private String pet;
    private String personality;

    // 새로운 선택지에 부여할 태그들
    private List<String> newTraitTags;
}
