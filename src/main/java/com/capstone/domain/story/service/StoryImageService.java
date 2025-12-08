package com.capstone.domain.story.service;

import com.capstone.domain.story.entity.StoryStatus;
import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryImageService {

    private final Client geminiClient;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.image-model-name:gemini-1.5-flash-001}")
    private String imageModelName;

    @Value("${story.image.save-path:./story-images}")
    private String imageSavePath;

    @Value("${story.image.base-url:/story-images}")
    private String imageBaseUrl;

    public String generateAndSaveImage(String imagePrompt, Long storyId, Integer step, List<String> referenceImagePaths, StoryStatus status) {
        if (!StringUtils.hasText(imagePrompt)) {
            log.warn("Empty image prompt for story {} step {}", storyId, step);
            return null;
        }

        try {
            String enhancedPrompt = buildEnhancedPrompt(imagePrompt, status);
            log.info("Generating image for story {} step {} with prompt: {}", storyId, step, enhancedPrompt);

            String imageData = generateImageWithGemini(enhancedPrompt, referenceImagePaths);

            if (imageData == null) {
                imageData = generatePlaceholderImage();
            }

            if (!StringUtils.hasText(imageData)) {
                log.warn("Failed to generate image for story {} step {}", storyId, step);
                return null;
            }

            String filename = saveImageFile(imageData, storyId, step);
            String imageUrl = imageBaseUrl + "/" + filename;
            log.info("Image saved successfully: {}", imageUrl);

            return imageUrl;
        } catch (Exception e) {
            log.error("Failed to generate and save image for story {} step {}: {}", storyId, step, e.getMessage(), e);
            return null;
        }
    }

    private String buildEnhancedPrompt(String basePrompt, StoryStatus status) {
        String sceneInstruction = "Your primary and most important task is to accurately illustrate the scene described in the 'Scene description'. All other instructions are secondary to this. Scene description: " + basePrompt;

        String styleInstruction;
        if (status == StoryStatus.JOB_STARTED || status == StoryStatus.IN_JOB_PROGRESS) {
            // Part 2 (with job)
            styleInstruction = "For the style, you MUST combine three reference images: 1. The main character's appearance, 2. The theme's art style, and 3. The job's visual elements. Draw the character exactly as shown.";
        } else {
            // Part 1 (no job)
            styleInstruction = "For the style, you MUST combine two reference images: 1. The main character's appearance, and 2. The theme's art style. Draw the character exactly as shown.";
        }

        String negativePrompt = "Critical rule: Unconditionally DO NOT draw any animals. The name '토리' (Tori) is a proper name, not an animal. Also, DO NOT include any text, speech bubbles, or captions in the image, whether in English or Korean.";
        String finalStyleRequirements = "Final style requirements: children's book illustration, cute, colorful, friendly cartoon, bright colors, no scary elements, high quality.";

        return String.join(" ", sceneInstruction, styleInstruction, negativePrompt, finalStyleRequirements);
    }

    private String generateImageWithGemini(String prompt, List<String> referenceImagePaths) {
        try {
            log.info("Requesting image generation from Gemini with prompt: {}", prompt);

            String apiUrl = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                    imageModelName, geminiApiKey);

            Map<String, Object> requestBody = createImageGenerationRequest(prompt, referenceImagePaths);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
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
        }
        catch (Exception e) {
            log.error("Failed to generate image with Gemini: {}", e.getMessage(), e);
            return generatePlaceholderImage();
        }
    }

    private Map<String, Object> createImageGenerationRequest(String prompt, List<String> referenceImagePaths) {
        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);
        parts.add(textPart);

        if (!CollectionUtils.isEmpty(referenceImagePaths)) {
            for (String imagePath : referenceImagePaths) {
                try {
                    byte[] imageBytes = readImageToBytes(imagePath);
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    String mimeType = getMimeType(imagePath);

                    Map<String, Object> imagePart = new HashMap<>();
                    Map<String, Object> inlineData = new HashMap<>();
                    inlineData.put("mimeType", mimeType);
                    inlineData.put("data", base64Image);
                    imagePart.put("inlineData", inlineData);
                    parts.add(imagePart);
                    log.info("Added reference image to request: {}", imagePath);
                } catch (IOException e) {
                    log.error("Failed to read or encode reference image: {}", imagePath, e);
                }
            }
        }

        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("candidateCount", 1);
        generationConfig.put("maxOutputTokens", 2048);

        requestBody.put("contents", new Object[]{content});
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private byte[] readImageToBytes(String imagePath) throws IOException {
        log.debug("Reading image from path: {}", imagePath);
        ClassPathResource resource = new ClassPathResource(imagePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private String getMimeType(String filename) {
        if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".webp")) {
            return "image/webp";
        } else {
            return "application/octet-stream";
        }
    }

    private String extractImageFromGeminiResponse(Map<String, Object> responseBody) {
        try {
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
        Path saveDir = Paths.get(imageSavePath);
        if (!Files.exists(saveDir)) {
            Files.createDirectories(saveDir);
        }

        String fileExtension = determineImageFormat(imageData);
        String filename = String.format("story_%d_step_%d_%s.%s",
                storyId, step, UUID.randomUUID().toString().substring(0, 8), fileExtension);
        Path filePath = saveDir.resolve(filename);

        try {
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            Files.write(filePath, imageBytes);
            log.info("Image saved to: {}", filePath.toAbsolutePath());
        } catch (IllegalArgumentException e) {
            Files.write(filePath, imageData.getBytes());
            log.info("Text image saved to: {}", filePath.toAbsolutePath());
        }

        return filename;
    }

    private String determineImageFormat(String imageData) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(imageData.substring(0, Math.min(100, imageData.length())));

            if (decodedBytes.length >= 8 &&
                    decodedBytes[0] == (byte) 0x89 && decodedBytes[1] == 0x50 &&
                    decodedBytes[2] == (byte) 0x4E && decodedBytes[3] == (byte) 0x47) {
                return "png";
            }

            if (decodedBytes.length >= 3 &&
                    decodedBytes[0] == (byte) 0xFF && decodedBytes[1] == (byte) 0xD8 &&
                    decodedBytes[2] == (byte) 0xFF) {
                return "jpg";
            }

            if (decodedBytes.length >= 12 &&
                    decodedBytes[8] == (byte) 0x57 && decodedBytes[9] == (byte) 0x45 &&
                    decodedBytes[10] == (byte) 0x42 && decodedBytes[11] == (byte) 0x50) {
                return "webp";
            }

        } catch (Exception e) {
            if (imageData.contains("<svg")) {
                return "svg";
            }
        }
        return "png";
    }
}