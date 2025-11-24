package com.capstone.domain.story.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 아이 분석 응답 DTO
@Getter
@Builder
public class ChildAnalysisResponseDto {
    // 성격 분석 (전반적인 성향 요약)
    private String personalityAnalysis;

    // 주요 강점 리스트
    private List<String> strengths;

    // 관심 분야 분석
    private String interestAnalysis;

    // 추천 활동 리스트
    private List<String> recommendedActivities;

    // 추천 직업 분야
    private List<String> recommendedCareerPaths;

    // 종합 소견 (부모님을 위한 조언)
    private String overallAssessment;
}
