package com.capstone.domain.child.service;

import com.capstone.domain.child.dto.*;
import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.repository.ChildRepository;
import com.capstone.domain.story.dto.StoryBookSummaryDto;
import com.capstone.domain.story.dto.request.ChildAnalysisRequestDto;
import com.capstone.domain.story.dto.response.ChildAnalysisResponseDto;
import com.capstone.domain.story.entity.*;
import com.capstone.domain.story.repository.*;
import com.capstone.domain.story.service.StoryGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DashboardService {

    private final ChildRepository childRepository;
    private final StoryRepository storyRepository;
    private final StoryPageRepository storyPageRepository;
    private final StorySelectLogRepository storySelectLogRepository;
    private final StoryChoiceRepository storyChoiceRepository;
    private final StoryGenerator storyGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChildDashboardDto getDashboard(Long childId) {
        Child child = childRepository.findById(childId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid child Id: " + childId));

        // 완성된 스토리 조회
        List<Story> completedStories = storyRepository.findByChildIdAndStatusInOrderByCompletedAtDesc(
            childId, List.of(StoryStatus.COMPLETED));

        // 기본 대시보드 DTO 생성
        ChildDashboardDto.ChildDashboardDtoBuilder builder = ChildDashboardDto.builder()
            .childId(child.getId())
            .childName(child.getName())
            .childAge(child.getAge())
            .childGender(child.getGender().name())
            .completedStoryCount(completedStories.size());

        // 성향 태그 통계 집계
        Map<String, Integer> traitCounts = aggregateTraitCounts(completedStories);
        List<TraitStatDto> topTraits = buildTopTraits(traitCounts, 10);

        // 직업 통계 집계
        List<JobStatDto> jobs = aggregateJobs(completedStories);

        // 테마 통계 집계
        List<ThemeStatDto> themes = aggregateThemes(completedStories);

        // 최근 스토리 목록 (최대 5개)
        List<StoryBookSummaryDto> recentStories = completedStories.stream()
            .limit(5)
            .map(story -> {
                String representativeImage = getRepresentativeImage(story);
                String jobName = story.getSelectedJob() != null ? story.getSelectedJob().getName() : null;

                return StoryBookSummaryDto.builder()
                    .storyId(story.getId())
                    .title(story.getTitle() != null ? story.getTitle() : "제목 없음")
                    .summary(story.getSummary() != null ? story.getSummary() : "")
                    .imageUrl(representativeImage)
                    .jobName(jobName)
                    .completedAt(story.getCompletedAt())
                    .build();
            })
            .collect(Collectors.toList());

        return builder
            .topTraits(topTraits)
            .selectedJobs(jobs)
            .themes(themes)
            .recentStories(recentStories)
            .build();
    }

    public ChildAnalysisResponseDto analyzeChild(Long childId) {
        Child child = childRepository.findById(childId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid child Id: " + childId));

        List<Story> completedStories = storyRepository.findByChildIdAndStatusInOrderByCompletedAtDesc(
            childId, List.of(StoryStatus.COMPLETED));

        // 성향 태그 집계
        Map<String, Integer> traitCounts = aggregateTraitCounts(completedStories);

        // 직업 리스트
        List<String> selectedJobs = completedStories.stream()
            .filter(story -> story.getSelectedJob() != null)
            .map(story -> story.getSelectedJob().getName())
            .collect(Collectors.toList());

        // 테마 리스트
        List<String> themes = completedStories.stream()
            .map(story -> story.getTheme().getName())
            .distinct()
            .collect(Collectors.toList());

        // AI 분석 요청
        ChildAnalysisRequestDto request = ChildAnalysisRequestDto.builder()
            .childName(child.getName())
            .childAge(child.getAge())
            .childGender(child.getGender().name())
            .traitCounts(traitCounts)
            .selectedJobs(selectedJobs)
            .themes(themes)
            .completedStoryCount(completedStories.size())
            .build();

        return storyGenerator.analyzeChild(request);
    }

    private Map<String, Integer> aggregateTraitCounts(List<Story> stories) {
        Map<String, Integer> traitCounts = new HashMap<>();

        for (Story story : stories) {
            List<StorySelectLog> logs = storySelectLogRepository.findByStoryOrderByStepAsc(story);
            for (StorySelectLog log : logs) {
                List<String> traits = parseTraits(log.getChoice().getTraitsJson());
                for (String trait : traits) {
                    traitCounts.put(trait, traitCounts.getOrDefault(trait, 0) + 1);
                }
            }
        }

        return traitCounts;
    }

    private List<TraitStatDto> buildTopTraits(Map<String, Integer> traitCounts, int limit) {
        List<Map.Entry<String, Integer>> sorted = traitCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toList());

        List<TraitStatDto> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : sorted) {
            result.add(TraitStatDto.builder()
                .traitName(entry.getKey())
                .count(entry.getValue())
                .rank(rank++)
                .build());
        }
        return result;
    }

    private List<JobStatDto> aggregateJobs(List<Story> stories) {
        Map<String, Integer> jobCounts = new HashMap<>();

        for (Story story : stories) {
            if (story.getSelectedJob() != null) {
                String jobName = story.getSelectedJob().getName();
                jobCounts.put(jobName, jobCounts.getOrDefault(jobName, 0) + 1);
            }
        }

        return jobCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(entry -> JobStatDto.builder()
                .jobName(entry.getKey())
                .count(entry.getValue())
                .build())
            .collect(Collectors.toList());
    }

    private List<ThemeStatDto> aggregateThemes(List<Story> stories) {
        Map<String, Integer> themeCounts = new HashMap<>();

        for (Story story : stories) {
            String themeName = story.getTheme().getName();
            themeCounts.put(themeName, themeCounts.getOrDefault(themeName, 0) + 1);
        }

        return themeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(entry -> ThemeStatDto.builder()
                .themeName(entry.getKey())
                .count(entry.getValue())
                .build())
            .collect(Collectors.toList());
    }

    private List<String> parseTraits(String traitsJson) {
        if (!StringUtils.hasText(traitsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(traitsJson, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.warn("Failed to parse traits JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getRepresentativeImage(Story story) {
        List<StoryPage> pages = storyPageRepository.findByStoryOrderByStepAsc(story);
        if (pages.isEmpty()) {
            return null;
        }

        // 중간 페이지의 이미지 우선 (직업 선택/체험 화면)
        int midIndex = pages.size() / 2;
        for (int i = midIndex; i < pages.size(); i++) {
            if (StringUtils.hasText(pages.get(i).getImageUrl())) {
                return pages.get(i).getImageUrl();
            }
        }

        // 중간 이후에 없으면 처음부터 찾기
        for (StoryPage page : pages) {
            if (StringUtils.hasText(page.getImageUrl())) {
                return page.getImageUrl();
            }
        }

        return null;
    }
}
