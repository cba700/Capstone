package com.capstone.domain.story.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = {
	@UniqueConstraint(columnNames = {"story_id", "step"})
})
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

	@Column(length = 500)
	private String imageUrl;

	@Column(nullable = false) //분기유무
	private Boolean hasChoice;

	/**
	 * 이미지 URL 설정 (비동기 이미지 생성 후 업데이트용)
	 */
	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}
}
