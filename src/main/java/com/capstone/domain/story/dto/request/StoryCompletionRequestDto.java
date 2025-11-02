package com.capstone.domain.story.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 3. 이야기 완결 및 직업 추천 요청 DTO
@Getter
@Builder
public class StoryCompletionRequestDto {
    // 필수 정보
    private String childName;
    private String childGender;
    private String lastChoice;

    // 추가 정보
    private String friendName;
    private String pet;
    private String personality;

    // 서버 분석 결과
    private List<String> recommendedJobs; // [1순위 직업명, 2순위 직업명, 3순위 직업명]
}
