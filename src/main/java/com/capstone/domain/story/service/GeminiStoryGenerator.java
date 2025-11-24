package com.capstone.domain.story.service;

import com.capstone.domain.story.dto.request.ChildAnalysisRequestDto;
import com.capstone.domain.story.dto.request.JobExperienceStartRequestDto;
import com.capstone.domain.story.dto.request.StoryCompletionRequestDto;
import com.capstone.domain.story.dto.request.StoryNextStepRequestDto;
import com.capstone.domain.story.dto.request.StoryStartRequestDto;
import com.capstone.domain.story.dto.request.TitleGenerationRequestDto;
import com.capstone.domain.story.dto.response.AiResponseDto;
import com.capstone.domain.story.dto.response.ChildAnalysisResponseDto;
import com.capstone.domain.story.dto.response.TitleResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Primary // StoryGenerator의 기본 구현체로 지정
@RequiredArgsConstructor
public class GeminiStoryGenerator implements StoryGenerator {

    private static final String BASE_PREAMBLE = String.join("\n",
            "당신은 5~7세 아이에게 직접 이야기를 들려주는 친근한 토끼 '토키'입니다.",
            "절대적인 안전 원칙을 지키고, 폭력성·공포·선정성·비윤리적 표현을 절대 사용하지 마세요.",
            "모든 문단은 최대 3문장으로 유지하고, 아이 눈높이의 다정한 구어체(~했단다, ~했어 등)를 사용하세요.",
            "밝고 긍정적인 단어만 사용하며, 모든 출력은 한국어입니다.");

    private static final String JSON_SCHEMA_GUIDE = "JSON 블록은 다음 스키마를 따라야 합니다:\n" +
            "```json\n" +
            "{\n" +
            "  \"narrationSections\": [\"짧은 문단 1\", \"짧은 문단 2\"],\n" +
            "  \"problem\": \"마지막에 제시할 문제 상황이나 다음 선택 안내\",\n" +
            "  \"imagePrompt\": \"현재 장면을 묘사하는 이미지 생성용 영어 프롬프트 (어린이용 일러스트 스타일)\",\n" +
            "  \"choices\": [\n" +
            "    {\"key\": \"A\", \"text\": \"선택지 문장\", \"traits\": [\"#용기\"], \"jobName\": \"관련된 직업명(필요 시)\", \"themeWorld\": \"연결된 테마 월드 이름(필요 시)\"},\n" +
            "    {\"key\": \"B\", \"text\": \"선택지 문장\", \"traits\": [\"#호기심\"], \"jobName\": null, \"themeWorld\": null},\n" +
            "    {\"key\": \"C\", \"text\": \"선택지 문장\", \"traits\": [\"#상상력\"], \"jobName\": null, \"themeWorld\": null}\n" +
            "  ]\n" +
            "}\n" +
            "```";

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(GeminiStoryGenerator.class);

    private final Client geminiClient;

    @Value("${gemini.model-name}")
    private String modelName;


    @Override
    public AiResponseDto generateStoryStart(StoryStartRequestDto dto) {
        String prompt = buildStoryStartPrompt(dto);
        String rawResponse = generateContent(prompt);
        return parseAiResponse(rawResponse);
    }

    @Override
    public AiResponseDto generateNextStep(StoryNextStepRequestDto dto) {
        String prompt = buildNextStepPrompt(dto);
        String rawResponse = generateContent(prompt);
        return parseAiResponse(rawResponse);
    }

    @Override
    public AiResponseDto generateStoryCompletion(StoryCompletionRequestDto dto) {
        String prompt = buildStoryCompletionPrompt(dto);
        String rawResponse = generateContent(prompt);
        return parseAiResponse(rawResponse);
    }

    @Override
    public AiResponseDto generateJobExperienceStart(JobExperienceStartRequestDto dto) {
        String prompt = buildJobExperienceStartPrompt(dto);
        String rawResponse = generateContent(prompt);
        return parseAiResponse(rawResponse);
    }

    @Override
    public TitleResponseDto generateStorybookTitle(TitleGenerationRequestDto dto) {
        String prompt = buildTitleGenerationPrompt(dto);
        String rawResponse = generateContent(prompt);
        return parseTitleResponse(rawResponse);
    }

    @Override
    public ChildAnalysisResponseDto analyzeChild(ChildAnalysisRequestDto dto) {
        String prompt = buildChildAnalysisPrompt(dto);
        String rawResponse = generateContent(prompt);
        return parseChildAnalysisResponse(rawResponse);
    }


