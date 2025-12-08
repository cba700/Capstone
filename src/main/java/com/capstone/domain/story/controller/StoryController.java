package com.capstone.domain.story.controller;

import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.service.StoryService;
import com.capstone.global.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/story")
public class StoryController {

    private final StoryService storyService;
    private final ResourceService resourceService;

    @PostMapping("/create")
    public String createStory(@RequestParam Long childId, @RequestParam Long themeId) {
        Story story = storyService.createStory(childId, themeId);
        return "redirect:/story/" + story.getId() + "/page/1";
    }

    @GetMapping("/{storyId}/page/{step}")
    public String showPage(@PathVariable Long storyId, @PathVariable Integer step, Model model) {
        StoryPageResponseDto pageDto = storyService.getPage(storyId, step);
        List<String> jobImagePaths = resourceService.getJobImagePaths();
        model.addAttribute("page", pageDto);
        model.addAttribute("jobImagePaths", jobImagePaths);
        return "story-play";
    }

    @PostMapping("/{storyId}/choice")
    public String makeChoice(@PathVariable Long storyId, @RequestParam Long choiceId) {
        int nextStep = storyService.makeChoice(storyId, choiceId);
        return "redirect:/story/" + storyId + "/page/" + nextStep;
    }

    @PostMapping("/start-job")
    public String startJobStory(@RequestParam Long previousStoryId, @RequestParam String jobName, @RequestParam(required = false) String themeWorld) {
        Story newStory = storyService.startJobStory(previousStoryId, jobName, themeWorld);
        return "redirect:/story/" + newStory.getId() + "/page/1";
    }

    /**
     * 특정 페이지가 생성 완료되었는지 확인 (AJAX 폴링용)
     */
    @GetMapping("/{storyId}/page/{step}/status")
    @ResponseBody
    public java.util.Map<String, Boolean> checkPageStatus(@PathVariable Long storyId, @PathVariable Integer step) {
        boolean ready = storyService.isPageReady(storyId, step);
        return java.util.Map.of("ready", ready);
    }
}
