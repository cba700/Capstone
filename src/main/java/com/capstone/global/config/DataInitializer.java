package com.capstone.global.config;

import com.capstone.domain.entity.Theme;
import com.capstone.domain.repository.ThemeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 샘플 테마를 자동으로 채워 개발 중 화면 확인을 돕는다.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final ThemeRepository themeRepository;

    @PostConstruct
    public void initialize() {
        if (themeRepository.count() == 0) {
            themeRepository.save(createTheme("SPACE", "우주 탐험", "반짝이는 별들과 함께 우주의 비밀을 찾는 모험"));
            themeRepository.save(createTheme("DINO", "공룡 친구들", "사라진 공룡 섬에서 새로운 친구들과 협동 모험"));
            themeRepository.save(createTheme("OCEAN", "바닷속 여행", "푸른 바다를 헤엄치며 해양 생물을 돕는 이야기"));
        }
    }

    private Theme createTheme(String code, String title, String description) {
        Theme theme = new Theme();
        theme.setCode(code);
        theme.setTitle(title);
        theme.setDescription(description);
        theme.setActive(true);
        return theme;
    }
}
