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
public class StoryChoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 어느 페이지의 선택지인지 (페이지 단위 연결)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "page_id")
	private StoryPage page;

	// A, B, C 중 하나 — ENUM을 이용해 명시적 관리
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 1)
	private ChoiceKey choiceKey;

	@Column(nullable = false, length = 120)
	private String label; // 아이에게 보여줄 선택 문구

	@Column(columnDefinition = "JSON")
	private String traitsJson; // 이 선택이 연관된 성향 태그들

	public enum ChoiceKey {
		A, B, C
	}
}
