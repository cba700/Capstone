package com.capstone.domain.child.controller;

import com.capstone.domain.child.dto.ChildCreateRequestDto;
import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.service.ChildService;
import com.capstone.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ChildRestController {

    private final ChildService childService;

    @GetMapping("/children")
    public ResponseEntity<List<Child>> getMyChildren(@AuthenticationPrincipal User user) {
        List<Child> children = childService.findMyChildren(user);
        return ResponseEntity.ok(children);
    }

    @PostMapping("/children")
    public ResponseEntity<Child> createChild(@RequestBody ChildCreateRequestDto requestDto, @AuthenticationPrincipal User user) {
        Child createdChild = childService.createChild(requestDto, user);
        return ResponseEntity.ok(createdChild);
    }

    @DeleteMapping("/children/{id}")
    public ResponseEntity<Void> deleteChild(@PathVariable Long id) {
        childService.deleteChild(id);
        return ResponseEntity.ok().build();
    }
}
