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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StoryService {

	private static final int STORY_CHOICE_LIMIT = 3;
	private static final List<String> JOB_FALLBACKS = List.of("소방관", "교사", "과학자(실험실 연구원)");
	private static final List<String> DEFAULT_TRAIT_TAGS = List.of("#용기", "#상상력", "#협동심", "#친절", "#탐구심");
	private static final int TRAITS_PER_CHOICE = 3;
	private static final int STORY_COMPLETED_REDIRECT = -1;

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

		saveSceneFromAiResponse(savedStory, aiResponse);
		return savedStory;
	}

	public int makeChoice(Long storyId, Long choiceId) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
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
			StoryNextStepRequestDto requestDto = StoryNextStepRequestDto.builder()
				.childName(story.getChild().getName())
				.childGender(story.getChild().getGender().toString())
				.previousChoice(choice.getLabel())
				.currentStep(Math.min((int)choiceCount + 1, STORY_CHOICE_LIMIT))
				.choiceTraitCandidates(nextTraitTagGroups)
				.build();
			AiResponseDto aiResponse = storyGenerator.generateNextStep(requestDto);
			if (isJobStory) {
				story.updateStatus(StoryStatus.IN_JOB_PROGRESS);
			}
			return saveSceneFromAiResponse(story, aiResponse);
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
				saveSceneFromAiResponse(story, finalResponse, Collections.emptyList());
				applyTitleAndSummary(story);
				return STORY_COMPLETED_REDIRECT;
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
			return saveSceneFromAiResponse(story, aiResponse, recommendedJobs);
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

		JobExperienceStartRequestDto requestDto = JobExperienceStartRequestDto.builder()
			.childName(child.getName()).childGender(child.getGender().toString())
			.selectedJob(job.getName()).themeWorld(resolvedThemeWorld)
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

	private int saveSceneFromAiResponse(Story story, AiResponseDto aiResponse) {
		return saveSceneFromAiResponse(story, aiResponse, Collections.emptyList());
	}

	private int saveSceneFromAiResponse(Story story, AiResponseDto aiResponse, List<String> fallbackJobNames) {
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
				String resolvedJobName = resolveJobName(choiceDto.getJobName(), fallbackJobNames, i);
				String resolvedThemeWorld = resolveThemeWorld(choiceDto.getThemeWorld(), resolvedJobName);
				storyChoiceRepository.save(StoryChoice.builder()
					.page(choicePage)
					.choiceKey(choiceKey)
					.label(choiceDto.getChoiceText())
					.traitsJson(traitsJson)
					.targetJobName(resolvedJobName)
					.targetThemeWorld(resolvedThemeWorld)
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
			.map(story -> StoryBookSummaryDto.builder()
				.storyId(story.getId())
				.title(resolveDisplayTitle(story))
				.summary(resolveDisplaySummary(story))
				.completedAt(story.getCompletedAt())
				.build())
			.toList();
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

	@Transactional(readOnly = true)
	public StoryPageResponseDto getPage(Long storyId, Integer step) {
		Story story = storyRepository.findById(storyId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid story Id:" + storyId));
		StoryPage page = storyPageRepository.findByStoryAndStep(story, step)
			.orElseThrow(() -> new IllegalArgumentException("Invalid step:" + step));
		List<ChoiceResponseDto> choices = storyChoiceRepository.findByPage(page)
			.stream()
			.map(choice -> ChoiceResponseDto.builder()
				.choiceId(choice.getId())
				.text(choice.getLabel())
				.jobName(choice.getTargetJobName())
				.themeWorld(choice.getTargetThemeWorld())
				.build())
			.collect(Collectors.toList());
		return StoryPageResponseDto.from(page, choices);
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