    private String generateContent(String prompt) {
        try {
            GenerateContentResponse response = geminiClient.models.generateContent(modelName, prompt, null);
            String text = response.text();
            log.info("[Gemini] Raw response:\n{}", text);
            return text;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate content from Gemini API", e);
        }
    }

    private String buildStoryStartPrompt(StoryStartRequestDto dto) {
        StringBuilder prompt = new StringBuilder(BASE_PREAMBLE);
        prompt.append("\n\n# 작업: 이야기 시작\n");
        prompt.append("아이에게 첫 문제 상황과 선택지를 들려주는 도입부를 작성하세요.\n\n");

        prompt.append("## 입력 정보\n");
        prompt.append("- 아이 이름: ").append(dto.getChildName()).append('\n');
        prompt.append("- 아이 나이: ").append(dto.getChildAge()).append("세\n");
        prompt.append("- 아이 성별: ").append(dto.getChildGender()).append('\n');
        prompt.append("- 관심사: ").append(String.join(", ", dto.getInterests())).append('\n');
        appendIfPresent(prompt, "- 친구 이름", dto.getFriendName());
        appendIfPresent(prompt, "- 반려동물", dto.getPet());
        appendIfPresent(prompt, "- 성향", dto.getPersonality());
        appendChoiceTraitCandidates(prompt, dto.getChoiceTraitCandidatesOrDefault());

        prompt.append("\n## 출력 지침\n");
        prompt.append("1. 토키의 말투로 1~2개의 짧은 문단을 작성해 모험 배경과 첫 문제 상황을 설명합니다.\n");
        prompt.append("2. 이어서 '문제 상황' 표기 아래에 핵심 상황을 다시 한 번 간단히 요약합니다.\n");
        prompt.append("3. '선택지' 표기 아래에 A, B, C 선택지를 제공하고 각각 1~2개의 성향 태그(#용기 등)를 괄호 안에 표기합니다.\n");
        prompt.append("4. 태그는 서버가 전달한 후보에서 골라 사용하고, 그대로 JSON에도 반영합니다.\n");
        prompt.append("5. 마지막 줄에 위 스키마와 동일한 JSON 블록을 제공합니다. JSON 안의 텍스트는 한국어로, imagePrompt만 영어로 작성합니다.\n");
        prompt.append(JSON_SCHEMA_GUIDE);
        prompt.append("\nJSON 키 이름과 구조를 반드시 그대로 지키고, traits 배열에는 '#'이 포함된 태그만 넣으세요.\n");
        prompt.append("choice.text 값에는 태그 표현을 포함하지 말고, 선택 문장만 넣으세요.\n");
        prompt.append("themeWorld 값은 아직 직업 추천 단계가 아니므로 null로 남겨둡니다.\n");
        prompt.append("imagePrompt에는 현재 장면을 표현하는 영어 프롬프트를 작성하세요. 예: 'cute children's book illustration, fantasy adventure scene, colorful and friendly style, safe for kids'\n");
        return prompt.toString();
    }

