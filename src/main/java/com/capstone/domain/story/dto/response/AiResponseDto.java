package com.capstone.domain.story.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class AiResponseDto {
    private List<String> narrationSections; // 이야기 진행 서술 문단 (최대 3문장 x N)
    private String problem;   // 새로운 문제 상황 또는 미션
    private List<ChoiceDto> choices; // 선택지 리스트

    public List<String> getNarrationSectionsOrDefault() {
        return narrationSections != null ? narrationSections : Collections.emptyList();
    }

    @Getter
    @Builder
    public static class ChoiceDto {
        private String choiceKey; // A, B, C 등 분기 식별자
        private String choiceText; // 선택지 텍스트 (예: "첫 번째 길로 간다...")
        private List<String> traits; // 성향 태그 리스트 (예: ["#용기", "#리더십"])
        private String jobName; // 결말 단계에서 직업 체험으로 이어질 대상 직업명
        private String themeWorld; // 결말 선택지에서 안내한 테마 월드 이름 (예: "무지개 별빛 공방")

        public List<String> getTraitsOrDefault() {
            return traits != null ? traits : Collections.emptyList();
        }

        public String getThemeWorldOrDefault(String fallback) {
            return themeWorld != null && !themeWorld.isBlank() ? themeWorld : fallback;
        }
    }
}
