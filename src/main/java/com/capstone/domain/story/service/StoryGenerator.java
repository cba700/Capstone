package com.capstone.domain.story.service;

import com.capstone.domain.job.entity.Job;
import com.capstone.domain.story.entity.StorySelectLog;
import com.capstone.domain.theme.entity.Theme;

import java.util.List;

// AI의 역할을 추상화하는 인터페이스
public interface StoryGenerator {
    // 1부 스토리의 첫 페이지 생성
    String generateFirstPage(Theme theme);

    // 사용자의 선택에 기반해 다음 페이지 생성
    String generateNextPage(List<StorySelectLog> history);

    // 1부 스토리의 결말 및 직업 추천 생성
    String generateEnding(List<StorySelectLog> history);

    // 2부 스토리(직업 체험)의 첫 페이지 생성
    String generateJobStoryFirstPage(Job job);

    // 2부 스토리의 다음 페이지 생성
    String generateJobStoryNextPage(List<StorySelectLog> history);
}
