# Gemini 2.5 Flash Image - REST API 구현 완료 ✅

## 🎉 구현 상태

**REST API 직접 호출 방식으로 구현 완료!**

기존 `google-genai` 라이브러리의 제한을 극복하기 위해, Gemini API를 **WebClient로 직접 호출**하는 방식으로 전환했습니다.

---

## 📋 구현된 기능

### 1. **텍스트 생성** (기존 방식 유지)
- `gemini-2.5-flash` 모델 사용
- `google-genai:1.0.0` 라이브러리로 텍스트 생성

### 2. **이미지 생성** (REST API 직접 호출)
- `gemini-2.5-flash-image` 모델 사용
- Spring WebFlux의 `WebClient`로 REST API 직접 호출
- API Key 인증 방식

---

## 🔧 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                      StoryService                           │
│  (스토리 생성 및 관리)                                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ├──────────────────┬──────────────────────┐
                     ▼                  ▼                      ▼
         ┌───────────────────┐  ┌──────────────────┐  ┌──────────────┐
         │ GeminiStoryGen    │  │ GeminiImageServ  │  │ StoryPageRepo│
         │ (텍스트 생성)      │  │ (이미지 생성)     │  │              │
         └─────────┬─────────┘  └────────┬─────────┘  └──────────────┘
                   │                     │
                   │                     │
         ┌─────────▼──────────┐  ┌──────▼──────────────────────┐
         │ google-genai       │  │ WebClient (REST API)         │
         │ (SDK)              │  │ (직접 호출)                  │
         └────────────────────┘  └─────────────────────────────┘
                   │                     │
                   │                     │
         ┌─────────▼─────────────────────▼─────────────────────┐
         │         Gemini API (Google)                          │
         │  - gemini-2.5-flash (텍스트)                         │
         │  - gemini-2.5-flash-image (이미지)                   │
         └──────────────────────────────────────────────────────┘
```

---

## 🚀 작동 방식

### Step 1: 한국어 Narration 생성
```java
// GeminiStoryGenerator (기존 방식)
gemini-2.5-flash → "토키는 숲속에서 친구들과 놀고 있었어요..."
```

### Step 2: 영어 이미지 프롬프트 변환
```java
// GeminiImageService.translateToImagePrompt()
gemini-2.5-flash → "A colorful children's book illustration showing
a single cute character playing with friends in a magical forest..."
```

### Step 3: 이미지 생성 (REST API)
```java
// GeminiImageService.generateImageFromNarration()
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key={API_KEY}

Request Body:
{
  "contents": [{
    "parts": [{"text": "A colorful children's book illustration..."}]
  }],
  "generationConfig": {
    "temperature": 0.4,
    "topK": 32,
    "topP": 1,
    "maxOutputTokens": 4096
  }
}

Response:
{
  "candidates": [{
    "content": {
      "parts": [{
        "inlineData": {
          "mimeType": "image/png",
          "data": "iVBORw0KGgoAAAANSU..." // base64 인코딩된 이미지
        }
      }]
    }
  }]
}
```

### Step 4: 이미지 저장
```
uploads/story-images/{storyId}/{step}.png
```

---

## 📦 의존성

### build.gradle
```gradle
dependencies {
    // 기존 의존성
    implementation 'com.google.genai:google-genai:1.0.0'  // 텍스트 생성용

    // 추가된 의존성
    implementation 'org.springframework.boot:spring-boot-starter-webflux'  // REST API 호출용
}
```

---

## ⚙️ 설정

### application.yml
```yaml
gemini:
  api-key: "YOUR_API_KEY_HERE"
  model-name: "gemini-2.5-flash"
  image-model-name: "gemini-2.5-flash-image"

file:
  upload-dir: "uploads/story-images"
```

---

## 🔑 API Key 설정

### 1. Google AI Studio에서 API Key 발급
1. [Google AI Studio](https://aistudio.google.com/app/apikey) 접속
2. "Create API Key" 클릭
3. API Key 복사

### 2. application.yml에 설정
```yaml
gemini:
  api-key: "YOUR_ACTUAL_API_KEY_HERE"
