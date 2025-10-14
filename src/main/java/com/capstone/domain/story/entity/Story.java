package com.capstone.domain.story.entity;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.job.entity.Job;
import com.capstone.domain.theme.entity.Theme;

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
public class Story {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "child_id")
	private Child child;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "theme_id")
	private Theme theme;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StoryStatus status;

	@Column(nullable = false)
	private Integer currentStep;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "selected_job_id")
	private Job selectedJob;

	@Column(length = 120)
	private String title;

}
