package com.capstone.domain.job.service;

import com.capstone.domain.job.entity.Job;
import com.capstone.domain.job.entity.JobRecommendation;
import com.capstone.domain.job.repository.JobRecommendationRepository;
import com.capstone.domain.job.repository.JobRepository;
import com.capstone.domain.story.entity.Story;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JobRecommendationService {

    private final JobRepository jobRepository;
    private final JobRecommendationRepository jobRecommendationRepository;

    public void generateRecommendations(Story story) {
        // TODO: StorySelectLog를 분석하여 성향 점수를 계산하는 로직 구현

        // 현재는 Mock 데이터로 3개의 직업을 무작위 추천
        List<Job> jobs = jobRepository.findAll(); // 실제로는 성향 기반으로 찾아야 함

        for (int i = 0; i < 3; i++) {
            Job job = jobs.get(i);
            JobRecommendation recommendation = JobRecommendation.builder()
                    .story(story)
                    .rankNo(i + 1)
                    .job(job)
                    .themeWorld(job.getName() + "의 나라") // 임시 테마 월드 이름
                    .selected(false)
                    .build();
            jobRecommendationRepository.save(recommendation);
        }
    }
}
