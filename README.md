# **서산책 : AI 여행 플래너 & 지역화폐 연동 플랫폼(07/24~)**

서산책은 **AI 기반 여행 추천**과 **지역 경제 활성화**를 목표로 한 웹 플랫폼입니다.

**예산, 일정, 관심사**를 입력하면 AI가 최적의 여행 코스를 제안하며, **지역화폐 가맹점 정보**와 **실시간 할인 혜택**을 제공합니다.

---

## ✨ **주요 기능**

- 🤖 **AI 여행 플래너**: 관심사(힐링, 먹방, 역사, 인생샷 등), 예산, 일정 기반 최적의 코스 추천
- 🗺️ **코스 시각화**: 카카오 지도 API 기반, 위치별 이동 경로 제공
- 💰 **지역화폐 연동**: 추천 코스 내 **가맹점 태그**, 실시간 할인 정보 제공
- 🌤️ **실시간 여행 보정**: 날씨·혼잡도·SNS 트렌드 기반 루트 자동 조정
- 🗣️ **커뮤니티 (확장 예정)**: 여행 후기, 꿀팁 공유 가능
- 👤 **사용자 인증**: 카카오 OAuth2 로그인 + JWT 기반 인증

---

## 🛠️ **기술 스택**

| 영역 | 기술 |
| --- | --- |
| **Frontend** | React, Tailwind CSS |
| **Backend** | Node.js (Express), Python (AI 추천 로직) |
| **Database** | MySQL |
| **AI** | OpenAI API |
| **지도 API** | 카카오 지도 API |
| **인증** | JWT, OAuth 2.0 (Kakao) |
| **외부 API** | 공공데이터포털 제로페이 가맹점 API / 행안부 “지역사랑상품권” 정책 데이터 |
| **기타** | Docker, Swagger (API 문서화) |

---

## 🚀 **시작 가이드**

### 1. **요구 사항**

- SpringBoot 3.xx
- MySQL 8.x

---

### 2. **프로젝트 실행**

```bash

# 1. 프로젝트 클론
git clone https://github.com/{your-github-username}/SeosanChaek.git
cd SeosanChaek

# 2. 백엔드 빌드 & 실행
cd backend
npm install
npm run start

# 3. 프론트엔드 실행
cd frontend
npm install
npm run dev

```

---

### 3. **환경 변수 설정**

`.env` 파일 생성 후 아래 항목 입력:

```yaml

# Database
DB_HOST=localhost
DB_PORT=3306
DB_USER={DB_USER}
DB_PASSWORD={DB_PASSWORD}
DB_NAME=seosanchaek

# Kakao Login
KAKAO_CLIENT_ID={KAKAO_CLIENT_ID}
KAKAO_CLIENT_SECRET={KAKAO_CLIENT_SECRET}
REDIRECT_URI=http://localhost:8080/login/oauth2/code/kakao

# JWT
JWT_SECRET={JWT_SECRET_KEY}

# API Keys
TOUR_API_KEY={TOUR_API_KEY}
OPENAI_API_KEY={OPENAI_API_KEY}

```

---

## 📄 **API 문서**

- Swagger UI: `http://localhost:8080/api-docs`

---

## 📁 **주요 패키지 구조**

```

seosanchaek
├── ai              # AI 여행 코스 추천 로직
├── auth            # 인증 (JWT, OAuth2)
├── course          # 여행 코스 관리
├── map             # 지도 API 연동
├── localpay        # 지역화폐 기능
├── community       # 커뮤니티 기능 (확장 예정)
├── common          # 공통 설정 및 예외 처리
└── user            # 사용자 관리

```
