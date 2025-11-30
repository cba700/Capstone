package com.capstone.domain.story.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 아직 생성되지 않은 페이지의 템플릿 정보를 저장
 * 백그라운드에서 페이지 생성 시 사용
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryPageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;

    @Column(nullable = false)
    private Integer step;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(columnDefinition = "TEXT")
    private String imagePrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PageType pageType;

    @Column(nullable = false)
    private Boolean hasChoice;

    /**
     * 선택지 정보를 JSON 형태로 저장
     * 선택지가 있는 페이지의 경우 AiResponseDto.ChoiceDto 리스트를 JSON으로 직렬화
     */
    @Column(columnDefinition = "TEXT")
    private String choicesJson;

    /**
     * 실제 페이지 생성 완료 여부
     */
    @Column(nullable = false, name = "is_generated")
    @Builder.Default
    private Boolean isGenerated = false;
}
