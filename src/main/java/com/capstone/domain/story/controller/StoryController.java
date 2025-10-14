package com.capstone.domain.story.controller;

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

    // TODO: API 명세에 따라 choice, complete 등 메소드 구현 예정

}
