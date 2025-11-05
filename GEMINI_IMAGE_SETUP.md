# Gemini 2.5 Flash Image 설정 가이드

## ⚠️ 중요 사항

현재 프로젝트에서 사용 중인 `google-genai:1.0.0` 라이브러리는 **이미지 생성 기능이 제한적**일 수 있습니다.

## 📋 설정 방법

### 옵션 1: REST API 직접 호출 (권장)

`google-genai` 라이브러리 대신 Vertex AI REST API를 직접 호출하는 방식입니다.

#### 1. 의존성 추가

`build.gradle`에 HTTP 클라이언트 추가:

```gradle
dependencies {
    // ... 기존 의존성들 ...

    // Google Cloud Vertex AI
    implementation 'com.google.cloud:google-cloud-aiplatform:3.35.0'

    // 또는 REST API 직접 호출
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

#### 2. REST API 방식 구현

`GeminiImageService.java`를 REST API 방식으로 변경:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiImageService {

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1beta")
            .build();
    }

    public String generateImageFromNarration(String narration, Child child, Long storyId, Integer step) {
        try {
            String prompt = translateToImagePrompt(narration, child);

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                    "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                    "response_mime_type", "image/png"
                )
            );

            String response = webClient.post()
                .uri("/models/gemini-2.5-flash-image:generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            // 응답에서 이미지 추출 및 저장
            byte[] imageData = extractImageFromJson(response);
            return saveImage(imageData, storyId, step);

        } catch (Exception e) {
            log.error("Failed to generate image", e);
            return null;
        }
    }
}
```

---

### 옵션 2: google-genai 라이브러리 업그레이드

#### 1. `build.gradle` 수정

```gradle
dependencies {
    // 기존 버전
    // implementation 'com.google.genai:google-genai:1.0.0'

    // 최신 버전으로 업그레이드 (버전은 확인 필요)
    implementation 'com.google.genai:google-genai:2.0.0' // 또는 최신 버전
}
```

#### 2. Gradle 의존성 업데이트

```bash
./gradlew clean build --refresh-dependencies
```

---

### 옵션 3: Google Cloud Vision AI 사용

Gemini 2.5 Flash Image 대신 Google Cloud Vision AI의 Imagen 모델을 사용:

#### 1. 의존성 추가

```gradle
dependencies {
    implementation 'com.google.cloud:google-cloud-aiplatform:3.35.0'
}
```

#### 2. Vertex AI 인증 설정

```bash
# Google Cloud 서비스 계정 키 생성
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
```

#### 3. Imagen API 사용

```java
@Service
public class GeminiImageService {

    public String generateImage(String prompt, Long storyId, Integer step) {
        PredictionServiceSettings settings = PredictionServiceSettings.newBuilder()
            .setEndpoint("us-central1-aiplatform.googleapis.com:443")
            .build();

        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {
            EndpointName endpoint = EndpointName.of(projectId, "us-central1", "imagen-3.0");

            // Imagen API 호출
            // ...
        }
    }
}
```

---

## 🔧 현재 구현 상태

### 작동하는 기능
- ✅ 한국어 narration → 영어 이미지 프롬프트 변환
- ✅ 아이 이름을 일반적인 묘사로 변환
- ✅ 이미지 저장 및 파일 서빙 설정
- ✅ 동화책 레이아웃 UI

### 확인 필요한 기능
- ⚠️ Gemini 2.5 Flash Image API 호출 (라이브러리 버전 제약)
- ⚠️ 이미지 응답 데이터 추출

---

## 🧪 테스트 방법

### 1. 로그 확인

애플리케이션 실행 후 스토리 생성 시 로그를 확인:

```
[Gemini Image] Generating image for story 1 step 1
[Gemini Image] Using prompt: A colorful children's book illustration showing...
```

### 2. 에러 확인

이미지 생성 실패 시:

```
[Gemini Image] Failed to generate image for story 1 step 1
[Gemini Image] 이미지 생성에 실패했습니다. google-genai 라이브러리 버전이나 Vertex AI 설정을 확인해주세요.
```

**중요**: 이미지 생성 실패해도 스토리는 정상적으로 진행됩니다.

---

## 📚 참고 자료

- [Vertex AI Imagen API 문서](https://cloud.google.com/vertex-ai/docs/generative-ai/image/overview)
- [Gemini API 문서](https://ai.google.dev/docs)
- [Google GenAI SDK GitHub](https://github.com/googleapis/google-genai-java)

---

## 🆘 문제 해결

### 컴파일 에러: "package com.google.genai.models does not exist"
- ✅ 해결됨: `GenerateContentConfig` import 제거

### 런타임 에러: "No image data in response"
- 원인: API가 이미지 대신 텍스트를 반환
- 해결: REST API 직접 호출 방식으로 변경 (옵션 1 참조)

### 인증 에러: "Unauthenticated"
- Vertex AI 인증 설정 확인
- `application.yml`에 `project-id` 설정 확인
- 환경 변수 `GOOGLE_APPLICATION_CREDENTIALS` 확인

---

## 💡 추천 구현 순서

1. **단계 1**: 현재 코드로 빌드 및 실행 테스트
2. **단계 2**: 로그 확인하여 API 호출 여부 확인
3. **단계 3**: 이미지 생성 실패 시 → **옵션 1 (REST API)** 구현
4. **단계 4**: 성공 시 → UI 및 사용자 경험 개선

---

## ✅ 체크리스트

- [ ] `google-genai` 라이브러리 버전 확인
- [ ] Vertex AI API 활성화
- [ ] Google Cloud 프로젝트 ID 설정
- [ ] 인증 정보 설정 (API Key 또는 Service Account)
- [ ] 이미지 저장 디렉토리 권한 확인
- [ ] 로그 레벨을 DEBUG로 변경하여 상세 로그 확인

---

**작성일**: 2025-11-05
**작성자**: Claude
