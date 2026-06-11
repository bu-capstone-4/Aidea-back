<div align="center">

# Aidea

**아이디어 한 줄로 시작하는 AI 기획 & 협업 플랫폼**

[![Frontend Repo](https://img.shields.io/badge/Frontend-Aidea--front-blue?logo=react)](https://github.com/bu-capstone-4/Aidea-front)
[![Backend Repo](https://img.shields.io/badge/Backend-Aidea--back-success?logo=spring)](https://github.com/bu-capstone-4/Aidea-back)

</div>

<img width="800" alt="readme_main" src="https://github.com/user-attachments/assets/3651cacd-aead-4e94-b80a-e8d74daa6965" />

<br>

---

<br>

## 1. 프로젝트 소개

**Aidea**는 아주 간단한 아이디어 한 줄만 입력하면, AI가 해당 아이디어를 구체화하기 위한 질문을 먼저 던지고 사용자의 답변을 바탕으로 서비스 기획에 필요한 문서(유저 시나리오, 기능 명세, API 설계 등)를 자동으로 생성해주는 서비스입니다.

문서 생성에서 끝나지 않고, Notion과 같은 실시간 협업 에디터를 통해 팀원들과 함께 문서를 다듬을 수 있으며, 완성된 기획 문서를 기반으로 AI가 할 일 목록(Backlog)을 자동으로 생성해 프로젝트 진행 상황까지 관리할 수 있습니다.

### 핵심 가치

- **AI 인터뷰 기반 아이디어 구체화**: 막연한 아이디어를 AI의 질문-답변 흐름을 통해 구체적인 기획으로 발전
- **AI 기획 문서 자동 생성**: 유저 시나리오, 기능 명세, API 설계 등 프로젝트 유형에 맞는 문서 초안을 자동 작성
- **실시간 협업**: 팀원들의 커서와 작업 현황을 실시간으로 공유하며 함께 문서 작성
- **AI 기반 백로그 관리**: 기획 문서를 기반으로 AI가 할 일 목록(Epic/Story/Task)을 생성하고 칸반/리스트 보드로 관리


<br>

## 2. 주요 기능

| 기능                  | 설명                                                                 |
| --------------------- | -------------------------------------------------------------------- |
| 🤖 AI 기획 초안 생성  | 핵심 아이디어 입력 → AI 질의응답 → 기획 문서 초안 자동 생성          |
| 📝 실시간 협업 에디터 | BlockNote 기반 노션 스타일 에디터, 실시간 커서 프레즌스 및 동시 편집 |
| 🗂️ AI 백로그 관리     | 기획 문서 기반 Epic/Story/Task 자동 생성, 보드/리스트 뷰 제공        |
| 💬 AI 문서 피드백     | 논리적 일관성, 누락 내용, 모호한 표현에 대한 개선 제안               |
| 🔗 GitHub 이슈 연동   | 완성된 기획을 GitHub Epic/Story/Task 이슈로 자동 변환                |
| 👥 팀스페이스 & 초대  | 팀스페이스 생성, 멤버 초대 및 권한 관리                              |
| 📄 문서 내보내기      | 완성된 기획 문서를 PDF 등으로 내보내기                               |

<br>

## 3. 워크플로우

### 1) 아이디어 입력

GIF 삽입 예정

만들고 싶은 서비스의 핵심 아이디어를 한 줄로 입력합니다.

<br>

### 2) AI가 기획 문서 생성

GIF 삽입 예정

AI가 아이디어를 구체화하는 질문을 던지고, 사용자의 답변을 바탕으로 유저 시나리오·기능 명세 등 문서 초안을 자동 작성합니다.

<br>

### 3) 팀과 실시간 협업

팀원을 초대해 실시간으로 문서를 함께 완성합니다. (커서 위치, 수정 이력 공유)

<br>

GIF 삽입 예정

<br>
<br>

### 4) AI 백로그 생성

GIF 삽입 예정

완성된 기획을 기반으로 할 일 목록(Epic/Story/Task)을 자동 생성하여 백로그를 관리합니다.

<br>

## 4. 기술 스택

### Frontend

![React](https://img.shields.io/badge/React-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-646CFF?logo=vite&logoColor=white)
![TailwindCSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?logo=tailwindcss&logoColor=white)
![Zustand](https://img.shields.io/badge/Zustand-433E38?logo=react&logoColor=white)
![React Router](https://img.shields.io/badge/React_Router-CA4245?logo=reactrouter&logoColor=white)
![Axios](https://img.shields.io/badge/Axios-5A29E4?logo=axios&logoColor=white)

### 실시간 협업

![Yjs](https://img.shields.io/badge/Yjs-000000?logo=yjs&logoColor=white)
![BlockNote](https://img.shields.io/badge/BlockNote-2383E2?logo=notion&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-y--websocket-010101?logo=socketdotio&logoColor=white)

### Tooling

![ESLint](https://img.shields.io/badge/ESLint-4B32C3?logo=eslint&logoColor=white)
![Prettier](https://img.shields.io/badge/Prettier-F7B93E?logo=prettier&logoColor=black)
![Husky](https://img.shields.io/badge/Husky-000000?logo=git&logoColor=white)
![pnpm](https://img.shields.io/badge/pnpm-F69220?logo=pnpm&logoColor=white)

<br>

## 5. 인프라 아키텍처

<img width="1001" alt="image" src="https://github.com/user-attachments/assets/01f26339-7f4d-401d-8c7a-342dfbe8e1a9" />

<br>

## 6. CI/CD 파이프라인

<img width="1010" alt="image" src="https://github.com/user-attachments/assets/fd4d4412-0c20-4a13-9920-434b0c26bbf7" />

<br>

## 7. 실행 방법

```bash
# 의존성 설치
pnpm install

# 개발 서버 실행
pnpm dev

# 빌드
pnpm build

# 린트
pnpm lint
```

<br>

## 8. 프로젝트 레포지토리

- 🔗 **백엔드**: [bu-capstone-4/Aidea-back](https://github.com/bu-capstone-4/Aidea-back)
- 🔗 **프론트엔드**: [bu-capstone-4/Aidea-front](https://github.com/bu-capstone-4/Aidea-front)

<br>

## 9. 팀원 소개

<div align="center">

|                                               Leader                                               |                                             Front                                              |                                         Front                                          |                                                Backend                                                 |                                                Backend                                                 |
| :------------------------------------------------------------------------------------------------: | :--------------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------------: |
| [<img src="https://github.com/kang-min-seok.png" width="100px">](https://github.com/kang-min-seok) | [<img src="https://github.com/hanjiwon818.png" width="100px">](https://github.com/hanjiwon818) | [<img src="https://github.com/PARKDEC.png" width="100px">](https://github.com/PARKDEC) | [<img src="https://github.com/bhkim-fullstack.png" width="100px">](https://github.com/bhkim-fullstack) | [<img src="https://github.com/gaeunssssssssss.png" width="100px">](https://github.com/gaeunssssssssss) |
|                             [강민석](https://github.com/kang-min-seok)                             |                            [한지원](https://github.com/hanjiwon818)                            |                          [박지훈](https://github.com/PARKDEC)                          |                              [김병현](https://github.com/bhkim-fullstack)                              |                              [박가은](https://github.com/gaeunssssssssss)                              |

</div>

<br>
