package com.capstone.domain.story.repository;

import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.entity.StoryPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoryPageRepository extends JpaRepository<StoryPage, Long> {
    Optional<StoryPage> findByStoryAndStep(Story story, Integer step);
}
