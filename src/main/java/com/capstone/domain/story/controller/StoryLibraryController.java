package com.capstone.domain.story.controller;

import com.capstone.domain.story.dto.StoryBookDetailDto;
import com.capstone.domain.story.dto.StoryBookSummaryDto;
import com.capstone.domain.story.service.StoryService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping
public class StoryLibraryController {

    private final StoryService storyService;

    @GetMapping("/bookshelf")
    public String bookshelf(HttpSession session, Model model) {
        Long selectedChildId = (Long) session.getAttribute("selectedChildId");
        if (selectedChildId == null) {
            return "redirect:/main";
        }
        List<StoryBookSummaryDto> books = storyService.getCompletedBooks(selectedChildId);
        model.addAttribute("books", books);
        return "bookshelf";
    }

    @GetMapping("/story/view")
    public String viewStory(@RequestParam("bookId") Long bookId, HttpSession session, Model model) {
        Long selectedChildId = (Long) session.getAttribute("selectedChildId");
        if (selectedChildId == null) {
            return "redirect:/main";
        }
        try {
            StoryBookDetailDto book = storyService.getStoryBook(bookId, selectedChildId);
            model.addAttribute("book", book);
            return "story-view";
        } catch (IllegalArgumentException e) {
            return "redirect:/bookshelf";
        }
    }
    
    @GetMapping("/story/read/{storyId}/page/{step}")
    public String readStoryPage(@PathVariable Long storyId, @PathVariable Integer step, 
                               HttpSession session, Model model) {
        Long selectedChildId = (Long) session.getAttribute("selectedChildId");
        if (selectedChildId == null) {
            return "redirect:/main";
        }
        try {
            var pageDto = storyService.getPage(storyId, step);
            boolean hasNext = storyService.hasNextPage(storyId, step);
            
            model.addAttribute("page", pageDto);
            model.addAttribute("hasNext", hasNext);
            return "story-read";
        } catch (IllegalArgumentException e) {
            return "redirect:/bookshelf";
        }
    }
}
