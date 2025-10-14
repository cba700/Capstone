package com.capstone.domain.user.controller;

import com.capstone.domain.user.dto.UserRegisterRequestDto;
import com.capstone.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping
public class UserController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute UserRegisterRequestDto requestDto) {
        userService.signUp(requestDto);
        return "redirect:/login";
    }

    @GetMapping("/main")
    public String mainPage() {
        return "main";
    }
}
