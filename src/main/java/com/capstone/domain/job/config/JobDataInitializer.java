package com.capstone.domain.job.config;

import com.capstone.domain.job.service.JobDataSetupService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 시 직업-성향 샘플 데이터를 로딩합니다.
 * 중복 데이터는 {@link JobDataSetupService} 내부에서 자동으로 방지됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"local", "dev"})
public class JobDataInitializer {

    private final JobDataSetupService jobDataSetupService;

    @PostConstruct
    public void loadSampleData() {
        log.info("[JobDataInitializer] Loading sample job/trait mappings from CSV...");
        jobDataSetupService.loadSampleJobsAndTraits();
    }
}
