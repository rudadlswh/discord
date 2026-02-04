# discord

디스코드 앱을 모방한 Kotlin 기반 애플리케이션의 초기 스캐폴딩입니다.

## 목표 기능
- 다중 참여 음성 채팅 (핵심)
- 텍스트 채팅
- 친구 신청/추가/거절
- 화면 공유
- 음성 채팅 중 화상 통화

## 기술 방향
- 언어: Kotlin
- 빌드: Gradle Kotlin DSL
- DB: Oracle (Docker 기반)

## 로컬 개발 준비
### Oracle 실행
```bash
docker compose up -d
```

기본 접속 정보:
- 사용자: discord
- 비밀번호: discord
- 포트: 1521

### 애플리케이션 실행
```bash
./gradlew run
```

## 다음 단계
- 도메인 모델 설계
- 실시간 음성/영상/화면공유용 WebRTC 게이트웨이 도입
- 채팅/친구 관리용 REST/WS API 설계
- 인증/인가 및 사용자 상태 관리
