package com.capstone.domain.trait.entity;

import com.capstone.domain.job.entity.Job;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trait_id", "job_id"})
})
@Getter
public class TraitJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trait_id", nullable = false)
	private Trait trait;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "job_id", nullable = false)
	private Job job;

	@Column(nullable = false)
	private Integer weight;
}
