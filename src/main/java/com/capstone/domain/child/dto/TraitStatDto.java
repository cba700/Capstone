package com.capstone.domain.child.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TraitStatDto {
    private String traitName;  // 성향 태그명 (예: #용기, #호기심)
    private Integer count;     // 선택 횟수
    private Integer rank;      // 순위 (1위, 2위, 3위...)
}
