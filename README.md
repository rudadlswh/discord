# discord

Kotlin 기반 Discord 클론 MVP 스캐폴드입니다.

## 포함 기능 (서버 스켈레톤)
- 멀티 유저 음성 채널 시그널링 (WebRTC)
- 텍스트 채팅 (WebSocket + REST 히스토리)
- 친구 요청 (추가/수락/거절)
- 채널 생성/입장
- Oracle DB (Docker)

> 오디오/비디오/화면 공유는 WebRTC 클라이언트가 처리하며, 서버는 시그널링만 중계합니다.

## 구조
- `server/`: Ktor 백엔드
- `db/init/`: Oracle 스키마
- `docker-compose.yml`: Oracle DB 컨테이너
- `docs/`: 아키텍처/API 문서

## 실행
### 1) Oracle DB
```sh
docker compose up -d
```

기본 접속 정보:
- URL: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`
- USER: `discord`
- PASSWORD: `discord`

### 2) 서버
Gradle 설치가 필요합니다.
```sh
cd server
gradle run
```

루트에서 실행:
```sh
gradle -p server run
```

### 3) Android 앱 (에뮬레이터)
Java 21이 필요합니다. (`scripts/run-android.sh`가 자동으로 설정)

권장: 실행 메크로 (Android 에뮬레이터 자동 실행)
`scripts/run-android.sh`가 에뮬레이터 실행, 앱 설치, 런처 진입을 자동으로 수행합니다.
스크립트는 실행 위치와 무관하게 레포 경로를 자동으로 찾습니다.
- 기본 AVD: `Medium_Phone_API_36.0`
- 필요 도구: `adb`, Android Emulator (`$HOME/Library/Android/sdk/emulator/emulator`)
- Java 21이 설치되어 있어야 합니다.

사용 예시:
```sh
./scripts/run-android.sh
./scripts/run-android.sh Pixel_6_API_34
```

필요 시 환경 변수로 경로를 변경할 수 있습니다:
- `EMULATOR_BIN`: emulator 실행 파일 경로
- `ADB_BIN`: adb 경로

수동 실행:
```sh
cd android
./gradlew installDebug
adb shell am start -n com.chogm.discordapp/.WelcomeActivity
```

에뮬레이터 기준 기본 Base URL: `http://10.0.2.2:8080`

### 환경 변수 (서버)
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_DRIVER`
- `DB_AUTO_MIGRATE` (true/false)
- `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`

기본 포트: `8080`

Ping: `GET /ping`

## 다음 작업
- Android 클라이언트 (WebRTC) 추가
- TURN 서버 (coturn) 추가
- 대규모 채널용 SFU 추가

자세한 내용은 `docs/architecture.md`, `docs/api.md`를 참고하세요.
