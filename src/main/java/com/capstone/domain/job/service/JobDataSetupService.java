package com.capstone.domain.job.service;

import com.capstone.domain.job.entity.Job;
import com.capstone.domain.job.repository.JobRepository;
import com.capstone.domain.trait.entity.Trait;
import com.capstone.domain.trait.entity.TraitJob;
import com.capstone.domain.trait.repository.TraitJobRepository;
import com.capstone.domain.trait.repository.TraitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class JobDataSetupService {

    private final JobRepository jobRepository;
    private final TraitRepository traitRepository;
    private final TraitJobRepository traitJobRepository;

    /**
     * CSV 파일을 읽어 직업/성향 데이터를 DB에 저장합니다.
     * 형식: job,trait
     */
    public void loadSampleJobsAndTraits() {
        List<JobSpec> jobSpecs = loadFromCsv("/data/job_trait_2.csv");

        jobSpecs.forEach(spec -> {
            Job job = ensureJob(spec.jobName());
            String normalizedTag = normalizeTraitLabel(spec.traitLabel());
            if (!StringUtils.hasText(normalizedTag)) {
                return;
            }
            Trait trait = ensureTrait(normalizedTag);
            linkTraitToJob(trait, job);
        });
    }

    /**
     * CSV 파일에서 job-trait 쌍을 읽어옵니다.
     */
    private List<JobSpec> loadFromCsv(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {

            return reader.lines()
                .skip(1) // header 제거
                .map(line -> line.split(","))
                .filter(parts -> parts.length >= 2)
                .map(parts -> new JobSpec(parts[0].trim(), parts[1].trim()))
                .filter(spec -> StringUtils.hasText(spec.jobName()) && StringUtils.hasText(spec.traitLabel()))
                .collect(Collectors.toList());
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("❌ CSV 파일을 읽는 데 실패했습니다: " + resourcePath, e);
        }
    }

    private Job ensureJob(String jobName) {
        return jobRepository.findByName(jobName)
            .orElseGet(() -> jobRepository.save(Job.builder()
                .name(jobName)
                .description(null)
                .build()));
    }

    private Trait ensureTrait(String tag) {
        return traitRepository.findByTag(tag)
            .orElseGet(() -> traitRepository.save(Trait.builder()
                .tag(tag)
                .description(tag + " 성향")
                .build()));
    }

    private void linkTraitToJob(Trait trait, Job job) {
        if (traitJobRepository.findByTraitAndJob(trait, job).isPresent()) return;

        traitJobRepository.save(TraitJob.builder()
            .trait(trait)
            .job(job)
            .weight(1)
            .build());
    }

    private String normalizeTraitLabel(String rawTag) {
        if (!StringUtils.hasText(rawTag)) {
            return null;
        }
        String trimmed = rawTag.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        return trimmed.replaceAll("\\s+", "");
    }

    private record JobSpec(String jobName, String traitLabel) {}
}
