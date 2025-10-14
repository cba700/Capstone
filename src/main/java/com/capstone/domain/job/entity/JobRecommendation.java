package com.capstone.domain.job.entity;

import com.capstone.domain.story.entity.Story;

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
public class JobRecommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 어떤 스토리에서 생성된 추천인지
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "story_id")
	private Story story;

	// 추천 순위 (1~3)
	@Column(nullable = false)
	private Integer rankNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_id")
	private Job job;

	@Column(length = 120)
	private String themeWorld; // “테마 월드” 명칭 (예: '반짝이는 보석 동굴')

	@Column(nullable = false)
	private Boolean selected; // 아이가 실제로 선택한 경우 true

	public void select() {
		this.selected = true;
	}
}
