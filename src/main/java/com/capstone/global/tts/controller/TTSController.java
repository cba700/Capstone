package com.capstone.global.tts.controller;

import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.service.StoryService;
import com.capstone.domain.user.entity.User;
import com.capstone.domain.user.repository.UserRepository;
import com.capstone.global.tts.service.TTSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tts")
public class TTSController {

    private static final Logger logger = LoggerFactory.getLogger(TTSController.class);
    private static final String DEFAULT_ELEVENLABS_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"; // ElevenLabs Default Voice: "Rachel"

    private final TTSService ttsService;
    private final StoryService storyService;
    private final UserRepository userRepository;

    public TTSController(TTSService ttsService, StoryService storyService, UserRepository userRepository) {
        this.ttsService = ttsService;
        this.storyService = storyService;
        this.userRepository = userRepository;
    }

    @GetMapping("/story/{storyId}/page/{pageNumber}")
    public ResponseEntity<byte[]> getStoryPageAudio(
            @AuthenticationPrincipal User authenticatedUser,
            @PathVariable Long storyId,
            @PathVariable Integer pageNumber) {

        String storyText;
        String voiceId;

        try {
            // 1. Get Story Page text
            StoryPageResponseDto pageDto = storyService.getPage(storyId, pageNumber);
            storyText = pageDto.narration();
            if (!StringUtils.hasText(storyText)) {
                storyText = pageDto.choices().isEmpty() ? "" : "어떤 선택을 할까?";
            }

            // 2. Get Voice ID from the authenticated user by fetching the latest user data
            if (authenticatedUser != null) {
                User freshUser = userRepository.findByEmail(authenticatedUser.getUsername())
                        .orElse(authenticatedUser); // Fallback to session user if not found
                
                if (StringUtils.hasText(freshUser.getElevenlabsVoiceId())) {
                    voiceId = freshUser.getElevenlabsVoiceId();
                    logger.info("Using custom voiceId: {} for user: {}", voiceId, freshUser.getUsername());
                } else {
                    voiceId = DEFAULT_ELEVENLABS_VOICE_ID;
                    logger.info("Custom voiceId not found for user: {}. Using default voiceId: {}", freshUser.getUsername(), voiceId);
                }
            } else {
                 voiceId = DEFAULT_ELEVENLABS_VOICE_ID;
                 logger.info("User not authenticated. Using default voiceId: {}", voiceId);
            }

        } catch (IllegalArgumentException e) {
            logger.warn("Could not find story or page for Story ID: {}, Page Number: {}", storyId, pageNumber, e);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (!StringUtils.hasText(storyText)) {
             logger.info("No text to synthesize for Story ID: {}, Page Number: {}", storyId, pageNumber);
             return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        logger.info("Request received for TTS audio for Story ID: {}, Page Number: {}. Voice ID: {}", storyId, pageNumber, voiceId);

        try {
            // 3. Synthesize text with the chosen voiceId
            byte[] audioContents = ttsService.synthesizeText(storyText, voiceId);

            if (audioContents.length == 0) {
                logger.warn("No audio content generated for Story ID: {}", storyId);
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("audio/mpeg"));
            headers.setContentLength(audioContents.length);

            return new ResponseEntity<>(audioContents, headers, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error generating TTS audio for Story ID: {}", storyId, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