    private String buildNextStepPrompt(StoryNextStepRequestDto dto) {
        StringBuilder prompt = new StringBuilder(BASE_PREAMBLE);
        prompt.append("\n\n# 작업: 이야기 진행\n");
        prompt.append("아이가 이전에 고른 선택을 반영하여 다음 장면을 마지막 선택(3번째) 전까지 이어가세요.\n\n");

        prompt.append("## 입력 정보\n");
        prompt.append("- 아이 이름: ").append(dto.getChildName()).append('\n');
        prompt.append("- 아이 성별: ").append(dto.getChildGender()).append('\n');
        prompt.append("- 이전 선택: ").append(dto.getPreviousChoice()).append('\n');
        prompt.append("- 현재 선택 횟수: ").append(dto.getCurrentStep()).append('\n');
        appendIfPresent(prompt, "- 친구 이름", dto.getFriendName());
        appendIfPresent(prompt, "- 반려동물", dto.getPet());
        appendIfPresent(prompt, "- 성향", dto.getPersonality());
        appendChoiceTraitCandidates(prompt, dto.getChoiceTraitCandidatesOrDefault());
        if (CollectionUtils.isEmpty(dto.getChoiceTraitCandidatesOrDefault())) {
            prompt.append("- 새로운 선택지 태그 후보: (서버에서 제시되지 않으므로, 이야기 맥락에 맞는 긍정 태그를 직접 정하세요)\n");
        }

        prompt.append("\n## 출력 지침\n");
        prompt.append("1. 이전 선택의 결과와 이어지는 이야기를 1~2개의 짧은 문단으로 들려줍니다.\n");
        prompt.append("2. '---' 구분선을 추가하여 새로운 문제 상황을 명확히 나눕니다.\n");
        prompt.append("3. '문제 상황' 아래에 다음 선택을 유도하는 질문 또는 미션을 제시합니다.\n");
        prompt.append("4. '선택지' 아래에 A, B, C 선택지를 작성하고, 제공된 태그 후보를 그대로 사용해 괄호에 표기합니다.\n");
        prompt.append("5. 마지막에 JSON 스키마를 사용하여 기계 판독용 데이터를 제공합니다.\n");
        prompt.append(JSON_SCHEMA_GUIDE);
        prompt.append("\nJSON 블록은 이야기 본문 다음에 한 번만 제공하고, traits에는 '#'이 포함된 태그 명칭만 넣으세요.\n");
        prompt.append("choice.text는 태그 없이 선택 문장만 포함해야 합니다.\n");
        prompt.append("themeWorld 값은 직업 추천 이전 단계이므로 null로 설정합니다.\n");
        prompt.append("imagePrompt에는 현재 이야기 장면을 묘사하는 영어 프롬프트를 작성하세요. 어린이용 일러스트 스타일로 설명하세요.\n");
        return prompt.toString();
    }

    private String buildStoryCompletionPrompt(StoryCompletionRequestDto dto) {
        StringBuilder prompt = new StringBuilder(BASE_PREAMBLE);
        prompt.append("\n\n# 작업: 이야기 완결 및 테마 월드 제안\n");
        prompt.append("아이가 마지막 선택을 마친 뒤의 결말과 다음 모험 선택지를 하나의 이야기로 마무리하세요.\n\n");

        prompt.append("## 입력 정보\n");
        prompt.append("- 아이 이름: ").append(dto.getChildName()).append('\n');
        prompt.append("- 아이 성별: ").append(dto.getChildGender()).append('\n');
        prompt.append("- 마지막 선택: ").append(dto.getLastChoice()).append('\n');
        appendIfPresent(prompt, "- 친구 이름", dto.getFriendName());
        appendIfPresent(prompt, "- 반려동물", dto.getPet());
        appendIfPresent(prompt, "- 성향", dto.getPersonality());
        List<String> recommendedJobs = dto.getRecommendedJobs() != null ? dto.getRecommendedJobs() : Collections.emptyList();
        String topJob = jobOrPlaceholder(recommendedJobs, 0, "꿈꾸는 발명가");
        String secondJob = jobOrPlaceholder(recommendedJobs, 1, "달콤한 디저트 장인");
        String thirdJob = jobOrPlaceholder(recommendedJobs, 2, "별빛 수호자");
        prompt.append("- 추천 직업 1순위: ").append(topJob).append('\n');
        prompt.append("- 추천 직업 2순위: ").append(secondJob).append('\n');
        prompt.append("- 추천 직업 3순위: ").append(thirdJob).append('\n');

        prompt.append("\n## 출력 지침\n");
        prompt.append("1. 마지막 선택의 결과와 모두가 행복해지는 결말을 2~3개의 짧은 문단으로 서술합니다.\n");
        prompt.append("2. 토키가 아이를 칭찬하며 자연스럽게 다음 모험으로 가는 갈림길을 소개합니다.\n");
        prompt.append("3. A/B/C 선택지는 각각 추천 직업과 연결된 테마 월드를 소개해야 합니다.\n");
        prompt.append("4. 이야기 본문 뒤에 JSON 블록을 제공하여, \"problem\" 필드에는 다음 모험 안내 문단을 넣고, \"choices\"에는 추천 직업 3개를 활용한 선택지를 채웁니다. 각 choice 객체에는 반드시 jobName과 themeWorld 속성을 채워 직업명과 테마 월드를 명시하세요.\n");
        prompt.append(JSON_SCHEMA_GUIDE);
        prompt.append("\nJSON 블록에서 choice.text에는 테마 월드와 직업을 모두 언급하고, traits에는 직업에 어울리는 긍정 태그를 제공합니다. themeWorld에는 선택지에서 안내한 테마 월드 이름만 간결하게 적으세요.\n");
        prompt.append("choice.text에는 태그 표현을 포함하지 마세요.\n");
        prompt.append("imagePrompt에는 모험 완료를 축하하는 장면을 묘사하는 영어 프롬프트를 작성하세요.\n");
        return prompt.toString();
    }

