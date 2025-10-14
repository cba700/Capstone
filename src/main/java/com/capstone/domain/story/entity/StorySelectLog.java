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
public class StorySelectLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 어떤 스토리 내에서의 선택인지
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "story_id")
	private Story story;

	// 어떤 페이지에서 선택이 이루어졌는지
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "page_id")
	private StoryPage page;

	// 실제로 어떤 선택지(A/B/C)를 고른 건지
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "choice_id")
	private StoryChoice choice;

	@Column(nullable = false)
	private Integer step; // 스토리 내 진행 단계

	@Column(columnDefinition = "JSON")
	private String traitDelta; // 이 선택으로 얻은 성향 변화값 (예: {"#호기심": 1, "#창의력": 2})
}
