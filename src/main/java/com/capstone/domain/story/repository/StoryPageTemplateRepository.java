package com.capstone.domain.story.repository;

import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.entity.StoryPageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoryPageTemplateRepository extends JpaRepository<StoryPageTemplate, Long> {
    Optional<StoryPageTemplate> findByStoryAndStepAndIsGeneratedFalse(Story story, int step);
    void deleteByStoryAndStepGreaterThan(Story story, int step);

    /**
     * 특정 스토리의 모든 생성되지 않은 템플릿 조회
     */
    List<StoryPageTemplate> findByStoryAndIsGeneratedFalseOrderByStepAsc(Story story);

    /**
     * 특정 스토리의 특정 스텝 템플릿 조회 (생성 여부 무관)
     */
    Optional<StoryPageTemplate> findByStoryAndStep(Story story, Integer step);

    /**
     * 특정 스토리의 모든 템플릿 조회
     */
    List<StoryPageTemplate> findByStoryOrderByStepAsc(Story story);
}
