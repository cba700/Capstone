package com.capstone.domain.story.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
