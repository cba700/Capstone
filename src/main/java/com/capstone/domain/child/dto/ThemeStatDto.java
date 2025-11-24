package com.capstone.domain.child.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ThemeStatDto {
    private String themeName;  // 테마명
    private Integer count;     // 선택 횟수
}
