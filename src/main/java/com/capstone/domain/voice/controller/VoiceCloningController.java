package com.capstone.domain.voice.controller;

import com.capstone.domain.user.entity.User;
import com.capstone.domain.voice.service.VoiceCloningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/voices")
public class VoiceCloningController {

    private final VoiceCloningService voiceCloningService;

    public VoiceCloningController(VoiceCloningService voiceCloningService) {
        this.voiceCloningService = voiceCloningService;
    }

    @PostMapping("/clone")
    public ResponseEntity<?> cloneVoice(
            @AuthenticationPrincipal User user,
            @RequestParam("name") String voiceName,
            @RequestParam("files") List<MultipartFile> files) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated.");
        }
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("At least one audio file is required.");
        }

        try {
            String voiceId = voiceCloningService.cloneVoice(user, voiceName, files);
            return ResponseEntity.ok(Map.of("voiceId", voiceId));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process files for voice cloning: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
             e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during voice cloning: " + e.getMessage());
        }
    }
}