    private String buildJobExperienceStartPrompt(JobExperienceStartRequestDto dto) {
        StringBuilder prompt = new StringBuilder(BASE_PREAMBLE);
        prompt.append("\n\n# 작업: 직업 체험 모험 시작\n");
        prompt.append("아이에게 선택한 직업과 테마 월드를 소개하고 첫 번째 미션을 제시하세요.\n\n");

        prompt.append("## 입력 정보\n");
        prompt.append("- 아이 이름: ").append(dto.getChildName()).append('\n');
        prompt.append("- 아이 성별: ").append(dto.getChildGender()).append('\n');
        prompt.append("- 선택한 직업: ").append(dto.getSelectedJob()).append('\n');
        prompt.append("- 선택한 테마 월드: ").append(dto.getThemeWorld()).append('\n');
        prompt.append("- 핵심 성향: ").append(dto.getCoreTrait()).append('\n');
        appendIfPresent(prompt, "- 친구 이름", dto.getFriendName());
        appendIfPresent(prompt, "- 반려동물", dto.getPet());
        appendIfPresent(prompt, "- 성향", dto.getPersonality());

        prompt.append("\n## 출력 지침\n");
        prompt.append("1. \"직업 체험 시작: [직업]\" 제목과 함께 토키가 테마 월드를 소개합니다.\n");
        prompt.append("2. 짧은 문단으로 아이가 직업을 수행하게 된 배경을 설명합니다.\n");
        prompt.append("3. '첫 번째 미션 (문제 상황)'을 제시하고, 세 가지 선택지를 태그와 함께 제공합니다.\n");
        prompt.append("4. 마지막에 JSON 스키마를 사용하여 본문 정보를 구조화합니다.\n");
        prompt.append(JSON_SCHEMA_GUIDE);
        prompt.append("\ntraits에는 선택한 직업 수행에 도움이 되는 긍정 태그를 넣으세요.\n");
        prompt.append("choice.text에는 태그 표현을 넣지 마세요.\n");
        prompt.append("themeWorld 값에는 입력으로 받은 테마 월드 이름을 그대로 적어주세요.\n");
        prompt.append("imagePrompt에는 직업 체험 시작 장면을 묘사하는 영어 프롬프트를 작성하세요.\n");
        return prompt.toString();
    }

    private String buildTitleGenerationPrompt(TitleGenerationRequestDto dto) {
        StringBuilder prompt = new StringBuilder(BASE_PREAMBLE);
        prompt.append("\n\n# 작업: 스토리북 제목 생성\n");
        prompt.append("아이의 모험을 대표할 한글 제목을 1개 정하고, 이유를 한 문장으로 설명하세요.\n\n");

        prompt.append("## 입력 정보\n");
        prompt.append("- 아이 이름: ").append(dto.getChildName()).append('\n');
        prompt.append("- 핵심 관심사: ").append(String.join(", ", dto.getCoreInterests())).append('\n');
        appendIfPresent(prompt, "- 선택한 직업", dto.getSelectedJob());
        appendIfPresent(prompt, "- 가장 많이 누적된 성향", dto.getMostFrequentTrait());

        prompt.append("\n## 출력 지침\n");
        prompt.append("1. 토키가 축하 인사를 건네는 한 문단을 작성합니다.\n");
        prompt.append("2. 이어서 강조 표시된 최종 제목을 제시합니다.\n");
        prompt.append("3. 마지막에는 아래 JSON을 제공해 기계 판독이 가능하도록 합니다.\n");
        prompt.append("```json\n{\n  \"title\": \"완성된 제목\",\n  \"reason\": \"제목 선정 이유 한 문장\"\n}\n```\n");
        prompt.append("JSON의 키 이름을 바꾸지 말고, 내용은 모두 한국어로 작성하세요.\n");
        return prompt.toString();
    }

    private void appendIfPresent(StringBuilder prompt, String label, String value) {
        if (StringUtils.hasText(value)) {
            prompt.append(label).append(": ").append(value).append('\n');
        }
    }

