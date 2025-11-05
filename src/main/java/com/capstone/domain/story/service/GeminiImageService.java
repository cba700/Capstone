package com.capstone.domain.story.service;

import com.capstone.domain.child.entity.Child;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiImageService {

    private final Client geminiClient;  // 텍스트용 (프롬프트 변환)

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model-name}")
    private String textModelName;

    @Value("${gemini.image-model-name}")
    private String imageModelName;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[Gemini Image Service] Initialized with image model: {}", imageModelName);
    }

    /**
     * (Text-to-Prompt) 동화 텍스트(장면)와 아이 정보를 받아
     * '일반적인 주인공 묘사'가 포함된 이미지 프롬프트로 변환합니다.
     */
    private String translateToImagePrompt(String narration, Child child) {
        String childName = child.getName();

        String template = String.format("""
            # 역할: 너는 동화책 제작을 위한 전문 프롬프트 엔지니어야.
            # 지시사항:
            1.  '이야기 조각'(한국어)을 읽고, 이 장면을 묘사하는 동화책 스타일의 이미지 프롬프트로 변환해야 해.
            2.  [매우 중요!] 너의 응답은 **반드시 영어(English)로만** 작성되어야 해.
            3.  [형식] '이미지 프롬프트:' 같은 접두사나 부연 설명 없이, 오직 영어 프롬프트 내용만 깔끔하게 반환해.

            # [★★★ (핵심 수정) 주인공 '일반 묘사' 규칙 ★★★★]
            4.  '이야기 조각'에는 '%s'이라는 이름의 주인공이 한 명 등장해.
            5.  **절대로 '%s'이라는 고유 이름을 프롬프트에 포함하지 마.**
            6.  [★★★ 수정 ★★★] 대신, '%s'이 등장하는 부분은 **'a single cute boy', 'one cute girl'처럼 '단 한 명'임을 강조하는 일반적인 묘사**로 바꿔서 표현해.
            7.  너의 묘사에는 이 '일반적인 주인공 묘사'와 그의 '행동', '배경', '다른 등장인물'이 모두 포함되어야 해.
                (예: "%s이가 공룡과 차를 탔다" -> "A single cute boy is driving a space car with friendly dinosaurs.")
            8.  [★★★ 수정 ★★★] 너의 최종 프롬프트 맨 끝에는, **반드시 아래의 영어 구문을 그대로 추가해줘.**
                **"children's storybook illustration, avoid overly 3D style, no text, no letters, no typography, only one main character, no multiple characters, no duplicates, no cloned figures"**
            9.  **[단순화]**: 장면의 핵심 요소(행동, 배경, 다른 등장인물)는 포함하되, 간결하게 묘사해줘.
            10. **[특별 규칙]**: 만약 '이야기 조각'에 '토키'가 등장하면, **반드시 'a friendly rabbit named Toki'**라고 묘사해줘.

            # 아이 이름: %s
            # 이야기 조각: %s

            # English Image Prompt:""",
                childName, childName, childName, childName, childName, narration);

        try {
            GenerateContentResponse response = geminiClient.models.generateContent(
                    textModelName, template, null
            );
            String imagePrompt = response.text().trim();
            log.info("[Image Prompt] Narration: {} → English Prompt: {}",
                     narration.substring(0, Math.min(50, narration.length())) + "...",
                     imagePrompt);
            return imagePrompt;
        } catch (Exception e) {
            log.error("[Image Prompt] Failed to translate narration to image prompt", e);
            throw new RuntimeException("Failed to translate narration to image prompt", e);
        }
    }

    /**
     * 이미지 생성 메인 메서드 (REST API 직접 호출)
     * @param narration 한국어 narration
     * @param child 아이 정보
     * @param storyId 스토리 ID
     * @param step 페이지 번호
     * @return 저장된 이미지 경로
     */
    public String generateImageFromNarration(String narration, Child child, Long storyId, Integer step) {
        try {
            // 1. 한국어 narration → 영어 이미지 프롬프트 변환
            String englishPrompt = translateToImagePrompt(narration, child);

            log.info("[Gemini Image API] Generating image for story {} step {}", storyId, step);
            log.info("[Gemini Image API] Using prompt: {}", englishPrompt);

            // 2. REST API로 이미지 생성 요청
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", englishPrompt)
                    ))
                ),
                "generationConfig", Map.of(
                    "temperature", 0.4,
                    "topK", 32,
                    "topP", 1,
                    "maxOutputTokens", 4096
                )
            );

            // 3. API 호출
            Map<String, Object> response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + imageModelName + ":generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.debug("[Gemini Image API] Response: {}", response);

            // 4. 응답에서 이미지 데이터 추출
            byte[] imageData = extractImageFromResponse(response);

            // 5. 이미지 저장
            String filePath = saveImage(imageData, storyId, step);

            log.info("[Gemini Image API] Image saved: {}", filePath);
            return filePath;

        } catch (Exception e) {
            log.error("[Gemini Image API] Failed to generate image for story {} step {}", storyId, step, e);
            log.warn("[Gemini Image API] 이미지 생성에 실패했습니다. API Key와 모델명을 확인해주세요.");
            return null;  // 이미지 생성 실패해도 스토리는 계속 진행
        }
    }

    /**
     * REST API 응답에서 이미지 데이터 추출
     */
    private byte[] extractImageFromResponse(Map<String, Object> response) {
        try {
            // 응답 구조: { "candidates": [{ "content": { "parts": [{ "inlineData": { "mimeType": "image/png", "data": "base64..." } }] } }] }
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.error("[Gemini Image API] No candidates in response");
                throw new RuntimeException("No candidates in response");
            }

            Map<String, Object> candidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            if (content == null) {
                log.error("[Gemini Image API] No content in candidate");
                throw new RuntimeException("No content in candidate");
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                log.error("[Gemini Image API] No parts in content");
                throw new RuntimeException("No parts in content");
            }

            // 첫 번째 part에서 이미지 데이터 찾기
            for (Map<String, Object> part : parts) {
                Map<String, Object> inlineData = (Map<String, Object>) part.get("inlineData");
                if (inlineData != null) {
                    String base64Data = (String) inlineData.get("data");
                    if (base64Data != null) {
                        log.info("[Gemini Image API] Found base64 image data (length: {})", base64Data.length());
                        return Base64.getDecoder().decode(base64Data);
                    }
                }

                // text가 있는 경우 (이미지가 아닌 텍스트 응답)
                String text = (String) part.get("text");
                if (text != null) {
                    log.warn("[Gemini Image API] API returned text instead of image: {}", text.substring(0, Math.min(100, text.length())));
                }
            }

            throw new RuntimeException("No image data found in response parts");

        } catch (ClassCastException e) {
            log.error("[Gemini Image API] Unexpected response structure", e);
            throw new RuntimeException("Unexpected response structure", e);
        }
    }

    /**
     * 이미지 파일 저장
     * @return 웹 접근 가능한 상대 경로
     */
    private String saveImage(byte[] imageData, Long storyId, Integer step) throws IOException {
        // 디렉토리 생성
        Path storyDir = Paths.get(uploadDir, String.valueOf(storyId));
        Files.createDirectories(storyDir);

        // 파일 저장
        String fileName = step + ".png";
        Path filePath = storyDir.resolve(fileName);

        // 바이트 배열을 이미지로 변환 후 저장
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) {
            log.error("[Gemini Image API] Failed to decode image data");
            throw new IOException("Failed to decode image data");
        }

        ImageIO.write(image, "png", filePath.toFile());

        // 웹 접근 가능한 상대 경로 반환
        return "/uploads/story-images/" + storyId + "/" + fileName;
    }
}
