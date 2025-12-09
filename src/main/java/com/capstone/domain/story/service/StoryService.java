package com.capstone.domain.story.service;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.repository.ChildRepository;
import com.capstone.domain.job.entity.Job;
import com.capstone.domain.job.repository.JobRepository;
import com.capstone.domain.story.dto.ChoiceResponseDto;
import com.capstone.domain.story.dto.StoryBookDetailDto;
import com.capstone.domain.story.dto.StoryBookSummaryDto;
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
import com.capstone.domain.story.repository.StoryPageTemplateRepository;
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
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StoryService {

	private static final int STORY_CHOICE_LIMIT = 1;
	private static final List<String> JOB_FALLBACKS = List.of("소방관", "교사", "과학자(실험실 연구원)");
	private static final List<String> DEFAULT_TRAIT_TAGS = List.of("#용기", "#상상력", "#협동심", "#친절", "#탐구심");
	private static final int TRAITS_PER_CHOICE = 3;

	private final StoryRepository storyRepository;
	private final StoryPageRepository storyPageRepository;
	private final StoryPageTemplateRepository storyPageTemplateRepository;
	private final StoryChoiceRepository storyChoiceRepository;
	private final StorySelectLogRepository storySelectLogRepository;
	private final ThemeRepository themeRepository;
	private final ChildRepository childRepository;
	private final TraitRepository traitRepository;
	private final TraitJobRepository traitJobRepository;
	private final JobRepository jobRepository;
	private final StoryGenerator storyGenerator;
	private final StoryImageService storyImageService;
	private final ApplicationContext applicationContext;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ConcurrentHashMap<String, Object> pageGenerationLocks = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Object> choiceProcessingLocks = new ConcurrentHashMap<>();

	public Story createStory(Long childId, Long themeId) {
		Child child = childRepository.findById(childId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid child Id:" + childId));
		Theme theme = themeRepository.findById(themeId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid theme Id:" + themeId));

		StoryStartRequestDto requestDto = StoryStartRequestDto.builder()
			.childName(child.getName())
			.childAge(child.getAge())
			.childGender(child.getGender().toString())
			.interests(buildInterests(theme))
			.choiceTraitCandidates(prepareTraitCandidates(Collections.emptyList(), Collections.emptyMap()))
			.build();

		AiResponseDto aiResponse = storyGenerator.generateStoryStart(requestDto);

		Story story = Story.builder().
			child(child).
			theme(theme).
			status(StoryStatus.IN_PROGRESS).
			currentStep(0).
			build();

		Story savedStory = storyRepository.save(story);

		// 첫 페이지만 동기 생성, 나머지는 템플릿으로 저장
		saveFirstPageFromAiResponse(savedStory, aiResponse, Collections.emptyList());
		return savedStory;
	}

	public int makeChoice(Long storyId, Long choiceId) {
		String lockKey = "choice-story-" + storyId;
		Object lock = choiceProcessingLocks.computeIfAbsent(lockKey, k -> new Object());

		try {
			            synchronized (lock) {
			                Story story = storyRepository.findById(storyId)
			                    .orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
			
			                // 방어 코드 추가: 완료된 스토리에서는 더 이상 선택을 처리하지 않음
			                if (story.getStatus() == StoryStatus.COMPLETED) {
			                    log.warn("[스토리 {}] 완료된 스토리에서 중복 선택 시도. 현재 스텝을 반환합니다.", storyId);
			                    return story.getCurrentStep();
			                }
			
			                // 중복 요청 방지: 이미 해당 스텝에 대한 선택 기록이 있는지 확인
			                if (storySelectLogRepository.existsByStoryAndStep(story, story.getCurrentStep())) {					log.warn("[스토리 {}] 중복 선택 요청: Step {} 에 대한 선택이 이미 처리되었습니다. 현재 페이지를 반환합니다.", storyId, story.getCurrentStep());
					// 이미 다음 페이지가 생성되었을 수 있으므로, 다음 스텝 번호를 반환해준다.
					return story.getCurrentStep() + 1;
				}

				StoryChoice choice = storyChoiceRepository.findById(choiceId)
					.orElseThrow(() -> new IllegalArgumentException("Invalid choice Id:" + choiceId));

				if (story.getStatus() == StoryStatus.RECOMMENDED && StringUtils.hasText(choice.getTargetJobName())) {
					throw new IllegalStateException("직업 추천 단계의 선택지는 /story/start-job으로 전달되어야 합니다.");
				}

				storySelectLogRepository.save(StorySelectLog.builder()
					.story(story)
					.page(choice.getPage())
					.choice(choice)
					.step(story.getCurrentStep())
					.build());

				// 분기점 선택 시, 기존에 생성된 뒷 이야기들을 모두 삭제
				int choiceStep = choice.getPage().getStep();
				storyPageRepository.deleteByStoryAndStepGreaterThan(story, choiceStep);
				storyPageTemplateRepository.deleteByStoryAndStepGreaterThan(story, choiceStep);

				storySelectLogRepository.flush();
				long choiceCount = storySelectLogRepository.countByStory(story);
				boolean isJobStory = story.getStatus() == StoryStatus.JOB_STARTED || story.getStatus() == StoryStatus.IN_JOB_PROGRESS;
				List<String> selectedTraits = parseTraits(choice.getTraitsJson());
				log.info("[Story {}] Step {} choice '{}', traits {}", story.getId(), story.getCurrentStep(), choice.getLabel(), selectedTraits);
				Map<String, Integer> traitSnapshot = countTraitsInStory(story);
				log.info("[Story {}] Trait counts after step {}: {}", story.getId(), choiceCount, traitSnapshot);

				if (choiceCount < STORY_CHOICE_LIMIT) {
					List<List<String>> nextTraitTagGroups = prepareTraitCandidates(selectedTraits, traitSnapshot);
					log.info("[Story {}] Suggested tag groups for next step {}: {}", story.getId(), choiceCount + 1, nextTraitTagGroups);
					List<String> interests = buildInterests(story.getTheme());
					StoryNextStepRequestDto requestDto = StoryNextStepRequestDto.builder()
						.childName(story.getChild().getName())
						.childGender(story.getChild().getGender().toString())
						.previousChoice(choice.getLabel())
						.currentStep(Math.min((int) choiceCount + 1, STORY_CHOICE_LIMIT))
						.choiceTraitCandidates(nextTraitTagGroups)
						.interests(interests)
						.build();
					AiResponseDto aiResponse = storyGenerator.generateNextStep(requestDto);
					if (isJobStory) {
						story.updateStatus(StoryStatus.IN_JOB_PROGRESS);
					}
					return saveFirstPageFromAiResponse(story, aiResponse, Collections.emptyList());
				} else {
					if (isJobStory) {
						AiResponseDto finalResponse = AiResponseDto.builder()
							.narrationSections(List.of(
								"토키: " + story.getChild().getName() + "야, 오늘 " +
									(story.getSelectedJob() != null ? story.getSelectedJob().getName() : "모험") + " 체험을 멋지게 끝내줬구나!",
								"모두가 너의 활약에 감동했단다. 잠시 쉬었다가 또 다른 모험을 떠나보자."))
							.problem(null)
							.choices(Collections.emptyList())
							.build();
						story.updateStatus(StoryStatus.COMPLETED);
						int nextStepForCompletion = saveFirstPageFromAiResponse(story, finalResponse, Collections.emptyList());
						applyTitleAndSummary(story);
						return nextStepForCompletion;
					}
					List<String> recommendedJobs = recommendJobsBasedOnTraits(story);
					for (int i = recommendedJobs.size(); i < 3; i++) {
						recommendedJobs.add(JOB_FALLBACKS.get(i % JOB_FALLBACKS.size()));
					}
					StoryCompletionRequestDto requestDto = StoryCompletionRequestDto.builder()
						.childName(story.getChild().getName()).childGender(story.getChild().getGender().toString())
						.lastChoice(choice.getLabel()).recommendedJobs(recommendedJobs).build();
					AiResponseDto aiResponse = storyGenerator.generateStoryCompletion(requestDto);
					story.updateStatus(StoryStatus.RECOMMENDED);
					return saveFirstPageFromAiResponse(story, aiResponse, recommendedJobs);
				}
			}
		} finally {
			choiceProcessingLocks.remove(lockKey);
		}
	}

	public Story startJobStory(Long previousStoryId, String jobName, String themeWorld) {
		Story previousStory = storyRepository.findById(previousStoryId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + previousStoryId));
		Child child = previousStory.getChild();
		Job job = jobRepository.findByName(jobName)
			.orElseThrow(() -> new IllegalArgumentException("Invalid job name: " + jobName));

		String coreTrait = findCoreTrait(previousStory);
		String resolvedThemeWorld = StringUtils.hasText(themeWorld) ? themeWorld.trim() : job.getName() + " 나라";

		Theme theme = previousStory.getTheme();
		List<String> interests = buildInterests(theme);

		JobExperienceStartRequestDto requestDto = JobExperienceStartRequestDto.builder()
			.childName(child.getName()).childGender(child.getGender().toString())
			.selectedJob(job.getName()).themeWorld(resolvedThemeWorld)
			.coreTrait(coreTrait)
			.interests(interests)
			.build();

		AiResponseDto aiResponse = storyGenerator.generateJobExperienceStart(requestDto);

		Story jobStory = Story.builder().child(child).theme(previousStory.getTheme()).status(StoryStatus.JOB_STARTED)
			.currentStep(0).selectedJob(job).build();
		Story savedJobStory = storyRepository.save(jobStory);

		saveFirstPageFromAiResponse(savedJobStory, aiResponse, Collections.emptyList());
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
		Map<String, Integer> canonicalTraitCounts = new HashMap<>();
		for (Map.Entry<String, Integer> entry : traitCounts.entrySet()) {
			String canonical = stripTraitDecorations(entry.getKey());
			if (canonical != null) {
				canonicalTraitCounts.merge(canonical, entry.getValue(), Integer::sum);
			}
		}

		List<String> topCanonicalTraits = canonicalTraitCounts.entrySet().stream()
			.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.limit(3)
			.map(Map.Entry::getKey)
			.toList();

		LinkedHashSet<String> recommendedJobs = new LinkedHashSet<>();
		for (String canonicalTrait : topCanonicalTraits) {
			Optional<Trait> traitOpt = resolveTraitEntity(canonicalTrait);
			if (traitOpt.isEmpty()) {
				log.info("[Story {}] No trait entity found for canonical tag '{}'", story.getId(), canonicalTrait);
				continue;
			}
			Trait trait = traitOpt.get();
			List<TraitJob> rankedJobs = new ArrayList<>(traitJobRepository.findByTraitIn(List.of(trait)));
			rankedJobs.sort(Comparator.comparing(TraitJob::getWeight).reversed());
			Optional<String> topJobName = rankedJobs.stream()
				.map(TraitJob::getJob)
				.map(Job::getName)
				.filter(StringUtils::hasText)
				.filter(name -> !recommendedJobs.contains(name))
				.findFirst();
			if (topJobName.isPresent()) {
				recommendedJobs.add(topJobName.get());
			} else {
				log.info("[Story {}] No distinct job recommendation left for trait '{}'", story.getId(), canonicalTrait);
			}
		}

		log.info("[Story {}] Trait totals {} (canonical {}) -> preliminary jobs {}", story.getId(), traitCounts, canonicalTraitCounts, recommendedJobs);

		List<String> finalList = new ArrayList<>(recommendedJobs);
		for (String fallback : JOB_FALLBACKS) {
			if (finalList.size() >= 3) {
				break;
			}
			if (!finalList.contains(fallback)) {
				finalList.add(fallback);
			}
		}

		if (finalList.size() > 3) {
			finalList = finalList.subList(0, 3);
		}

		log.info("[Story {}] Recommended jobs (fallback applied if needed): {}", story.getId(), finalList);
		return finalList;
	}

	private Map<String, Integer> countTraitsInStory(Story story) {
		List<StorySelectLog> logs = storySelectLogRepository.findByStoryOrderByStepAsc(story);
		Map<String, Integer> traitCounts = new HashMap<>();
		for (StorySelectLog log : logs) {
			for (String trait : parseTraits(log.getChoice().getTraitsJson())) {
				traitCounts.put(trait, traitCounts.getOrDefault(trait, 0) + 1);
			}
		}
		return traitCounts;
	}

	private List<List<String>> prepareTraitCandidates(List<String> selectedTraits, Map<String, Integer> traitSnapshot) {
		int choiceCount = StoryChoice.ChoiceKey.values().length;
		LinkedHashSet<String> ordered = new LinkedHashSet<>();

		if (selectedTraits != null) {
			selectedTraits.stream()
				.forEach(tag -> {
					Optional<String> resolved = resolveTraitTagFromCandidate(tag);
					if (resolved.isPresent()) {
						ordered.add(resolved.get());
					} else if (StringUtils.hasText(tag)) {
						ordered.add(tag.trim());
					}
				});
		}

		if (traitSnapshot != null && !traitSnapshot.isEmpty()) {
			traitSnapshot.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
				.forEach(entry -> {
					Optional<String> resolved = resolveTraitTagFromCandidate(entry.getKey());
					resolved.ifPresent(ordered::add);
				});
		}

		List<String> allTraitTags = traitRepository.findAll().stream()
			.map(Trait::getTag)
			.filter(StringUtils::hasText)
			.collect(Collectors.toList());

		if (!allTraitTags.isEmpty()) {
			List<String> shuffled = new ArrayList<>(allTraitTags);
			Collections.shuffle(shuffled, ThreadLocalRandom.current());
			ordered.addAll(shuffled);
		}

		ordered.addAll(DEFAULT_TRAIT_TAGS);

		List<String> pool = new ArrayList<>(ordered);
		if (pool.isEmpty()) {
			pool.addAll(DEFAULT_TRAIT_TAGS);
		}

		int required = choiceCount * TRAITS_PER_CHOICE;
		while (pool.size() < required && !allTraitTags.isEmpty()) {
			Collections.shuffle(allTraitTags, ThreadLocalRandom.current());
			pool.addAll(allTraitTags);
		}

		if (pool.size() < required) {
			while (pool.size() < required) {
				pool.add(DEFAULT_TRAIT_TAGS.get(pool.size() % DEFAULT_TRAIT_TAGS.size()));
			}
		}

		List<List<String>> result = new ArrayList<>();
		int cursor = 0;
		for (int i = 0; i < choiceCount; i++) {
			List<String> tagsForChoice = new ArrayList<>();
			for (int j = 0; j < TRAITS_PER_CHOICE; j++) {
				if (pool.isEmpty()) {
					break;
				}
				tagsForChoice.add(pool.get(cursor % pool.size()));
				cursor++;
			}
			result.add(tagsForChoice);
		}
		return result;
	}

	/**
	 * 첫 페이지만 동기 생성하고, 나머지 페이지는 템플릿으로 저장
	 * 사용자에게 빠르게 첫 페이지를 보여주기 위함
	 */
	private int saveFirstPageFromAiResponse(Story story, AiResponseDto aiResponse, List<String> fallbackJobNames) {
		List<String> narrationSections = aiResponse.getNarrationSectionsOrDefault().stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.collect(Collectors.toList());

		int firstNewStep = story.getCurrentStep() + 1;
		String lockKey = "story-" + story.getId() + "-step-" + firstNewStep;
		Object lock = pageGenerationLocks.computeIfAbsent(lockKey, k -> new Object());

		try {
			synchronized (lock) {
				// 첫 번째 섹션만 즉시 생성
				if (!narrationSections.isEmpty()) {
					// 잠금을 획득한 후, 페이지가 이미 생성되었는지 다시 확인
					if (pageExists(story, firstNewStep)) {
						log.info("[스토리 {}] 1단계: 페이지 {}가 비동기 작업에 의해 이미 생성되어 동기 생성을 건너뜁니다.", story.getId(), firstNewStep);
					} else {
						String firstSection = narrationSections.get(0);
						StoryPage page = StoryPage.builder()
							.story(story)
							.step(firstNewStep)
							.pageType(PageType.PROGRESS)
							.narration(firstSection)
							.imagePrompt(firstSection) // Use page-specific text as prompt
							.hasChoice(false)
							.build();

						StoryPage savedPage = storyPageRepository.save(page);

						// 첫 페이지 이미지 생성 (동기)
						try {
							// 이미지 생성을 위한 참조 이미지 경로 리스트 생성
						List<String> referenceImagePaths = new java.util.ArrayList<>();
						// 1. 캐릭터 이미지 추가
						referenceImagePaths.add("static/images/sample/" + (story.getChild().getGender() == com.capstone.domain.child.entity.Child.Gender.BOY ? "남자.png" : "여자.png"));
						// 2. 테마 이미지 추가
						referenceImagePaths.add("static/images/themes/" + story.getTheme().getId() + ".jpg");
						// 3. 직업 스토리인 경우 직업 이미지 추가
						if (story.getSelectedJob() != null) {
							referenceImagePaths.add("static/images/jobs/" + story.getSelectedJob().getName() + ".png");
						}

						String imageUrl = storyImageService.generateAndSaveImage(
								firstSection, // Use page-specific text as prompt
								story.getId(),
								savedPage.getStep(),
								referenceImagePaths,
								story.getStatus(),
								story.getChild().getName()
							);
							if (StringUtils.hasText(imageUrl)) {
								savedPage.setImageUrl(imageUrl);
								storyPageRepository.save(savedPage);
								log.info("[스토리 {}] 1단계: 페이지 {} 텍스트 및 이미지 동기 생성 완료.", story.getId(), savedPage.getStep());
							}
						} catch (Exception e) {
							log.warn("[스토리 {}] 1단계: 페이지 {} 이미지 생성 실패: {}",
								story.getId(), savedPage.getStep(), e.getMessage());
						}
					}
					story.updateCurrentStep(firstNewStep);
				}
			}
		} finally {
			pageGenerationLocks.remove(lockKey);
		}


		// 나머지 섹션들을 템플릿으로 저장 (이 부분은 잠금 밖에서 수행)
		int templateStep = firstNewStep + 1;
		for (int i = 1; i < narrationSections.size(); i++) {
			String narrationSection = narrationSections.get(i);
			StoryPageTemplate template = StoryPageTemplate.builder()
				.story(story)
				.step(templateStep++)
				.narration(narrationSection)
				.imagePrompt(narrationSection) // Use page-specific text as prompt
				.pageType(PageType.PROGRESS)
				.hasChoice(false)
				.isGenerated(false)
				.build();
			storyPageTemplateRepository.save(template);
			log.info("[스토리 {}] 2단계: 페이지 {} 텍스트 템플릿 저장 (비동기 이미지 생성 예정).", story.getId(), template.getStep());
		}

		// 선택지 페이지도 템플릿으로 저장
		List<AiResponseDto.ChoiceDto> aiChoices = aiResponse.getChoices();
		boolean hasChoices = aiChoices != null && !aiChoices.isEmpty();
		String problemNarration = StringUtils.hasText(aiResponse.getProblem()) ? aiResponse.getProblem().trim() : "";

		try {
			String choicesJson = hasChoices ? objectMapper.writeValueAsString(aiChoices) : null;
			StoryPageTemplate choiceTemplate = StoryPageTemplate.builder()
				.story(story)
				.step(templateStep)
				.narration(problemNarration)
				.imagePrompt(problemNarration) // Use problem narration as prompt
				.pageType(hasChoices ? PageType.CHOICE : PageType.PROGRESS)
				.hasChoice(hasChoices)
				.choicesJson(choicesJson)
				.isGenerated(false)
				.build();
			storyPageTemplateRepository.save(choiceTemplate);
			log.info("[스토리 {}] 2단계: 페이지 {} 선택지 텍스트 템플릿 저장 (비동기 이미지 생성 예정).", story.getId(), choiceTemplate.getStep());

			// fallbackJobNames를 선택지에 반영하기 위해 별도 저장
			if (hasChoices && !fallbackJobNames.isEmpty()) {
				List<AiResponseDto.ChoiceDto> enrichedChoices = new ArrayList<>();
				for (int i = 0; i < aiChoices.size(); i++) {
					AiResponseDto.ChoiceDto original = aiChoices.get(i);
					String resolvedJobName = resolveJobName(original.getJobName(), fallbackJobNames, i);
					String resolvedThemeWorld = resolveThemeWorld(original.getThemeWorld(), resolvedJobName);

					enrichedChoices.add(AiResponseDto.ChoiceDto.builder()
						.choiceKey(original.getChoiceKey())
						.choiceText(original.getChoiceText())
						.traits(original.getTraitsOrDefault())
						.jobName(resolvedJobName)
						.themeWorld(resolvedThemeWorld)
						.build());
				}
				choiceTemplate.setChoicesJson(objectMapper.writeValueAsString(enrichedChoices));
				storyPageTemplateRepository.save(choiceTemplate);
			}
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize choices to JSON: {}", e.getMessage());
		}

		return firstNewStep;
	}

	/**
	 * 백그라운드에서 비동기로 다음 페이지 생성
	 */
	@Async("storyPageExecutor")
	public void generateNextPageAsync(Long storyId, Integer step) {
		String lockKey = "story-" + storyId + "-step-" + step;
		Object lock = pageGenerationLocks.computeIfAbsent(lockKey, k -> new Object());

		try {
			synchronized (lock) {
				// 잠금을 획득한 후, 실제 트랜잭션 로직 호출
				StoryService proxy = applicationContext.getBean(StoryService.class);
				proxy.generatePageInTransaction(storyId, step);
			}
		} catch (Exception e) {
			log.error("[스토리 {}] 3단계: 페이지 {} 비동기 생성 중 예측하지 못한 예외 발생: {}", storyId, step, e.getMessage(), e);
		} finally {
			// 작업 완료 후 맵에서 락 제거
			pageGenerationLocks.remove(lockKey);
		}
	}

	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public void generatePageInTransaction(Long storyId, Integer step) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Story not found: " + storyId));

		// 트랜잭션 안에서 페이지 존재 여부를 다시 확인
		if (pageExists(story, step)) {
			log.info("[스토리 {}] 3단계: 페이지 {} 가 이미 존재하여 작업을 건너뜁니다.", storyId, step);
			return;
		}

		log.info("[스토리 {}] 3단계: 페이지 {} 비동기 생성 시작. (텍스트 템플릿 -> 페이지 + 이미지).", storyId, step);

		StoryPageTemplate template = storyPageTemplateRepository
			.findByStoryAndStep(story, step)
			.orElse(null);

		if (template == null) {
			log.info("[스토리 {}] 3단계: 페이지 {} 생성에 필요한 템플릿이 존재하지 않습니다.", storyId, step);
			return;
		}

		// 페이지 생성
		StoryPage page = StoryPage.builder()
			.story(story)
			.step(step)
			.pageType(template.getPageType())
			.narration(template.getNarration())
			.imagePrompt(template.getImagePrompt())
			.hasChoice(template.getHasChoice())
			.build();
		StoryPage savedPage = storyPageRepository.save(page);

		// 이미지 생성
		try {
			// 이미지 생성을 위한 참조 이미지 경로 리스트 생성
		List<String> referenceImagePaths = new java.util.ArrayList<>();
		// 1. 캐릭터 이미지 추가
		referenceImagePaths.add("static/images/sample/" + (story.getChild().getGender() == com.capstone.domain.child.entity.Child.Gender.BOY ? "남자.png" : "여자.png"));
		// 2. 테마 이미지 추가
		referenceImagePaths.add("static/images/themes/" + story.getTheme().getId() + ".jpg");
		// 3. 직업 스토리인 경우 직업 이미지 추가
		if (story.getSelectedJob() != null) {
			referenceImagePaths.add("static/images/jobs/" + story.getSelectedJob().getName() + ".png");
		}

		String imageUrl = storyImageService.generateAndSaveImage(
				template.getImagePrompt(),
				story.getId(),
				savedPage.getStep(),
				referenceImagePaths,
				story.getStatus(),
				story.getChild().getName()
			);
			if (StringUtils.hasText(imageUrl)) {
				savedPage.setImageUrl(imageUrl);
				storyPageRepository.save(savedPage);
			}
		} catch (Exception e) {
			log.error("[스토리 {}] 3단계: 페이지 {} 이미지 생성 실패: {}", storyId, step, e.getMessage());
		}

		// 선택지가 있으면 생성
		if (template.getHasChoice() && StringUtils.hasText(template.getChoicesJson())) {
			createChoicesFromJson(savedPage, template.getChoicesJson());
		}

		// 템플릿은 삭제하여 중복 처리 방지
		storyPageTemplateRepository.delete(template);

		log.info("[스토리 {}] 3단계: 페이지 {} 비동기 생성 및 이미지 생성 완료.", storyId, step);
	}

	/**
	 * JSON에서 선택지 생성
	 */
	private void createChoicesFromJson(StoryPage page, String choicesJson) {
		try {
			List<AiResponseDto.ChoiceDto> choiceDtos = objectMapper.readValue(
				choicesJson,
				new TypeReference<List<AiResponseDto.ChoiceDto>>() {}
			);

			for (int i = 0; i < choiceDtos.size(); i++) {
				AiResponseDto.ChoiceDto choiceDto = choiceDtos.get(i);
				String traitsJson = objectMapper.writeValueAsString(choiceDto.getTraitsOrDefault());
				StoryChoice.ChoiceKey choiceKey = resolveChoiceKey(i, choiceDto.getChoiceKey());

				storyChoiceRepository.save(StoryChoice.builder()
					.page(page)
					.choiceKey(choiceKey)
					.label(choiceDto.getChoiceText())
					.traitsJson(traitsJson)
					.targetJobName(choiceDto.getJobName())
					.targetThemeWorld(choiceDto.getThemeWorld())
					.build());
			}
		} catch (JsonProcessingException e) {
			log.error("Failed to parse choices JSON: {}", e.getMessage());
		}
	}

	/**
	 * 페이지 존재 여부 확인
	 */
	private boolean pageExists(Story story, Integer step) {
		return storyPageRepository.findFirstByStoryAndStepOrderByIdAsc(story, step).isPresent();
	}

	/**
	 * 페이지가 생성 완료되었는지 확인
	 */
	public boolean isPageReady(Long storyId, Integer step) {
		Story story = storyRepository.findById(storyId).orElse(null);
		if (story == null) {
			return false;
		}
		return pageExists(story, step);
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

	private String resolveJobName(String rawJobName, List<String> fallbackJobNames, int index) {
		if (fallbackJobNames.isEmpty()) {
			return null;
		}
		String candidate = StringUtils.hasText(rawJobName) ? rawJobName.trim() : null;
		if (!StringUtils.hasText(candidate) && index < fallbackJobNames.size()) {
			candidate = fallbackJobNames.get(index);
		}
		if (StringUtils.hasText(candidate)) {
			final String normalizedCandidate = candidate.replaceAll("\\s+", " ").trim();
			return fallbackJobNames.stream()
				.filter(name -> name.equals(normalizedCandidate)
					|| name.replace(" ", "").equalsIgnoreCase(normalizedCandidate.replace(" ", ""))
					|| normalizedCandidate.contains(name))
				.findFirst()
				.orElse(normalizedCandidate);
		}
		return null;
	}

	private String resolveThemeWorld(String rawThemeWorld, String resolvedJobName) {
		if (StringUtils.hasText(rawThemeWorld)) {
			return rawThemeWorld.replaceAll("\\s+", " ").trim();
		}
		if (StringUtils.hasText(resolvedJobName)) {
			return resolvedJobName.trim() + " 나라";
		}
		return null;
	}

	private String stripTraitDecorations(String tag) {
		if (!StringUtils.hasText(tag)) {
			return null;
		}
		String collapsed = tag.trim().replace("#", "").replaceAll("\\s+", "");
		return StringUtils.hasText(collapsed) ? collapsed : null;
	}

	private Optional<Trait> resolveTraitEntity(String canonicalTag) {
		if (!StringUtils.hasText(canonicalTag)) {
			return Optional.empty();
		}
		String normalized = canonicalTag.trim();
		String plain = normalized.startsWith("#") ? normalized.substring(1) : normalized;
		if (!StringUtils.hasText(plain)) {
			return Optional.empty();
		}
		String hashed = plain.startsWith("#") ? plain : "#" + plain;
		return traitRepository.findByTag(plain)
			.or(() -> traitRepository.findByTag(hashed));
	}

	private Optional<String> resolveTraitTagFromCandidate(String candidateTag) {
		String canonical = stripTraitDecorations(candidateTag);
		return resolveTraitEntity(canonical).map(Trait::getTag);
	}

	private List<String> buildInterests(Theme theme) {
		LinkedHashSet<String> interests = new LinkedHashSet<>();
		if (theme != null) {
			if (StringUtils.hasText(theme.getName())) {
				interests.add(theme.getName());
			}
			if (StringUtils.hasText(theme.getDescription())) {
				String[] tokens = theme.getDescription().split("[,/\n]");
				for (String token : tokens) {
					if (interests.size() >= 3) {
						break;
					}
					String trimmed = token.trim();
					if (StringUtils.hasText(trimmed)) {
						interests.add(trimmed);
					}
				}
			}
		}
		if (interests.size() < 2) {
			interests.add("모험");
		}
		if (interests.size() < 3) {
			interests.add("친구와 협동");
		}
		return interests.stream().limit(3).toList();
	}

	@Transactional(readOnly = true)
	public List<StoryBookSummaryDto> getCompletedBooks(Long childId) {
		List<Story> stories = storyRepository.findByChildIdAndStatusInOrderByCompletedAtDesc(
			childId, List.of(StoryStatus.COMPLETED));
		return stories.stream()
			.map(story -> {
				// 대표 이미지 선택 (중간 페이지 우선, 없으면 첫 이미지)
				String representativeImage = getRepresentativeImage(story);
				String jobName = story.getSelectedJob() != null ? story.getSelectedJob().getName() : null;

				return StoryBookSummaryDto.builder()
					.storyId(story.getId())
					.title(resolveDisplayTitle(story))
					.summary(resolveDisplaySummary(story))
					.imageUrl(representativeImage)
					.jobName(jobName)
					.completedAt(story.getCompletedAt())
					.build();
			})
			.toList();
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

	@Transactional(readOnly = true)
	public StoryBookDetailDto getStoryBook(Long storyId, Long childId) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
		if (!story.getChild().getId().equals(childId)) {
			throw new IllegalArgumentException("Story does not belong to the selected child.");
		}
		List<StoryPage> pages = storyPageRepository.findByStoryOrderByStepAsc(story);
		Map<Long, StorySelectLog> logMap = storySelectLogRepository.findByStoryOrderByStepAsc(story).stream()
			.collect(Collectors.toMap(log -> log.getPage().getId(), log -> log, (a, b) -> a, LinkedHashMap::new));
		String contentHtml = buildContentHtml(pages, logMap);
		return StoryBookDetailDto.builder()
			.storyId(storyId)
			.title(resolveDisplayTitle(story))
			.summary(resolveDisplaySummary(story))
			.contentHtml(contentHtml)
			.build();
	}

	@Transactional
	public StoryPageResponseDto getPage(Long storyId, Integer step) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid finishStory Id:" + storyId));
		StoryPage page = storyPageRepository.findFirstByStoryAndStepOrderByIdAsc(story, step)
			.orElseThrow(() -> new IllegalArgumentException("Invalid step:" + step));

		// 사용자의 현재 위치를 최신으로 갱신
		if (step > story.getCurrentStep()) {
			story.updateCurrentStep(step);
		}

		List<ChoiceResponseDto> choices = storyChoiceRepository.findByPage(page)
			.stream()
			.map(choice -> ChoiceResponseDto.builder()
				.choiceId(choice.getId())
				.text(choice.getLabel())
				.jobName(choice.getTargetJobName())
				.themeWorld(choice.getTargetThemeWorld())
				.build())
			.collect(Collectors.toList());

		// 선택 로그 조회
		ChoiceResponseDto selectedChoice = storySelectLogRepository.findFirstByPageOrderByIdDesc(page)
			.map(log -> ChoiceResponseDto.builder()
				.choiceId(log.getChoice().getId())
				.text(log.getChoice().getLabel())
				.jobName(log.getChoice().getTargetJobName())
				.themeWorld(log.getChoice().getTargetThemeWorld())
				.build())
			.orElse(null);

		// 다음 페이지가 없으면 백그라운드에서 생성 시작
		Integer nextStep = step + 1;
		if (!pageExists(story, nextStep)) {
			// 템플릿이 있는지 확인
			Optional<StoryPageTemplate> nextTemplate = storyPageTemplateRepository
				.findByStoryAndStepAndIsGeneratedFalse(story, nextStep);
			if (nextTemplate.isPresent()) {
				log.info("[스토리 {}] 4단계: 다음 페이지 {} 조회를 위해 비동기 생성 요청.", storyId, nextStep);
				// ApplicationContext를 통해 프록시 빈을 가져와서 @Async가 동작하도록 함
				StoryService proxy = applicationContext.getBean(StoryService.class);
				proxy.generateNextPageAsync(storyId, nextStep);
			}
		}

		StoryPageResponseDto dto = StoryPageResponseDto.from(page, choices);
		        return StoryPageResponseDto.builder()
		                .storyId(dto.storyId())
		                .step(dto.step())
		                .themeName(dto.themeName())
		                .narration(dto.narration())
		                .imageUrl(dto.imageUrl())
		                .hasChoice(dto.hasChoice())
		                .choices(dto.choices())
		                .storyTitle(dto.storyTitle())
		                .completion(dto.completion())
		                .hasNext(dto.hasNext())
		                .selectedChoice(selectedChoice)
		                .status(dto.status())
		                .build();	}

	@Transactional(readOnly = true)
	public boolean hasNextPage(Long storyId, Integer currentStep) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
		return storyPageRepository.findFirstByStoryAndStepOrderByIdAsc(story, currentStep + 1).isPresent();
	}

	private void applyTitleAndSummary(Story story) {
		if (story == null) {
			return;
		}
		TitleResponseDto titleResponse = null;
		if (!StringUtils.hasText(story.getTitle())) {
			try {
				titleResponse = storyGenerator.generateStorybookTitle(buildTitleGenerationRequest(story));
			} catch (Exception e) {
				log.warn("Failed to generate story title for story {}: {}", story.getId(), e.getMessage());
			}
		}
		String titleCandidate = story.getTitle();
		if (!StringUtils.hasText(titleCandidate)) {
			if (titleResponse != null && StringUtils.hasText(titleResponse.getTitle())) {
				titleCandidate = titleResponse.getTitle();
			} else {
				titleCandidate = fallbackTitleForStory(story);
			}
		}
		String summaryCandidate = titleResponse != null ? titleResponse.getReason() : story.getSummary();
		String resolvedSummary = buildSummaryFromStory(story, summaryCandidate);
		story.updateBookMetadata(titleCandidate, resolvedSummary);
	}

	private TitleGenerationRequestDto buildTitleGenerationRequest(Story story) {
		return TitleGenerationRequestDto.builder()
			.childName(story.getChild().getName())
			.coreInterests(buildInterests(story.getTheme()))
			.selectedJob(story.getSelectedJob() != null ? story.getSelectedJob().getName() : null)
			.mostFrequentTrait(findCoreTrait(story))
			.build();
	}

	private String buildSummaryFromStory(Story story, String candidate) {
		String summary = StringUtils.hasText(candidate) ? candidate.trim() : null;
		if (!StringUtils.hasText(summary)) {
			List<StoryPage> pages = storyPageRepository.findByStoryOrderByStepAsc(story);
			summary = pages.isEmpty() ? "우리의 모험이 멋지게 완성되었어요!" : pages.get(0).getNarration();
		}
		return truncate(summary, 120);
	}

	private String resolveDisplayTitle(Story story) {
		if (StringUtils.hasText(story.getTitle())) {
			return story.getTitle();
		}
		return fallbackTitleForStory(story);
	}

	private String fallbackTitleForStory(Story story) {
		if (story.getSelectedJob() != null) {
			return story.getSelectedJob().getName() + " 체험기";
		}
		if (story.getTheme() != null && StringUtils.hasText(story.getTheme().getName())) {
			return story.getTheme().getName() + " 모험";
		}
		return "나의 모험 이야기";
	}

	private String resolveDisplaySummary(Story story) {
		if (StringUtils.hasText(story.getSummary())) {
			return story.getSummary();
		}
		return buildSummaryFromStory(story, null);
	}

	private String buildContentHtml(List<StoryPage> pages, Map<Long, StorySelectLog> logsByPageId) {
		StringBuilder sb = new StringBuilder();
		for (StoryPage page : pages) {
			if (StringUtils.hasText(page.getNarration())) {
				sb.append("<p>")
					.append(HtmlUtils.htmlEscape(page.getNarration()).replace("\n", "<br/>"))
					.append("</p>");
			}
			if (Boolean.TRUE.equals(page.getHasChoice())) {
				StorySelectLog log = logsByPageId.get(page.getId());
				if (log != null && log.getChoice() != null && StringUtils.hasText(log.getChoice().getLabel())) {
					sb.append("<p><strong>나의 선택:</strong> ")
						.append(HtmlUtils.htmlEscape(log.getChoice().getLabel()))
						.append("</p>");
				}
			}
		}
		return sb.toString();
	}

	private List<String> parseTraits(String traitsJson) {
		if (!StringUtils.hasText(traitsJson)) {
			return Collections.emptyList();
		}
		try {
			return objectMapper.readValue(traitsJson, new TypeReference<List<String>>() {
			});
		} catch (IOException e) {
			log.warn("Failed to parse traits JSON: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	private String truncate(String text, int maxLength) {
		if (!StringUtils.hasText(text) || text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
	}
}
