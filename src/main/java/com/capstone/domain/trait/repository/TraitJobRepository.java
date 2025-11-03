package com.capstone.domain.trait.repository;

import com.capstone.domain.job.entity.Job;
import com.capstone.domain.trait.entity.Trait;
import com.capstone.domain.trait.entity.TraitJob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TraitJobRepository extends JpaRepository<TraitJob, Long> {
    List<TraitJob> findByTraitIn(List<Trait> traits);
    Optional<TraitJob> findByTraitAndJob(Trait trait, Job job);
}
