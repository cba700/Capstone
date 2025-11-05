package com.capstone.domain.story.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

// 1. 이야기 시작 요청 DTO
@Getter
@Builder
public class StoryStartRequestDto {
    // 필수 정보
    private String childName;
    private int childAge;
    private String childGender;
    private List<String> interests;

    // 추가 정보
    private String friendName; // Optional
    private String pet;        // Optional
    private String personality; // Optional

    private List<List<String>> choiceTraitCandidates;

    public List<List<String>> getChoiceTraitCandidatesOrDefault() {
        return choiceTraitCandidates != null ? choiceTraitCandidates : Collections.emptyList();
    }
}
