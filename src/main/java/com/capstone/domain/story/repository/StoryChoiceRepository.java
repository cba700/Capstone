package com.capstone.domain.story.repository;

import com.capstone.domain.story.entity.StoryChoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryChoiceRepository extends JpaRepository<StoryChoice, Long> {
}
