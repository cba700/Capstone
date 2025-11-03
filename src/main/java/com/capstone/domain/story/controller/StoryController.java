package com.capstone.domain.story.controller;

import com.capstone.domain.story.dto.EndingResponseDto;
import com.capstone.domain.story.dto.StoryPageResponseDto;
import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.capstone.domain.story.dto.response.TitleResponseDto;

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
        if (nextStep < 0) {
            return "redirect:/main";
        }
        return "redirect:/story/" + storyId + "/page/" + nextStep;
    }

    @PostMapping("/start-job")
    public String startJobStory(@RequestParam Long previousStoryId, @RequestParam String jobName) {
        Story newStory = storyService.startJobStory(previousStoryId, jobName);
        return "redirect:/story/" + newStory.getId() + "/page/1";
    }

   /* @GetMapping("/{storyId}/title")
    @ResponseBody
    public TitleResponseDto createTitle(@PathVariable Long storyId) {
        return storyService.createTitle(storyId);
    }*/
}
