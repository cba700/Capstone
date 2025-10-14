package com.capstone.domain.story.controller;

import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    // TODO: API 명세에 따라 /{storyId}/page/{step} 등 메소드 구현 예정

}
