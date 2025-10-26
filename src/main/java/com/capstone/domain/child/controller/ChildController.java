package com.capstone.domain.child.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ChildController {

    @GetMapping("/children")
    public String showChildManagementPage() {
        return "child-manage";
    }
}

