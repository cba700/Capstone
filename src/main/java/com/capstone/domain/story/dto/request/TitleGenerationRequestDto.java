package com.capstone.domain.story.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 5. 스토리북 제목 생성 요청 DTO
@Getter
@Builder
public class TitleGenerationRequestDto {
    private String childName;
    private List<String> coreInterests;
    private String selectedJob;
    private String mostFrequentTrait;
}
