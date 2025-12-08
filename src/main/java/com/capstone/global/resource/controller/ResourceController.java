package com.capstone.global.resource.controller;

import com.capstone.global.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/jobs")
    public List<String> getJobImageUrls() {
        return resourceService.getJobImagePaths();
    }
}
