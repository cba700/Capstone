package com.capstone.domain.voice.service;

import com.capstone.domain.user.entity.User;
import com.capstone.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class VoiceCloningService {

    private final UserRepository userRepository;
    private final WebClient webClient;
    private final String elevenLabsApiKey;
    private static final String ELEVENLABS_API_BASE_URL = "https://api.elevenlabs.io/v1";

    public VoiceCloningService(UserRepository userRepository, WebClient.Builder webClientBuilder, @Value("${elevenlabs.api-key}") String elevenLabsApiKey) {
        this.userRepository = userRepository;
        this.webClient = webClientBuilder.baseUrl(ELEVENLABS_API_BASE_URL).build();
        this.elevenLabsApiKey = elevenLabsApiKey;
    }

    @Transactional
    public String cloneVoice(User user, String voiceName, List<MultipartFile> audioFiles) throws IOException {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        if (audioFiles == null || audioFiles.isEmpty() || audioFiles.stream().anyMatch(MultipartFile::isEmpty)) {
            throw new IllegalArgumentException("No valid audio files provided for cloning.");
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("name", voiceName);
        bodyBuilder.part("description", "Voice for user " + user.getUsername());
        bodyBuilder.part("labels", "{\"user_id\": \"" + user.getId() + "\"}");

        for (MultipartFile file : audioFiles) {
            bodyBuilder.part("files", new ByteArrayResource(file.getBytes()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"files\"; filename=\"" + file.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType(file.getContentType()));
        }

        Mono<Map> responseMono = webClient.post()
                .uri("/voices/add")
                .header("xi-api-key", elevenLabsApiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        clientResponse.bodyToMono(String.class).flatMap(errorBody ->
                                Mono.error(new RuntimeException("ElevenLabs Client Error: " + clientResponse.statusCode() + " - " + errorBody))))
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        Mono.error(new RuntimeException("ElevenLabs Server Error: " + clientResponse.statusCode())))
                .bodyToMono(Map.class);

        Map response = responseMono.block();
        if (response == null || !response.containsKey("voice_id")) {
            throw new RuntimeException("ElevenLabs API did not return a voice_id.");
        }

        String voiceId = (String) response.get("voice_id");

        user.setElevenlabsVoiceId(voiceId);
        userRepository.save(user);

        return voiceId;
    }
}
