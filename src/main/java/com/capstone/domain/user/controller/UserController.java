package com.capstone.domain.user.controller;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.service.ChildService;
import com.capstone.domain.user.dto.UserRegisterRequestDto;
import com.capstone.domain.user.entity.User;
import com.capstone.domain.user.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping
public class UserController {

    private final UserService userService;
    private final ChildService childService;

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute UserRegisterRequestDto requestDto) {
        userService.signUp(requestDto);
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/main")
    public String mainPage(@AuthenticationPrincipal User user, Model model, HttpSession session) {
        // 모든 아이 목록 추가 (기존 로직)
        List<Child> children = childService.findMyChildren(user);
        model.addAttribute("children", children);

        // 세션에서 선택된 아이 정보 조회 및 모델에 추가
        Long selectedChildId = (Long) session.getAttribute("selectedChildId");
        if (selectedChildId != null) {
            childService.findChildById(selectedChildId).ifPresent(selectedChild ->
                model.addAttribute("selectedChild", selectedChild)
            );
        }

        return "main";
    }
}

