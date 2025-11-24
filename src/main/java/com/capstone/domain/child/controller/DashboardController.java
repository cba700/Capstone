package com.capstone.domain.child.controller;

import com.capstone.domain.child.dto.ChildDashboardDto;
import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.service.ChildService;
import com.capstone.domain.child.service.DashboardService;
import com.capstone.domain.story.dto.response.ChildAnalysisResponseDto;
import com.capstone.domain.user.entity.User;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ChildService childService;

    @GetMapping
    public String showDashboard(@AuthenticationPrincipal User user, HttpSession session, Model model) {
        // 모든 아이 목록 조회
        List<Child> children = childService.findMyChildren(user);
        model.addAttribute("children", children);

        // 세션에서 선택된 아이 확인
        Long selectedChildId = (Long) session.getAttribute("selectedChildId");

        if (selectedChildId != null) {
            // 선택된 아이의 대시보드 데이터 조회
            try {
                ChildDashboardDto dashboard = dashboardService.getDashboard(selectedChildId);
                model.addAttribute("dashboard", dashboard);
                model.addAttribute("selectedChildId", selectedChildId);
            } catch (IllegalArgumentException e) {
                // 아이를 찾을 수 없는 경우
                model.addAttribute("error", "선택한 아이 정보를 찾을 수 없습니다.");
            }
        }

        return "dashboard";
    }

    @GetMapping("/{childId}")
    public String showDashboardForChild(@PathVariable Long childId,
                                       @AuthenticationPrincipal User user,
                                       HttpSession session,
                                       Model model) {
        // 아이 목록 조회
        List<Child> children = childService.findMyChildren(user);
        model.addAttribute("children", children);

        // 선택한 아이의 대시보드 데이터 조회
        ChildDashboardDto dashboard = dashboardService.getDashboard(childId);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("selectedChildId", childId);

        // 세션에 선택한 아이 저장
        session.setAttribute("selectedChildId", childId);

        return "dashboard";
    }

    @PostMapping("/{childId}/analyze")
    @ResponseBody
    public ChildAnalysisResponseDto analyzeChild(@PathVariable Long childId) {
        return dashboardService.analyzeChild(childId);
    }
}