    private void appendChoiceTraitCandidates(StringBuilder prompt, List<List<String>> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return;
        }
        String[] labels = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < candidates.size(); i++) {
            List<String> group = candidates.get(i);
            if (CollectionUtils.isEmpty(group)) {
                continue;
            }
            String label = i < labels.length ? labels[i] : String.valueOf((char)('A' + i));
            prompt.append("- 선택지 ").append(label).append(" 태그 후보: ")
                .append(String.join(", ", group)).append('\n');
        }
    }

    private AiResponseDto parseAiResponse(String rawResponse) {
        String jsonPayload = extractJsonBlock(rawResponse);
        if (!StringUtils.hasText(jsonPayload)) {
            throw new RuntimeException("Gemini 응답에서 JSON 블록을 찾을 수 없습니다.\n응답: " + rawResponse);
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonPayload);
            List<String> narrationSections = OBJECT_MAPPER.convertValue(
                    root.path("narrationSections"), new TypeReference<List<String>>() {
                    });
            String problem = asTrimmedText(root.path("problem"));
            String imagePrompt = asTrimmedText(root.path("imagePrompt"));

            List<Map<String, Object>> rawChoices = OBJECT_MAPPER.convertValue(
                    root.path("choices"), new TypeReference<List<Map<String, Object>>>() {
                    });

            List<AiResponseDto.ChoiceDto> choices = rawChoices == null ? Collections.emptyList() : rawChoices.stream()
                    .map(choice -> AiResponseDto.ChoiceDto.builder()
                            .choiceKey(asTrimmedText(choice.get("key")))
                            .choiceText(asTrimmedText(choice.get("text")))
                            .traits(parseTraits(choice.get("traits")))
                            .jobName(asTrimmedOrNull(choice.get("jobName")))
                            .themeWorld(asTrimmedOrNull(choice.get("themeWorld")))
                            .build())
                    .toList();

            return AiResponseDto.builder()
                    .narrationSections(narrationSections)
                    .problem(problem)
                    .imagePrompt(imagePrompt)
                    .choices(choices)
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Gemini JSON 응답 파싱에 실패했습니다.", e);
        }
    }

    private TitleResponseDto parseTitleResponse(String rawResponse) {
        String jsonPayload = extractJsonBlock(rawResponse);
        if (!StringUtils.hasText(jsonPayload)) {
            throw new RuntimeException("Gemini 제목 응답에서 JSON 블록을 찾을 수 없습니다.\n응답: " + rawResponse);
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonPayload);
            String title = asTrimmedText(root.path("title"));
            String reason = asTrimmedText(root.path("reason"));
            return TitleResponseDto.builder()
                    .title(title)
                    .reason(reason)
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Gemini 제목 JSON 파싱에 실패했습니다.", e);
        }
    }

    private List<String> parseTraits(Object node) {
        if (node == null) {
            return Collections.emptyList();
        }
        return OBJECT_MAPPER.convertValue(node, new TypeReference<List<String>>() {
        });
    }

    private String jobOrPlaceholder(List<String> jobs, int index, String fallback) {
        if (jobs != null && jobs.size() > index && StringUtils.hasText(jobs.get(index))) {
            return jobs.get(index);
        }
        return fallback;
    }

    private String extractJsonBlock(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        text = text.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }
        return null;
    }

    private String buildChildAnalysisPrompt(ChildAnalysisRequestDto dto) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 아동 발달 및 진로 전문가입니다.\n");
        prompt.append("아이의 스토리 선택 기록을 바탕으로 성향을 분석하고, 부모님께 유익한 조언을 제공하세요.\n\n");

        prompt.append("## 아이 정보\n");
        prompt.append("- 이름: ").append(dto.getChildName()).append('\n');
        prompt.append("- 나이: ").append(dto.getChildAge()).append("세\n");
        prompt.append("- 성별: ").append(dto.getChildGender()).append('\n');
        prompt.append("- 완성한 스토리: ").append(dto.getCompletedStoryCount()).append("개\n\n");

        prompt.append("## 성향 태그 통계 (선택 횟수)\n");
        if (dto.getTraitCounts() != null && !dto.getTraitCounts().isEmpty()) {
            dto.getTraitCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> prompt.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("회\n"));
        } else {
            prompt.append("- 아직 선택 기록이 없습니다.\n");
        }

        prompt.append("\n## 선택한 직업\n");
        if (dto.getSelectedJobs() != null && !dto.getSelectedJobs().isEmpty()) {
            dto.getSelectedJobs().forEach(job -> prompt.append("- ").append(job).append('\n'));
        } else {
            prompt.append("- 아직 선택한 직업이 없습니다.\n");
        }

        prompt.append("\n## 관심사(테마)\n");
        if (dto.getThemes() != null && !dto.getThemes().isEmpty()) {
            dto.getThemes().forEach(theme -> prompt.append("- ").append(theme).append('\n'));
        } else {
            prompt.append("- 아직 관심사가 기록되지 않았습니다.\n");
        }

        prompt.append("\n## 분석 요청\n");
        prompt.append("다음 형식의 JSON으로 분석 결과를 제공하세요:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"personalityAnalysis\": \"아이의 전반적인 성격 분석 (2-3문장)\",\n");
        prompt.append("  \"strengths\": [\"강점1\", \"강점2\", \"강점3\"],\n");
        prompt.append("  \"interestAnalysis\": \"관심 분야 분석 (2-3문장)\",\n");
        prompt.append("  \"recommendedActivities\": [\"추천 활동1\", \"추천 활동2\", \"추천 활동3\"],\n");
        prompt.append("  \"recommendedCareerPaths\": [\"추천 직업 분야1\", \"추천 직업 분야2\", \"추천 직업 분야3\"],\n");
        prompt.append("  \"overallAssessment\": \"부모님을 위한 종합 소견 및 양육 조언 (3-4문장)\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("모든 텍스트는 한국어로 작성하고, 부드럽고 긍정적인 어조를 유지하세요.\n");
        prompt.append("JSON 블록만 출력하고, 다른 설명은 포함하지 마세요.\n");

        return prompt.toString();
    }

    private ChildAnalysisResponseDto parseChildAnalysisResponse(String rawResponse) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(rawResponse);
        if (!matcher.find()) {
            log.warn("[Gemini] No JSON block found in child analysis response");
            return createFallbackAnalysisResponse();
        }

        String jsonText = matcher.group(1);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonText);

            List<String> strengths = parseStringArray(root.get("strengths"));
            List<String> recommendedActivities = parseStringArray(root.get("recommendedActivities"));
            List<String> recommendedCareerPaths = parseStringArray(root.get("recommendedCareerPaths"));

            return ChildAnalysisResponseDto.builder()
                .personalityAnalysis(asTrimmedOrNull(root.get("personalityAnalysis")))
                .strengths(strengths.isEmpty() ? List.of("분석 중입니다") : strengths)
                .interestAnalysis(asTrimmedOrNull(root.get("interestAnalysis")))
                .recommendedActivities(recommendedActivities.isEmpty() ? List.of("곧 추천해드립니다") : recommendedActivities)
                .recommendedCareerPaths(recommendedCareerPaths.isEmpty() ? List.of("분석 중입니다") : recommendedCareerPaths)
                .overallAssessment(asTrimmedOrNull(root.get("overallAssessment")))
                .build();
        } catch (JsonProcessingException e) {
            log.error("[Gemini] Failed to parse child analysis JSON: {}", e.getMessage());
            return createFallbackAnalysisResponse();
        }
    }

    private ChildAnalysisResponseDto createFallbackAnalysisResponse() {
        return ChildAnalysisResponseDto.builder()
            .personalityAnalysis("분석을 진행 중입니다. 잠시 후 다시 시도해주세요.")
            .strengths(List.of("창의력", "호기심", "적극성"))
            .interestAnalysis("아이의 관심사를 분석 중입니다.")
            .recommendedActivities(List.of("그림 그리기", "책 읽기", "자연 탐험"))
            .recommendedCareerPaths(List.of("예술가", "과학자", "교육자"))
            .overallAssessment("아이의 성장 가능성을 분석하고 있습니다. 곧 상세한 분석 결과를 제공해드리겠습니다.")
            .build();
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> result = new java.util.ArrayList<>();
        arrayNode.forEach(node -> {
            String text = asTrimmedOrNull(node);
            if (text != null) {
                result.add(text);
            }
        });
        return result;
    }

    private String asTrimmedText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonNode node) {
            return node.asText("").trim();
        }
        return value.toString().trim();
    }

    private String asTrimmedOrNull(Object value) {
        String text = asTrimmedText(value);
        return StringUtils.hasText(text) ? text : null;
    }
}
