package com.capstone.domain.voice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/voice")
public class VoiceViewController {

    @GetMapping("/record")
    public String showVoiceRecordPage(Model model) {
        List<String> sentences = List.of(
            "우와, 저기 하늘에 떠 있는 무지개 좀 봐! 정말 아름답지 않니?",
            "하지만, 그렇게 깊은 숲 속에 혼자 들어가는 건 너무 위험할 것 같아.",
            "괜찮아, 누구나 처음에는 실수할 수 있어. 우리가 힘을 합치면, 그 어떤 어려운 문제라도 해결할 수 있을 거야.",
            "그래서 말인데, 다음번 모험은 어디로 떠나볼까? 신비로운 바닷속? 아니면 구름 위 궁전?"
        );
        model.addAttribute("sentences", sentences);
        return "voice-record";
    }
}
