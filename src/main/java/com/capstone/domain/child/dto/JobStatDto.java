package com.capstone.domain.child.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobStatDto {
    private String jobName;    // 직업명
    private Integer count;     // 선택 횟수 (같은 직업 여러 번 선택 가능)
}
