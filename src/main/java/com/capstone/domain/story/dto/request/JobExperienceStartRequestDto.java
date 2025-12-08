package com.capstone.domain.story.dto.request;

import lombok.Builder;
import lombok.Getter;

// 4. 직업 체험 시작 요청 DTO
@Getter
@Builder
public class JobExperienceStartRequestDto {
    // 필수 정보
    private String childName;
    private String childGender;
    private String selectedJob;
    private String themeWorld;
    private String coreTrait;

    // 추가 정보
    private String friendName;
    private String pet;
    private String personality;
    private java.util.List<String> interests;
}
