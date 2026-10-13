# 42Hz 기존 랜딩 디자인 복원판

- 원본: `42hz-fixed-hero.zip`의 랜딩 페이지, 히어로, 소개 섹션, 이미지와 스타일을 그대로 복원했습니다.
- 현재 MVP 백엔드 및 PostgreSQL 설정은 `42hz-mvp-room-join.zip`에서 유지했습니다.
- 시작 버튼은 `/rooms/create`로 이동하고, 방 생성 시 `POST /api/v1/rooms`를 호출합니다.
- 생성된 방은 `/room/{code}`에서 초대 및 참여를 진행합니다.

## 설치

기존 프로젝트를 백업한 뒤 이 압축의 `frontend/src/app`, `frontend/src/components`, `frontend/public` 및 `frontend/src/lib`를 적용하거나 전체 프로젝트를 별도 폴더에서 실행하세요.

```sh
docker compose -f infra/docker-compose.yml up -d
cd backend && ./gradlew bootRun
# 별도 터미널
cd frontend && npm install && npm run dev
```

참고: Gradle Wrapper는 이전 프로젝트에서 누락되었으므로 `backend/gradlew`가 없으면 설치한 Gradle로 `gradle wrapper --gradle-version 9.7.1`을 실행해야 합니다.
