package com.capstone.domain.story.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

// 아이 분석 요청 DTO
@Getter
@Builder
public class ChildAnalysisRequestDto {
    // 아이 기본 정보
    private String childName;
    private int childAge;
    private String childGender;

    // 성향 태그 통계 (태그명 -> 선택 횟수)
    private Map<String, Integer> traitCounts;

    // 선택한 직업 리스트 (중복 포함, 선택 순서대로)
    private List<String> selectedJobs;

    // 관심사(테마) 리스트
    private List<String> themes;

    // 완성한 스토리 개수
    private int completedStoryCount;
}
