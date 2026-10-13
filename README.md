# 42Hz MVP · 질문/답변 흐름

**기술 스택:** Java 25 · Spring Boot 4.1.1 · PostgreSQL 17 · Next.js 16.3.2 · TypeScript

## 실행 방법

### 1. PostgreSQL 실행
프로젝트 루트에서:
```bash
docker compose -f infra/docker-compose.yml up -d
```

### 2. 백엔드 실행
새 터미널에서:
```bash
cd backend
gradle bootRun
```
기본 주소는 `http://localhost:8080`입니다.

### 3. 프론트엔드 실행
다른 터미널에서:
```bash
cd frontend
npm install
npm run dev
```
`http://localhost:3000`을 열어주세요.

## 포함 기능
- 기존 42Hz 랜딩 페이지 및 밝은 파스텔 디자인 유지
- 방 생성, 초대 코드/링크 복사, 초대 코드 참여
- 두 명이 입장하면 연결 화면과 3초 카운트다운
- 13개 무료 질문: 상황형 선택 질문, 음악 취향 질문, 서술형 질문 1개
- 답변은 각자 제출 후 두 사람 모두 제출하면 공개
- 답변을 PostgreSQL에 저장하고 주기적으로 제출 상태 동기화
- 마지막 결과 화면에서 두 사람의 답변 비교

## API
- `POST /api/v1/rooms` 방 생성
- `POST /api/v1/rooms/{code}/join` 초대 참여
- `GET /api/v1/rooms/{code}` 방/참여자 상태
- `POST /api/v1/rooms/{code}/answers` 질문 답변 제출
- `GET /api/v1/rooms/{code}/answers/{questionIndex}` 해당 질문 제출 상태
- `GET /api/v1/rooms/{code}/results` 답변 결과

## MVP 참고사항
- 참여자 토큰은 브라우저 `sessionStorage`에 저장됩니다. 다른 기기나 브라우저에서 자동 복구되지 않습니다.
- 방 상태와 답변 상태는 프론트에서 주기적으로 조회합니다. 실시간 WebSocket 연결은 아직 사용하지 않습니다.
- 로컬 개발용 `ddl-auto: update` 설정입니다. 공개 서비스 배포 전 Flyway 마이그레이션, HTTPS, 속도 제한, 개인정보/만료 정책을 추가해야 합니다.
- 두 브라우저/기기에서 각각 초대 링크를 열어 전체 흐름을 테스트해 주세요.
