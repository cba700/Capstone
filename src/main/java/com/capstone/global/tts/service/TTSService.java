package com.capstone.global.tts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;

@Service
public class TTSService {

    private static final Logger logger = LoggerFactory.getLogger(TTSService.class);

    private final WebClient webClient;
    private final String elevenLabsApiKey;
    private static final String ELEVENLABS_API_BASE_URL = "https://api.elevenlabs.io/v1";
    private static final String DEFAULT_MODEL_ID = "eleven_multilingual_v2"; // Recommended for multilingual use

    public TTSService(WebClient.Builder webClientBuilder, @Value("${elevenlabs.api-key}") String elevenLabsApiKey) {
        this.webClient = webClientBuilder.baseUrl(ELEVENLABS_API_BASE_URL).build();
        this.elevenLabsApiKey = elevenLabsApiKey;
    }

    public byte[] synthesizeText(String text, String voiceId) {
        if (!StringUtils.hasText(text)) {
            logger.warn("Received empty or null text for TTS synthesis.");
            return new byte[0];
        }
        if (!StringUtils.hasText(voiceId)) {
            logger.warn("Received empty or null voiceId for TTS synthesis.");
            return new byte[0];
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);
        requestBody.put("model_id", DEFAULT_MODEL_ID);
        // 아이들에게 동화를 읽어주듯, 목소리 표현력을 높이고 안정성은 낮춤.
        requestBody.put("voice_settings", Map.of("stability", 0.5, "similarity_boost", 0.75));


        try {
            logger.info("Synthesizing text for voiceId: {}", voiceId);

            Mono<byte[]> responseMono = webClient.post()
                    .uri("/text-to-speech/{voiceId}", voiceId)
                    .header("xi-api-key", elevenLabsApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(errorBody ->
                                    Mono.error(new RuntimeException("ElevenLabs Client Error: " + clientResponse.statusCode() + " - " + errorBody))))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            Mono.error(new RuntimeException("ElevenLabs Server Error: " + clientResponse.statusCode())))
                    .bodyToMono(byte[].class);

            byte[] audioContents = responseMono.block(); // Blocking for simplicity

            if (audioContents == null || audioContents.length == 0) {
                logger.warn("ElevenLabs returned empty audio content for voiceId: {}", voiceId);
                return new byte[0];
            }

            return audioContents;
        } catch (Exception e) {
            logger.error("Error during ElevenLabs TTS synthesis for voiceId: {}, text: {}", voiceId, text, e);
            return new byte[0]; // Return empty byte array on error
        }
    }
}
