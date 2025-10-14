package com.capstone.domain.story.service;

import com.capstone.domain.story.entity.StorySelectLog;
import com.capstone.domain.theme.entity.Theme;
import org.springframework.stereotype.Component;

import java.util.List;

// AI 연동 없이 개발 및 테스트를 위한 Mock 구현체
@Component // Spring이 Bean으로 등록하도록 설정
public class MockStoryGenerator implements StoryGenerator {

    @Override
    public String generateFirstPage(Theme theme) {
        // 간단한 JSON 형태의 가짜 데이터 반환
        return "{\"narration\": \"'" + theme.getName() + "'의 세계에 오신 것을 환영합니다. 모험이 곧 시작됩니다.\", \"choices\": [ {\"text\": \"계속 진행한다\"} ]}";
    }

    @Override
    public String generateNextPage(List<StorySelectLog> history) {
        int step = history.size() + 1;
        return "{\"narration\": \"이것은 " + step + "번째 단계의 이야기입니다.\", \"choices\": [ {\"text\": \"다음으로 간다\"}, {\"text\": \"다른 길로 간다\"} ]}";
    }

    @Override
    public String generateEnding(List<StorySelectLog> history) {
        return "{\"narration\": \"모든 모험이 끝났습니다. 당신의 성향에 맞는 직업은 다음과 같습니다.\", \"recommendedJobs\": [\"소방관\", \"디자이너\", \"요리사\"]}";
    }
}
