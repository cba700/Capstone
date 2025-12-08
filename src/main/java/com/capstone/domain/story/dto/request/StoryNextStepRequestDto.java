package com.capstone.domain.story.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
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
    private List<String> interests;

    // 새로운 선택지에 부여할 태그 후보 (선택지 수 만큼 각 3개씩 제공)
    private List<List<String>> choiceTraitCandidates;

    public List<List<String>> getChoiceTraitCandidatesOrDefault() {
        return choiceTraitCandidates != null ? choiceTraitCandidates : Collections.emptyList();
    }
}
