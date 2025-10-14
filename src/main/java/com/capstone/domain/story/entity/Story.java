package com.capstone.domain.story.entity;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.job.entity.Job;
import com.capstone.domain.theme.entitiy.Theme;

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

	public void advanceStep() {
		this.currentStep++;
	}

	public void updateStatus(StoryStatus newStatus) {
		this.status = newStatus;
	}

	public void assignSelectedJob(Job job) {
		this.selectedJob = job;
	}

	public void updateTitle(String title) {
		this.title = title;
	}
}
