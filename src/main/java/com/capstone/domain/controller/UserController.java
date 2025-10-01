package com.capstone.domain.controller;

import com.capstone.domain.dto.UserRegisterRequestDto;
import com.capstone.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public String register(UserRegisterRequestDto requestDto, RedirectAttributes redirectAttributes) {
        userService.registerUser(requestDto);
        redirectAttributes.addFlashAttribute("registrationSuccess", true);
        return "redirect:/login";
    }
}
