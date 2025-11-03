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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JobDataSetupService {

    private static final List<JobSpec> JOB_SPECS = List.of(
            new JobSpec("검사", List.of("분석적/논리적", "꼼꼼함/질서")),
            new JobSpec("기업인", List.of("리더십/도전적")),
            new JobSpec("마케팅 전문가", List.of("리더십/도전적", "창의적/표현적")),
            new JobSpec("변호사", List.of("분석적/논리적", "꼼꼼함/질서")),
            new JobSpec("세무사", List.of("분석적/논리적", "꼼꼼함/질서")),
            new JobSpec("외교관", List.of("리더십/도전적")),
            new JobSpec("은행원", List.of("꼼꼼함/질서")),
            new JobSpec("투자 전문가", List.of("분석적/논리적")),
            new JobSpec("판사", List.of("분석적/논리적", "꼼꼼함/질서")),
            new JobSpec("회계사", List.of("분석적/논리적", "꼼꼼함/질서")),
            new JobSpec("경찰관", List.of("신체활동/모험심", "협동적/공감적")),
            new JobSpec("구급대원(응급구조사)", List.of("신체활동/모험심", "협동적/공감적")),
            new JobSpec("구조대원", List.of("신체활동/모험심", "협동적/공감적")),
            new JobSpec("군인", List.of("신체활동/모험심")),
            new JobSpec("소방관", List.of("신체활동/모험심", "협동적/공감적")),
            new JobSpec("철도기관사", List.of("협동적/공감적", "꼼꼼함/질서")),
            new JobSpec("항공안전요원", List.of("신체활동/모험심", "협동적/공감적")),
            new JobSpec("해양경찰", List.of("신체활동/모험심", "협동적/공감적")),
            new JobSpec("환경미화원", List.of("자연 친화/돌봄", "꼼꼼함/질서")),
            new JobSpec("고고학자", List.of("분석적/논리적", "탐구적")),
            new JobSpec("과학자(실험실 연구원)", List.of("분석적/논리적", "탐구적")),
            new JobSpec("기상학자", List.of("분석적/논리적", "탐구적")),
            new JobSpec("생물학자", List.of("분석적/논리적", "탐구적")),
            new JobSpec("엔지니어", List.of("분석적/논리적", "신체활동/모험심")),
            new JobSpec("천문학자", List.of("분석적/논리적", "탐구적")),
            new JobSpec("프로그래머", List.of("분석적/논리적", "창의적/표현적")),
            new JobSpec("해양학자", List.of("분석적/논리적", "탐구적")),
            new JobSpec("화학자", List.of("분석적/논리적", "탐구적")),
            new JobSpec("교사", List.of("협동적/공감적", "리더십/도전적")),
            new JobSpec("미술치료사", List.of("협동적/공감적")),
            new JobSpec("사서(도서관)", List.of("꼼꼼함/질서")),
            new JobSpec("어린이집 교사", List.of("협동적/공감적")),
            new JobSpec("음악치료사", List.of("협동적/공감적")),
            new JobSpec("청소년 지도사", List.of("협동적/공감적", "리더십/도전적")),
            new JobSpec("큐레이터", List.of("창의적/표현적", "꼼꼼함/질서")),
            new JobSpec("3D 프린팅 전문가", List.of("창의적/표현적", "분석적/논리적")),
            new JobSpec("건설 엔지니어", List.of("신체활동/모험심", "분석적/논리적")),
            new JobSpec("드론 조종사", List.of("신체활동/모험심", "탐구적")),
            new JobSpec("선장, 항해사", List.of("신체활동/모험심", "탐구적")),
            new JobSpec("용접 기술자", List.of("신체활동/모험심")),
            new JobSpec("우주비행사", List.of("신체활동/모험심", "탐구적")),
            new JobSpec("자동차 정비사", List.of("신체활동/모험심", "분석적/논리적")),
            new JobSpec("전기 기술자", List.of("분석적/논리적", "꼼꼼함/질서")),
            new JobSpec("철도 기술자", List.of("신체활동/모험심")),
            new JobSpec("항공기 조종사(파일럿)", List.of("신체활동/모험심")),
            new JobSpec("농부", List.of("자연 친화/돌봄", "꼼꼼함/질서"))
    );

    private final JobRepository jobRepository;
    private final TraitRepository traitRepository;
    private final TraitJobRepository traitJobRepository;

    /**
     * 테스트 및 초기화 용도로 직업/성향/연관 데이터를 저장합니다.
     * 이미 존재하는 데이터는 중복 생성되지 않습니다.
     */
    public void loadSampleJobsAndTraits() {
        JOB_SPECS.forEach(spec -> {
            Job job = ensureJob(spec.jobName());
            spec.traitLabels().stream()
                    .map(this::normalizeTraitLabel)
                    .filter(StringUtils::hasText)
                    .map(this::ensureTrait)
                    .forEach(trait -> linkTraitToJob(trait, job));
        });
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
                        .description(tag.substring(1) + " 성향")
                        .build()));
    }

    private void linkTraitToJob(Trait trait, Job job) {
        if (traitJobRepository.findByTraitAndJob(trait, job).isPresent()) {
            return;
        }
        traitJobRepository.save(TraitJob.builder()
                .trait(trait)
                .job(job)
                .weight(1)
                .build());
    }

    private String normalizeTraitLabel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }

    private record JobSpec(String jobName, List<String> traitLabels) {}
}
