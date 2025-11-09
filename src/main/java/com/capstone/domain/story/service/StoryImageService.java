package com.capstone.domain.story.service;

import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryImageService {

    private final Client geminiClient;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api-key}")
    private String geminiApiKey;


    @Value("${gemini.image-model-name:gemini-2.5-flash-image}")
    private String imageModelName;

    @Value("${story.image.save-path:./story-images}")
    private String imageSavePath;

    @Value("${story.image.base-url:/story-images}")
    private String imageBaseUrl;

    public String generateAndSaveImage(String imagePrompt, Long storyId, Integer step) {
        if (!StringUtils.hasText(imagePrompt)) {
            log.warn("Empty image prompt for story {} step {}", storyId, step);
            return null;
        }

        try {
            // 이미지 생성 프롬프트 보완
            String enhancedPrompt = buildEnhancedPrompt(imagePrompt);
            log.info("Generating image for story {} step {} with prompt: {}", storyId, step, enhancedPrompt);

            // Gemini로 이미지 생성 시도
            String imageData = generateImageWithGemini(enhancedPrompt);
            
            // Gemini 실패 시 플레이스홀더 사용
            if (imageData == null) {
                imageData = generatePlaceholderImage();
            }
            
            if (!StringUtils.hasText(imageData)) {
                log.warn("Failed to generate image for story {} step {}", storyId, step);
                return null;
            }

            // 이미지 파일 저장
            String filename = saveImageFile(imageData, storyId, step);
            
            // 웹 접근 가능한 URL 반환
            String imageUrl = imageBaseUrl + "/" + filename;
            log.info("Image saved successfully: {}", imageUrl);
            
            return imageUrl;
        } catch (Exception e) {
            log.error("Failed to generate and save image for story {} step {}: {}", storyId, step, e.getMessage(), e);
            return null;
        }
    }

    private String buildEnhancedPrompt(String basePrompt) {
        return String.format(
            "%s, children's book illustration, cute and colorful style, " +
            "safe for kids, friendly cartoon style, bright colors, " +
            "no scary elements, digital art, high quality",
            basePrompt
        );
    }


    private String generateImageWithGemini(String prompt) {
        try {
            log.info("Requesting image generation from Gemini with prompt: {}", prompt);
            
            // Gemini 이미지 생성 API 호출
            String apiUrl = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", 
                imageModelName, geminiApiKey);
            
            // 이미지 생성 요청 바디 구성
            Map<String, Object> requestBody = createImageGenerationRequest(prompt);
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // API 호출
            ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 응답에서 Base64 이미지 데이터 추출
                String base64ImageData = extractImageFromGeminiResponse(response.getBody());
                if (StringUtils.hasText(base64ImageData)) {
                    log.info("Successfully received image from Gemini");
                    return base64ImageData;
                }
            }
            
            log.warn("Gemini API failed, using placeholder");
            return generatePlaceholderImage();
            
        } catch (HttpClientErrorException e) {
            log.error("Gemini API client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return generatePlaceholderImage();
        } catch (Exception e) {
            log.error("Failed to generate image with Gemini: {}", e.getMessage(), e);
            return generatePlaceholderImage();
        }
    }
    
    private Map<String, Object> createImageGenerationRequest(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", new Object[]{part});
        
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("candidateCount", 1);
        generationConfig.put("maxOutputTokens", 1024);
        
        requestBody.put("contents", new Object[]{content});
        requestBody.put("generationConfig", generationConfig);
        
        return requestBody;
    }
    
    private String extractImageFromGeminiResponse(Map<String, Object> responseBody) {
        try {
            // Gemini 응답이 List 형태로 오는 경우를 처리
            Object candidatesObj = responseBody.get("candidates");
            if (candidatesObj instanceof java.util.List) {
                java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) candidatesObj;
                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                    
                    Object partsObj = content.get("parts");
                    if (partsObj instanceof java.util.List) {
                        java.util.List<Map<String, Object>> parts = (java.util.List<Map<String, Object>>) partsObj;
                        
                        for (Map<String, Object> part : parts) {
                            if (part.containsKey("inlineData")) {
                                Map<String, Object> inlineData = (Map<String, Object>) part.get("inlineData");
                                String mimeType = (String) inlineData.get("mimeType");
                                String base64Data = (String) inlineData.get("data");
                                
                                log.info("Received image from Gemini - MIME: {}, Data length: {}", 
                                    mimeType, base64Data != null ? base64Data.length() : 0);
                                return base64Data;
                            }
                        }
                    }
                }
            }
            
            log.warn("No inlineData found in Gemini response");
            return null;
            
        } catch (Exception e) {
            log.error("Failed to extract image from Gemini response: {}", e.getMessage(), e);
            return null;
        }
    }

    private String generatePlaceholderImage() {
        // 간단한 SVG 이미지를 base64로 인코딩하여 반환
        String svgContent = """
            <svg width="400" height="300" xmlns="http://www.w3.org/2000/svg">
              <rect width="100%" height="100%" fill="#e1f5fe"/>
              <circle cx="200" cy="150" r="80" fill="#ffb74d"/>
              <text x="200" y="250" text-anchor="middle" font-family="Arial" font-size="20" fill="#424242">스토리 이미지</text>
            </svg>
            """;
        
        return Base64.getEncoder().encodeToString(svgContent.getBytes());
    }

    private String saveImageFile(String imageData, Long storyId, Integer step) throws IOException {
        // 저장 디렉터리 생성
        Path saveDir = Paths.get(imageSavePath);
        if (!Files.exists(saveDir)) {
            Files.createDirectories(saveDir);
        }

        // 이미지 형식 확인 (base64 데이터에서)
        String fileExtension = determineImageFormat(imageData);
        
        // 파일명 생성
        String filename = String.format("story_%d_step_%d_%s.%s", 
            storyId, step, UUID.randomUUID().toString().substring(0, 8), fileExtension);
        
        Path filePath = saveDir.resolve(filename);

        try {
            // Base64 디코딩 후 파일 저장
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            Files.write(filePath, imageBytes);
            log.info("Image saved to: {}", filePath.toAbsolutePath());
        } catch (IllegalArgumentException e) {
            // Base64 디코딩 실패 시 (SVG 등의 텍스트 이미지)
            Files.write(filePath, imageData.getBytes());
            log.info("Text image saved to: {}", filePath.toAbsolutePath());
        }

        return filename;
    }
    
    private String determineImageFormat(String imageData) {
        try {
            // Base64 데이터의 시작 부분을 확인하여 이미지 형식 판단
            byte[] decodedBytes = Base64.getDecoder().decode(imageData.substring(0, Math.min(100, imageData.length())));
            
            // PNG 시그니처 확인
            if (decodedBytes.length >= 8 && 
                decodedBytes[0] == (byte)0x89 && decodedBytes[1] == 0x50 && 
                decodedBytes[2] == 0x4E && decodedBytes[3] == 0x47) {
                return "png";
            }
            
            // JPEG 시그니처 확인
            if (decodedBytes.length >= 3 && 
                decodedBytes[0] == (byte)0xFF && decodedBytes[1] == (byte)0xD8 && 
                decodedBytes[2] == (byte)0xFF) {
                return "jpg";
            }
            
            // WebP 시그니처 확인
            if (decodedBytes.length >= 12 && 
                decodedBytes[8] == 0x57 && decodedBytes[9] == 0x45 && 
                decodedBytes[10] == 0x42 && decodedBytes[11] == 0x50) {
                return "webp";
            }
            
        } catch (Exception e) {
            // Base64 디코딩 실패 시 SVG로 간주
            if (imageData.contains("<svg")) {
                return "svg";
            }
        }
        
        // 기본값은 png
        return "png";
    }
}