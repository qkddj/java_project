# 🚀 JAVA 응용 프로젝트 (PBL): Swing Mongo Auth & Realtime System

## 🛠 기술 스택 (Tech Stack)
### Core & UI
- Java 17 LTS: 최신 언어 기능/성능 활용
- Java Swing + FlatLaf: 데스크톱 GUI, 커스텀 사이버펑크 네온 테마
- JavaFX: WebRTC 화상 통화용 WebView 컴포넌트

### Backend & Database
- MongoDB (Atlas/Local): 사용자·게시글·신고·평점 데이터 저장
- MongoDB Java Driver (Sync): DB 연동
- BCrypt: 비밀번호 해싱

### Network & Communication
- Netty-SocketIO: 실시간 랜덤 채팅 서버 (Socket.IO 프로토콜)
- Socket.IO Client: 채팅 클라이언트 통신
- Jetty Server (WebSocket + Servlet): 화상 통화 시그널링, 정적 리소스 서빙
- Java HTTP Client: 행안부 재난문자 REST API 연동
- Ngrok Integration: 외부 HTTPS(WebRTC) 터널 자동화

---

## ✨ 주요 기능 (Key Features)
1. 🔐 인증/사용자 관리 (AuthService)
   - 회원가입/로그인: ID 중복 체크, BCrypt 해싱
   - 위치 자동 저장: GeoService로 국가/지역/동네 추정 후 저장
   - 자동 제재: 로그인 시 활동 데이터 평가
     - 신고 비율 10% 이상 차단
     - 영상/채팅 5회 이상 수행 후 평균 평점 2.0 미만 차단
     - 신고 5% 이상 + 저평점 시 복합 차단

2. 💬 실시간 랜덤 채팅 (ChatServer)
   - 1:1 매칭 큐, 실시간 매칭
   - 블랙리스트/저평점 필터로 불량 매칭 차단
   - Socket.IO로 저지연 메시지 전송

3. 📹 WebRTC 화상 통화 (ServerLauncher)
   - Jetty WebSocket 시그널링, JavaFX WebView 표시
   - Dynamic Port Allocation: 사용 가능한 포트 자동 탐색 후 `~/.video-call-server-port`에 기록
   - Ngrok 자동 실행/URL 감지로 HTTPS 환경 제공

4. 📢 게시판/커뮤니티 (PostService)
   - 위치 기반 노출(동네 우선), CRUD
   - 좋아요/싫어요, 댓글, 신고 지원

5. 🚨 재난 안전 알림 (SafetyAlertService)
   - 행안부 재난문자 API 실시간 조회
   - MongoDB TTL(1시간) 캐싱으로 호출 최적화

6. 📡 네트워크 자동 발견 (NetworkDiscovery)
   - Zero-Config: UDP 브로드캐스트로 서버가 IP/포트를 주기 송출
   - 클라이언트는 실행 시 3초 스캔 후 자동 연결

---

## 🏗 아키텍처 & 동작 원리
- 실행 모드 결정(Main.java)
  1) `CHAT_SERVER_HOST` 환경 변수 존재 시 즉시 클라이언트 모드
  2) UDP 스캔 3초: 서버 발견 시 해당 IP로 클라이언트 모드
  3) 미발견 시 로컬에 채팅/비디오 서버 구동(서버 모드)

- 포트 구성
  - 채팅 서버: 3001 (Socket.IO)
  - 비디오 서버: 동적 할당 (HTTP/WS) — 정적 페이지 + 시그널링
  - Service Discovery: 3002 (UDP)

---

## 💻 설치 및 실행 (Getting Started)
1) Prerequisites
- JDK 17+
- Maven 3.x
- MongoDB(로컬) 또는 Atlas URI
- (옵션) Ngrok: HTTPS WebRTC 터널링용, PATH 등록 권장

2) 설정 (Configuration)
- `src/main/java/com/swingauth/db/Mongo.java`에서 DB 연결 정보 설정
```java
// 보안을 위해 실제 배포 시 환경 변수 사용 권장
String uri = "mongodb+srv://<username>:<password>@cluster...";
String dbName = "swing_project_db";
```

3) 빌드 및 실행
```bash
mvn clean package -DskipTests
# 또는 Maven으로 바로 실행
mvn exec:java
```

---

## 🧪 빠른 테스트 체크
- 로그인/회원가입 후 위치 저장 확인
- 신고율 10%↑, 평점 < 2.0 시 로그인 차단 동작 확인
- 랜덤 채팅 매칭/이탈/재매칭 이벤트 동작
- 화상 통화: 시그널링 → 카메라/마이크 → Ngrok HTTPS 접속 확인
- 게시판 CRUD, 좋아요/싫어요,삭제, 댓글, 신고 반영 확인
- 재난문자 API 수신 및 1시간 TTL 캐싱 동작 확인
