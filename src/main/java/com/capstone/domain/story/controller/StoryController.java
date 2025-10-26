package com.capstone.domain.story.controller;

import com.capstone.domain.story.dto.EndingResponseDto;
import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/story")
public class StoryController {

    private final StoryService storyService;

    @PostMapping("/create")
    public String createStory(@RequestParam Long childId, @RequestParam Long themeId) {
        Story story = storyService.createStory(childId, themeId);
        return "redirect:/story/" + story.getId() + "/page/1";
    }

    @GetMapping("/{storyId}/page/{step}")
    public String showPage(@PathVariable Long storyId, @PathVariable Integer step, Model model) {
        StoryPageResponseDto pageDto = storyService.getPage(storyId, step);
        model.addAttribute("page", pageDto);
        return "story-play";
    }

    @PostMapping("/{storyId}/choice")
    public String makeChoice(@PathVariable Long storyId, @RequestParam Long choiceId) {
        int nextStep = storyService.makeChoice(storyId, choiceId);
        return "redirect:/story/" + storyId + "/page/" + nextStep;
    }

    @PostMapping("/{storyId}/complete")
    public String completeStory(@PathVariable Long storyId, @RequestParam Long choiceId) {
        storyService.completeStory(storyId, choiceId);
        return "redirect:/story/" + storyId + "/ending";
    }

    @GetMapping("/{storyId}/ending")
    public String showEnding(@PathVariable Long storyId, Model model) {
        EndingResponseDto endingDto = storyService.getEnding(storyId);
        model.addAttribute("ending", endingDto);
        return "story-ending"; // story-ending.html 뷰 반환
    }

    @PostMapping("/{storyId}/job-select")
    public String selectJob(@PathVariable Long storyId, @RequestParam Long recommendationId) {
        Story newStory = storyService.startJobStory(recommendationId);
        // 2부 스토리의 진행 페이지(job-play.html)로 리다이렉트
        return "redirect:/story/" + newStory.getId() + "/job";
    }

    @GetMapping("/{storyId}/job")
    public String showJobPage(@PathVariable Long storyId, Model model) {
        // 2부 스토리는 step이 URL에 없고, 항상 현재 step을 찾아서 보여줌
        Story story = storyService.getStory(storyId); // StoryService에 getStory 메소드 추가 필요
        StoryPageResponseDto pageDto = storyService.getPage(storyId, story.getCurrentStep());
        model.addAttribute("page", pageDto);
        return "job-play";
    }

    @PostMapping("/{storyId}/job/choice")
    public String makeJobChoice(@PathVariable Long storyId, @RequestParam Long choiceId) {
        storyService.makeJobChoice(storyId, choiceId);
        // 선택 후에는 같은 job 페이지로 리다이렉트하여 다음 내용을 표시
        return "redirect:/story/" + storyId + "/job";
    }
}
