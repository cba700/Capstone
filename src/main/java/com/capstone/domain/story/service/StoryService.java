package com.capstone.domain.story.service;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.repository.ChildRepository;
import com.capstone.domain.story.dto.ChoiceResponseDto;
import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.entity.StoryPage;
import com.capstone.domain.story.entity.StoryStatus;
import com.capstone.domain.story.repository.StoryChoiceRepository;
import com.capstone.domain.story.repository.StoryPageRepository;
import com.capstone.domain.story.repository.StoryRepository;
import com.capstone.domain.theme.entity.Theme;
import com.capstone.domain.theme.repository.ThemeRepository;
import lombok.RequiredArgsConstructor;
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
    private final StoryChoiceRepository storyChoiceRepository; // 추가
    private final ThemeRepository themeRepository;
    private final ChildRepository childRepository;
    private final StoryGenerator storyGenerator; // Mock 또는 실제 AI 구현체가 주입됨

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

        // TODO: 선택지(StoryChoice) 정보도 파싱해서 저장하는 로직 추가 필요

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
}
