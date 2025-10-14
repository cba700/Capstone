package com.capstone.domain.story.service;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.repository.ChildRepository;
import com.capstone.domain.job.dto.JobRecommendationDto;
import com.capstone.domain.job.repository.JobRecommendationRepository;
import com.capstone.domain.job.service.JobRecommendationService;
import com.capstone.domain.story.dto.EndingResponseDto;
import com.capstone.domain.story.dto.ChoiceResponseDto;
import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.entity.*;
import com.capstone.domain.story.repository.StoryChoiceRepository;
import com.capstone.domain.story.repository.StoryPageRepository;
import com.capstone.domain.story.repository.StoryRepository;
import com.capstone.domain.story.repository.StorySelectLogRepository;
import com.capstone.domain.theme.entity.Theme;
import com.capstone.domain.theme.repository.ThemeRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StoryService {

    private final StoryRepository storyRepository;
    private final StoryPageRepository storyPageRepository;
    private final StoryChoiceRepository storyChoiceRepository;
    private final StorySelectLogRepository storySelectLogRepository;
    private final ThemeRepository themeRepository;
    private final ChildRepository childRepository;
    private final JobRecommendationRepository jobRecommendationRepository; // 추가
    private final StoryGenerator storyGenerator;
    private final JobRecommendationService jobRecommendationService; // 추가

    // ... createStory, getPage, makeChoice 메소드 ...

    public Story createStory(Long childId, Long themeId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid child Id:" + childId));
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid theme Id:" + themeId));

        // 1. 스토리 생성
        Story story = Story.builder()
                .child(child)
                .theme(theme)
                .status(StoryStatus.IN_PROGRESS)
                .currentStep(1)
                .build();
        Story savedStory = storyRepository.save(story);

        // 2. AI를 통해 첫 페이지 생성 (현재는 Mock 사용)
        String generatedJson = storyGenerator.generateFirstPage(theme);
        JSONObject pageJson = new JSONObject(generatedJson);

        // 3. 첫 페이지 정보 저장
        StoryPage firstPage = StoryPage.builder()
                .story(savedStory)
                .step(1)
                .narration(pageJson.getString("narration"))
                .hasChoice(true) // 첫 페이지는 항상 선택지가 있다고 가정
                .build();
        storyPageRepository.save(firstPage);

        // 4. 선택지 정보 저장
        JSONArray choicesJson = pageJson.getJSONArray("choices");
        for (int i = 0; i < choicesJson.length(); i++) {
            JSONObject choiceJson = choicesJson.getJSONObject(i);
            StoryChoice choice = StoryChoice.builder()
                    .page(firstPage)
                    .choiceKey(StoryChoice.ChoiceKey.values()[i]) // A, B, C 순서대로 할당
                    .label(choiceJson.getString("text"))
                    .build();
            storyChoiceRepository.save(choice);
        }

        return savedStory;
    }

    @Transactional(readOnly = true)
    public StoryPageResponseDto getPage(Long storyId, Integer step) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
        StoryPage page = storyPageRepository.findByStoryAndStep(story, step)
                .orElseThrow(() -> new IllegalArgumentException("Invalid step:" + step));

        // 해당 페이지의 선택지들을 조회
        List<ChoiceResponseDto> choices = storyChoiceRepository.findByPage(page).stream()
                .map(choice -> ChoiceResponseDto.builder()
                        .choiceId(choice.getId())
                        .text(choice.getLabel())
                        .build())
                .collect(Collectors.toList());

        return StoryPageResponseDto.from(page, choices);
    }

    public int makeChoice(Long storyId, Long choiceId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
        StoryChoice choice = storyChoiceRepository.findById(choiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid choice Id:" + choiceId));

        // 1. 선택 로그 기록
        StorySelectLog log = StorySelectLog.builder()
                .story(story)
                .page(choice.getPage())
                .choice(choice)
                .step(story.getCurrentStep())
                .build();
        storySelectLogRepository.save(log);

        // 2. 스토리 현재 단계 업데이트
        story.advanceStep();
        storyRepository.save(story);

        // 3. AI를 통해 다음 페이지 생성 (현재는 Mock 사용)
        // List<StorySelectLog> history = storySelectLogRepository.findByStoryOrderByStepAsc(story);
        // String generatedJson = storyGenerator.generateNextPage(history);
        String generatedJson = storyGenerator.generateNextPage(null); // Mock이므로 임시로 null 전달
        JSONObject pageJson = new JSONObject(generatedJson);

        // 4. 다음 페이지 정보 저장
        StoryPage nextPage = StoryPage.builder()
                .story(story)
                .step(story.getCurrentStep())
                .narration(pageJson.getString("narration"))
                .hasChoice(true)
                .build();
        storyPageRepository.save(nextPage);

        // 5. 다음 페이지의 선택지 정보 저장
        JSONArray choicesJson = pageJson.getJSONArray("choices");
        for (int i = 0; i < choicesJson.length(); i++) {
            JSONObject choiceJson = choicesJson.getJSONObject(i);
            StoryChoice nextChoice = StoryChoice.builder()
                    .page(nextPage)
                    .choiceKey(StoryChoice.ChoiceKey.values()[i])
                    .label(choiceJson.getString("text"))
                    .build();
            storyChoiceRepository.save(nextChoice);
        }

        return story.getCurrentStep();
    }

    public void completeStory(Long storyId, Long choiceId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
        StoryChoice choice = storyChoiceRepository.findById(choiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid choice Id:" + choiceId));

        // 1. 마지막 선택 로그 기록
        StorySelectLog log = StorySelectLog.builder()
                .story(story)
                .page(choice.getPage())
                .choice(choice)
                .step(story.getCurrentStep())
                .build();
        storySelectLogRepository.save(log);

        // 2. 스토리 상태 변경
        story.updateStatus(StoryStatus.RECOMMENDED);
        storyRepository.save(story);

        // 3. 직업 추천 생성 로직 호출
        jobRecommendationService.generateRecommendations(story);
    }

    @Transactional(readOnly = true)
    public EndingResponseDto getEnding(Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));

        // AI를 통해 결말 나레이션 생성
        List<StorySelectLog> history = storySelectLogRepository.findByStoryOrderByStepAsc(story);
        String narrationJson = storyGenerator.generateEnding(history);
        JSONObject jsonObject = new JSONObject(narrationJson);
        String narration = jsonObject.getString("narration");

        // 저장된 직업 추천 목록 조회
        List<JobRecommendationDto> recommendations = jobRecommendationRepository.findByStory(story).stream()
                .map(JobRecommendationDto::from)
                .collect(Collectors.toList());

        return EndingResponseDto.builder()
                .narration(narration)
                .recommendations(recommendations)
                .build();
    }
}
