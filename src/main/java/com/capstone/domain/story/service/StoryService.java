package com.capstone.domain.story.service;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.repository.ChildRepository;
import com.capstone.domain.job.entity.Job;
import com.capstone.domain.job.repository.JobRepository;
import com.capstone.domain.story.dto.ChoiceResponseDto;
import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.dto.request.JobExperienceStartRequestDto;
import com.capstone.domain.story.dto.request.StoryCompletionRequestDto;
import com.capstone.domain.story.dto.request.StoryNextStepRequestDto;
import com.capstone.domain.story.dto.request.StoryStartRequestDto;
import com.capstone.domain.story.dto.request.TitleGenerationRequestDto;
import com.capstone.domain.story.dto.response.AiResponseDto;
import com.capstone.domain.story.dto.response.TitleResponseDto;
import com.capstone.domain.story.entity.*;
import com.capstone.domain.story.repository.StoryChoiceRepository;
import com.capstone.domain.story.repository.StoryPageRepository;
import com.capstone.domain.story.repository.StoryRepository;
import com.capstone.domain.story.repository.StorySelectLogRepository;
import com.capstone.domain.theme.entity.Theme;
import com.capstone.domain.theme.repository.ThemeRepository;
import com.capstone.domain.trait.entity.Trait;
import com.capstone.domain.trait.entity.TraitJob;
import com.capstone.domain.trait.repository.TraitJobRepository;
import com.capstone.domain.trait.repository.TraitRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StoryService {

	private static final int STORY_CHOICE_LIMIT = 3;
	private static final List<String> JOB_FALLBACKS = List.of("상상력 탐험가", "친절한 도우미", "용감한 탐험가");

	private final StoryRepository storyRepository;
	private final StoryPageRepository storyPageRepository;
	private final StoryChoiceRepository storyChoiceRepository;
	private final StorySelectLogRepository storySelectLogRepository;
	private final ThemeRepository themeRepository;
	private final ChildRepository childRepository;
	private final TraitRepository traitRepository;
	private final TraitJobRepository traitJobRepository;
	private final JobRepository jobRepository;
	private final StoryGenerator storyGenerator;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public Story createStory(Long childId, Long themeId) {
		Child child = childRepository.findById(childId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid child Id:" + childId));
		Theme theme = themeRepository.findById(themeId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid theme Id:" + themeId));

		StoryStartRequestDto requestDto = StoryStartRequestDto.builder()
			.childName(child.getName())
			.childAge(child.getAge())
			.childGender(child.getGender().toString())
			.interests(Collections.singletonList(theme.getName()))
			.build();

		AiResponseDto aiResponse = storyGenerator.generateStoryStart(requestDto);

		Story story = Story.builder().
			child(child).
			theme(theme).
			status(StoryStatus.IN_PROGRESS).
			currentStep(0).
			build();

		Story savedStory = storyRepository.save(story);

		saveSceneFromAiResponse(savedStory, aiResponse);
		return savedStory;
	}

	public int makeChoice(Long storyId, Long choiceId) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
		StoryChoice choice = storyChoiceRepository.findById(choiceId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid choice Id:" + choiceId));

		storySelectLogRepository.save(StorySelectLog.builder()
			.story(story)
			.page(choice.getPage())
			.choice(choice)
			.step(story.getCurrentStep())
			.build());

		long choiceCount = storySelectLogRepository.countByStory(story);

		if (choiceCount < STORY_CHOICE_LIMIT) {
			StoryNextStepRequestDto requestDto = StoryNextStepRequestDto.builder()
				.childName(story.getChild().getName()).childGender(story.getChild().getGender().toString())
				.previousChoice(choice.getLabel()).currentStep(Math.min((int)choiceCount + 1, STORY_CHOICE_LIMIT)).build();
			AiResponseDto aiResponse = storyGenerator.generateNextStep(requestDto);
			return saveSceneFromAiResponse(story, aiResponse);
		} else {
			List<String> recommendedJobs = recommendJobsBasedOnTraits(story);
			for (int i = recommendedJobs.size(); i < 3; i++) {
				recommendedJobs.add(JOB_FALLBACKS.get(i % JOB_FALLBACKS.size()));
			}
			StoryCompletionRequestDto requestDto = StoryCompletionRequestDto.builder()
				.childName(story.getChild().getName()).childGender(story.getChild().getGender().toString())
				.lastChoice(choice.getLabel()).recommendedJobs(recommendedJobs).build();
			AiResponseDto aiResponse = storyGenerator.generateStoryCompletion(requestDto);
			story.updateStatus(StoryStatus.RECOMMENDED);
			return saveSceneFromAiResponse(story, aiResponse);
		}
	}

	public Story startJobStory(Long previousStoryId, String jobName) {
		Story previousStory = storyRepository.findById(previousStoryId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + previousStoryId));
		Child child = previousStory.getChild();
		Job job = jobRepository.findByName(jobName)
			.orElseThrow(() -> new IllegalArgumentException("Invalid job name: " + jobName));

		String coreTrait = findCoreTrait(previousStory);

		JobExperienceStartRequestDto requestDto = JobExperienceStartRequestDto.builder()
			.childName(child.getName()).childGender(child.getGender().toString())
			.selectedJob(job.getName()).themeWorld(job.getName() + " 나라") // 테마월드 임시 생성
			.coreTrait(coreTrait).build();

		AiResponseDto aiResponse = storyGenerator.generateJobExperienceStart(requestDto);

		Story jobStory = Story.builder().child(child).theme(previousStory.getTheme()).status(StoryStatus.JOB_STARTED)
			.currentStep(0).selectedJob(job).build();
		Story savedJobStory = storyRepository.save(jobStory);

		saveSceneFromAiResponse(savedJobStory, aiResponse);
		return savedJobStory;
	}

	private String findCoreTrait(Story story) {
		Map<String, Integer> traitCounts = countTraitsInStory(story);
		return traitCounts.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(null);
	}

	private List<String> recommendJobsBasedOnTraits(Story story) {
		Map<String, Integer> traitCounts = countTraitsInStory(story);
		List<String> topTraits = traitCounts.entrySet().stream()
			.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.limit(2).map(Map.Entry::getKey).collect(Collectors.toList());

		List<Trait> traitEntities = traitRepository.findByTagIn(topTraits);
		List<TraitJob> traitJobs = traitJobRepository.findByTraitIn(traitEntities);

		Map<Job, Double> jobScores = new HashMap<>();
		for (TraitJob traitJob : traitJobs) {
			String traitTag = traitJob.getTrait().getTag();
			double score = traitCounts.getOrDefault(traitTag, 0) * traitJob.getWeight();
			jobScores.put(traitJob.getJob(), jobScores.getOrDefault(traitJob.getJob(), 0.0) + score);
		}

		return jobScores.entrySet().stream()
			.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.limit(3).map(entry -> entry.getKey().getName()).collect(Collectors.toList());
	}

	private Map<String, Integer> countTraitsInStory(Story story) {
		List<StorySelectLog> logs = storySelectLogRepository.findByStoryOrderByStepAsc(story);
		Map<String, Integer> traitCounts = new HashMap<>();
		for (StorySelectLog log : logs) {
			String traitsJson = log.getChoice().getTraitsJson();
			if (StringUtils.hasText(traitsJson)) {
				try {
					List<String> traits = objectMapper.readValue(traitsJson, new TypeReference<>() {
					});
					for (String trait : traits) {
						traitCounts.put(trait, traitCounts.getOrDefault(trait, 0) + 1);
					}
				} catch (IOException e) {
					throw new RuntimeException("Failed to deserialize traits from JSON", e);
				}
			}
		}
		return traitCounts;
	}

	private int saveSceneFromAiResponse(Story story, AiResponseDto aiResponse) {
		List<String> narrationSections = aiResponse.getNarrationSectionsOrDefault().stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.collect(Collectors.toList());

		int firstNewStep = story.getCurrentStep() + 1;
		int currentPageStep = firstNewStep;

		for (String section : narrationSections) {
			storyPageRepository.save(StoryPage.builder()
				.story(story)
				.step(currentPageStep++)
				.pageType(PageType.PROGRESS)
				.narration(section)
				.hasChoice(false)
				.build());
		}

		List<AiResponseDto.ChoiceDto> aiChoices = aiResponse.getChoices();
		boolean hasChoices = aiChoices != null && !aiChoices.isEmpty();
		String problemNarration = StringUtils.hasText(aiResponse.getProblem()) ? aiResponse.getProblem().trim() : "";

		StoryPage choicePage = storyPageRepository.save(StoryPage.builder()
			.story(story)
			.step(currentPageStep)
			.pageType(hasChoices ? PageType.CHOICE : PageType.PROGRESS)
			.narration(problemNarration)
			.hasChoice(hasChoices)
			.build());

		for (int i = 0; hasChoices && i < aiChoices.size(); i++) {
			AiResponseDto.ChoiceDto choiceDto = aiChoices.get(i);
			try {
				String traitsJson = objectMapper.writeValueAsString(choiceDto.getTraitsOrDefault());
				StoryChoice.ChoiceKey choiceKey = resolveChoiceKey(i, choiceDto.getChoiceKey());
				storyChoiceRepository.save(StoryChoice.builder()
					.page(choicePage)
					.choiceKey(choiceKey)
					.label(choiceDto.getChoiceText())
					.traitsJson(traitsJson)
					.build());
			} catch (JsonProcessingException e) {
				throw new RuntimeException("Failed to serialize traits to JSON", e);
			}
		}
		story.updateCurrentStep(currentPageStep);
		return firstNewStep;
	}

	private StoryChoice.ChoiceKey resolveChoiceKey(int index, String rawKey) {
		if (StringUtils.hasText(rawKey)) {
			try {
				return StoryChoice.ChoiceKey.valueOf(rawKey.trim().toUpperCase());
			} catch (IllegalArgumentException ignored) {
				// fall back to positional mapping below
			}
		}
		StoryChoice.ChoiceKey[] values = StoryChoice.ChoiceKey.values();
		return values[Math.min(index, values.length - 1)];
	}

	@Transactional(readOnly = true)
	public StoryPageResponseDto getPage(Long storyId, Integer step) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
		StoryPage page = storyPageRepository.findByStoryAndStep(story, step)
			.orElseThrow(() -> new IllegalArgumentException("Invalid step:" + step));
		List<ChoiceResponseDto> choices = storyChoiceRepository.findByPage(page)
			.stream()
			.map(choice -> ChoiceResponseDto.builder().choiceId(choice.getId()).text(choice.getLabel()).build())
			.collect(Collectors.toList());
		return StoryPageResponseDto.from(page, choices);
	}
}
