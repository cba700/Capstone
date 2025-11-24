package com.capstone.domain.story.service;

import com.capstone.domain.story.dto.request.*;
import com.capstone.domain.story.dto.response.AiResponseDto;
import com.capstone.domain.story.dto.response.ChildAnalysisResponseDto;
import com.capstone.domain.story.dto.response.TitleResponseDto;

// v5.0 명세에 따른 AI 역할 추상화 인터페이스
public interface StoryGenerator {
    // 1. 이야기 시작 요청
    AiResponseDto generateStoryStart(StoryStartRequestDto dto);

    // 2. 결과 생성 요청 (중간 과정)
    AiResponseDto generateNextStep(StoryNextStepRequestDto dto);

    // 3. 이야기 완결 및 직업 추천 요청
    AiResponseDto generateStoryCompletion(StoryCompletionRequestDto dto);

    // 4. 직업 체험 시작 요청
    AiResponseDto generateJobExperienceStart(JobExperienceStartRequestDto dto);

    // 5. 스토리북 제목 생성 요청
    TitleResponseDto generateStorybookTitle(TitleGenerationRequestDto dto);

    // 6. 아이 성향 종합 분석 요청
    ChildAnalysisResponseDto analyzeChild(ChildAnalysisRequestDto dto);
}