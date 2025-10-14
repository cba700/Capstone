package com.capstone.domain.story.repository;

import com.capstone.domain.story.entity.StoryChoice;
import com.capstone.domain.story.entity.StoryPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryChoiceRepository extends JpaRepository<StoryChoice, Long> {
    List<StoryChoice> findByPage(StoryPage page);
}
