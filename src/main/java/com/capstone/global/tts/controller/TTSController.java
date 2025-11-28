package com.capstone.global.tts.controller;

import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.service.StoryService;
import com.capstone.global.tts.service.TTSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/tts")
public class TTSController {

    private static final Logger logger = LoggerFactory.getLogger(TTSController.class);

    private final TTSService ttsService;
    private final StoryService storyService;

    @Autowired
    public TTSController(TTSService ttsService, StoryService storyService) {
        this.ttsService = ttsService;
        this.storyService = storyService;
    }

    @GetMapping("/story/{storyId}/page/{pageNumber}")
    public ResponseEntity<byte[]> getStoryPageAudio(
            @PathVariable Long storyId,
            @PathVariable Integer pageNumber) {

        String storyText;
        try {
            StoryPageResponseDto pageDto = storyService.getPage(storyId, pageNumber);
            storyText = pageDto.narration();
            if (!StringUtils.hasText(storyText)) {
                // If narration is empty, try to use the problem text from the choice page.
                storyText = pageDto.choices().isEmpty() ? "" : "어떤 선택을 할까?";
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Could not find story page for Story ID: {}, Page Number: {}", storyId, pageNumber, e);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        logger.info("Request received for TTS audio for Story ID: {}, Page Number: {}. Text: \"{}\"", storyId, pageNumber, storyText);

        try {
            byte[] audioContents = ttsService.synthesizeText(storyText);

            if (audioContents.length == 0) {
                logger.warn("No audio content generated for Story ID: {}, Page Number: {}", storyId, pageNumber);
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("audio/mpeg"));
            headers.setContentLength(audioContents.length);

            return new ResponseEntity<>(audioContents, headers, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error generating TTS audio for Story ID: {}, Page Number: {}", storyId, pageNumber, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
