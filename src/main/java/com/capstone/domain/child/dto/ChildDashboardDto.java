package com.capstone.domain.child.dto;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.story.dto.StoryBookSummaryDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChildDashboardDto {
    // 아이 기본 정보
    private Long childId;
    private String childName;
    private Integer childAge;
    private String childGender;

    // 통계 정보
    private Integer completedStoryCount;
    private List<TraitStatDto> topTraits;           // 상위 성향 태그 (순위별)
    private List<JobStatDto> selectedJobs;          // 선택한 직업들
    private List<ThemeStatDto> themes;              // 관심사(테마)
    private List<StoryBookSummaryDto> recentStories; // 최근 완성한 스토리

    public static ChildDashboardDto fromChild(Child child) {
        return ChildDashboardDto.builder()
            .childId(child.getId())
            .childName(child.getName())
            .childAge(child.getAge())
            .childGender(child.getGender().name())
            .build();
    }
}
