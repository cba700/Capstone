package com.capstone.global.tts.service;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TTSService {

    private static final Logger logger = LoggerFactory.getLogger(TTSService.class);

    private final TextToSpeechClient textToSpeechClient;

    @Autowired
    public TTSService(TextToSpeechClient textToSpeechClient) {
        this.textToSpeechClient = textToSpeechClient;
    }

    public byte[] synthesizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Received empty or null text for TTS synthesis.");
            return new byte[0]; // Return empty byte array for empty text
        }

        // Set the text input to be synthesized
        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

        // Build the voice request, select the language code ("en-US") and the SSML
        // voice gender ("FEMALE")
        // For Korean, use "ko-KR"
        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode("ko-KR")
                .setSsmlGender(SsmlVoiceGender.FEMALE) // Or MALE, NEUTRAL
                .build();

        // Select the type of audio file you want returned
        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3) // Or LINEAR16, OGG_OPUS
                .build();

        // Perform the text-to-speech request on the text input with the selected voice parameters and audio file type
        try {
            com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse response =
                    textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

            // Get the audio contents from the response
            ByteString audioContents = response.getAudioContent();
            return audioContents.toByteArray();
        } catch (Exception e) {
            logger.error("Error during TTS synthesis for text: {}", text, e);
            return new byte[0]; // Return empty byte array on error
        }
    }
}