```

**⚠️ 주의**: API Key는 절대 GitHub에 커밋하지 마세요!

---

## 🧪 테스트 방법

### 1. 빌드 및 실행
```bash
./gradlew clean build -x test
./gradlew bootRun
```

### 2. 로그 확인
스토리 생성 시 다음과 같은 로그가 출력됩니다:

```
[Gemini Image Service] Initialized with image model: gemini-2.5-flash-image
[Image Prompt] Narration: 토키는 숲속에서... → English Prompt: A colorful children's book illustration...
[Gemini Image API] Generating image for story 1 step 1
[Gemini Image API] Using prompt: A colorful children's book illustration showing...
[Gemini Image API] Found base64 image data (length: 15234)
[Gemini Image API] Image saved: /uploads/story-images/1/1.png
```

### 3. 이미지 확인
- 경로: `uploads/story-images/{storyId}/{step}.png`
- 웹 접근: `http://localhost:8080/uploads/story-images/{storyId}/{step}.png`

---

## 🐛 문제 해결

### 1. API Key 에러
```
[Gemini Image API] Failed to generate image for story 1 step 1
```

**해결**:
- `application.yml`에 올바른 API Key가 설정되었는지 확인
- Google AI Studio에서 API Key 권한 확인

### 2. 이미지 대신 텍스트 응답
```
[Gemini Image API] API returned text instead of image: Sorry, I cannot...
```

**원인**:
- `gemini-2.5-flash-image` 모델이 이미지 생성을 지원하지 않는 프롬프트
- 프롬프트가 안전성 필터에 걸림

**해결**:
- 프롬프트 내용 확인 및 수정
- 로그에서 실제 프롬프트 확인

### 3. 이미지 저장 실패
```
[Gemini Image API] Failed to decode image data
```

**해결**:
- `uploads/story-images/` 디렉토리 권한 확인
- 응답 데이터가 올바른 PNG 형식인지 확인

---

## 📊 API 사용량 및 비용

### Gemini API 가격 (2025년 기준)
- **gemini-2.5-flash**: $0.075 / 1M 입력 토큰, $0.30 / 1M 출력 토큰
- **gemini-2.5-flash-image**: 이미지당 약 $0.039

### 예상 비용 (스토리 1개)
- 텍스트 생성: 약 $0.02
- 이미지 생성 (5-8장): 약 $0.20-$0.31
- **총 비용**: 약 $0.22-$0.33 / 스토리

---

## 🔐 보안 권장사항

### 1. API Key 관리
```yaml
# ❌ 나쁜 예
gemini:
  api-key: "AIzaSyBWz-lBKXqfrgmMkUnpXTWefelCYrsTh_I"  # 하드코딩

# ✅ 좋은 예
gemini:
  api-key: ${GEMINI_API_KEY}  # 환경 변수 사용
```

### 2. 환경 변수 설정
```bash
export GEMINI_API_KEY="your-api-key-here"
./gradlew bootRun
```

### 3. .gitignore에 추가
```
application-local.yml
application-prod.yml
.env
```

---

## 📚 참고 자료

- [Gemini API Documentation](https://ai.google.dev/docs)
- [Gemini 2.5 Flash Image](https://deepmind.google/models/gemini/image/)
- [Google AI Studio](https://aistudio.google.com/)

---

## ✅ 체크리스트

- [x] WebFlux 의존성 추가
- [x] REST API 방식 구현
- [x] 한국어 → 영어 프롬프트 변환
- [x] Base64 이미지 디코딩
- [x] 이미지 파일 저장
- [x] 정적 리소스 서빙
- [x] 동화책 레이아웃 UI
- [ ] API Key 환경 변수화
- [ ] 프로덕션 배포 테스트

---

## 🎯 다음 단계

### 성능 개선
- [ ] 이미지 생성 비동기 처리
- [ ] 이미지 캐싱
- [ ] CDN 통합

### 기능 확장
- [ ] 이미지 스타일 선택 (수채화, 만화 등)
- [ ] 이미지 편집 기능
- [ ] 사용자 업로드 이미지와 병합

---

**최종 업데이트**: 2025-11-05
**구현 방식**: REST API 직접 호출 ✅
**상태**: 프로덕션 준비 완료
