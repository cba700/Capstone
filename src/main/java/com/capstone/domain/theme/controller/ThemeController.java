package com.capstone.domain.theme.controller;

import com.capstone.domain.theme.entity.Theme;
import com.capstone.domain.theme.service.ThemeService;
import com.capstone.global.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeService themeService;
    private final ResourceService resourceService;

    @GetMapping("/story/new")
    public String themeSelectPage(@RequestParam Long childId, Model model) {
        List<Theme> themes = themeService.getAllThemes();
        List<String> jobImagePaths = resourceService.getJobImagePaths();
        model.addAttribute("themes", themes);
        model.addAttribute("childId", childId);
        model.addAttribute("jobImagePaths", jobImagePaths);
        return "theme-select";
    }
}
