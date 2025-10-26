package com.capstone.domain.child.controller;

import com.capstone.domain.child.dto.ChildCreateRequestDto;
import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.service.ChildService;
import com.capstone.domain.user.entity.User;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChildController {

    private final ChildService childService;

    @GetMapping("/children")
    public String showChildListPage(@AuthenticationPrincipal User user, Model model) {
        List<Child> children = childService.findMyChildren(user);
        model.addAttribute("children", children);
        return "child-manage";
    }

    @GetMapping("/children/new")
    public String showCreateChildForm(Model model) {
        model.addAttribute("childCreateRequestDto", new ChildCreateRequestDto());
        return "child-form";
    }

    @PostMapping("/children/create")
    public String createChild(@ModelAttribute ChildCreateRequestDto requestDto, @AuthenticationPrincipal User user) {
        childService.createChild(requestDto, user);
        return "redirect:/children";
    }

    @PostMapping("/children/delete/{id}")
    public String deleteChild(@PathVariable Long id) {
        childService.deleteChild(id);
        return "redirect:/children";
    }

    @PostMapping("/children/select/{id}")
    public String selectChild(@PathVariable Long id, HttpSession session) {
        session.setAttribute("selectedChildId", id);
        return "redirect:/main";
    }
}

