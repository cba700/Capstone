package com.capstone.domain.story.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class StoryPage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "story_id")
	private Story story;

	@Column(nullable = false)
	private Integer step;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PageType pageType;

	@Column(columnDefinition = "TEXT")
	private String narration;

	@Column(columnDefinition = "TEXT")
	private String imagePrompt;

	@Column(nullable = false) //분기유무
	private Boolean hasChoice;
}
