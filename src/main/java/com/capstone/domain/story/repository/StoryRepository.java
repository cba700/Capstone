package com.capstone.domain.story.repository;

import com.capstone.domain.story.entity.Story;
import com.capstone.domain.story.entity.StoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {
	List<Story> findByChildIdAndStatusInOrderByCompletedAtDesc(Long childId, Collection<StoryStatus> statuses);
}
