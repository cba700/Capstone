package com.capstone.domain.story.service;

import com.capstone.domain.child.entity.Child;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiImageService {

    private final Client geminiClient;  // 텍스트용 (프롬프트 변환)
    private final Client vertexAiClient;  // 이미지 생성용

    @Value("${gemini.model-name}")
    private String textModelName;

    @Value("${gemini.image-model-name}")
    private String imageModelName;

    @Value("${file.upload-dir}")
    private String uploadDir;

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
     * 이미지 생성 메인 메서드
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

            log.info("[Gemini Image] Generating image for story {} step {}", storyId, step);
            log.info("[Gemini Image] Using prompt: {}", englishPrompt);

            // 2. 이미지 생성 (Vertex AI Client 사용)
            // Note: google-genai:1.0.0 버전에서는 이미지 생성이 제한적일 수 있습니다.
            // Vertex AI API를 직접 호출하거나 최신 버전으로 업그레이드가 필요할 수 있습니다.
            GenerateContentResponse response = vertexAiClient.models.generateContent(
                    imageModelName,
                    englishPrompt,
                    null
            );

            // 3. 이미지 데이터 추출 및 저장
            byte[] imageData = extractImageData(response);
            String filePath = saveImage(imageData, storyId, step);

            log.info("[Gemini Image] Image saved: {}", filePath);
            return filePath;

        } catch (Exception e) {
            log.error("[Gemini Image] Failed to generate image for story {} step {}", storyId, step, e);
            log.warn("[Gemini Image] 이미지 생성에 실패했습니다. google-genai 라이브러리 버전이나 Vertex AI 설정을 확인해주세요.");
            return null;  // 이미지 생성 실패해도 스토리는 계속 진행
        }
    }

    /**
     * 응답에서 이미지 바이트 데이터 추출
     */
    private byte[] extractImageData(GenerateContentResponse response) {
        // Google Genai SDK의 응답 구조에 따라 이미지 데이터 추출
        try {
            if (response.candidates() != null && !response.candidates().isEmpty()) {
                var candidate = response.candidates().get(0);
                if (candidate.content() != null && candidate.content().parts() != null) {
                    for (var part : candidate.content().parts()) {
                        // inlineData 방식으로 이미지 데이터가 포함될 수 있음
                        if (part.inlineData() != null && part.inlineData().data() != null) {
                            return part.inlineData().data();
                        }

                        // 또는 다른 형태로 이미지가 포함될 수 있음
                        // 실제 응답 구조를 로그로 확인하여 디버깅
                        log.debug("[Gemini Image] Part type: {}", part.getClass().getName());
                    }
                }
            }

            // 이미지 데이터를 찾지 못한 경우
            log.error("[Gemini Image] Response structure: {}", response);
            throw new RuntimeException("No image data found in response. Response may not contain image.");

        } catch (Exception e) {
            log.error("[Gemini Image] Error extracting image data", e);
            throw new RuntimeException("Failed to extract image data from response", e);
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
        ImageIO.write(image, "png", filePath.toFile());

        // 웹 접근 가능한 상대 경로 반환
        return "/uploads/story-images/" + storyId + "/" + fileName;
    }
}
