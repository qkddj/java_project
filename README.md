# 🚀 JAVA 응용 프로젝트 (PBL): Swing Mongo Auth & Realtime System

## 📖 프로젝트 소개 (Project Overview)
**Swing Mongo Auth & Realtime System**은 Java Swing을 기반으로 한 **올인원 소셜/커뮤니케이션 데스크톱 애플리케이션**입니다.  
대학교 PBL(Project Based Learning) 과제로 개발되었으며, 별도의 복잡한 설정 없이 실행만으로 서버와 클라이언트가 자동으로 연결되는 **Zero-Config** 환경을 지향합니다.

### 🎯 개발 목적 & 목표
- **Single Jar Architecture**: 하나의 JAR 파일로 서버(Server)와 클라이언트(Client) 역할을 모두 수행.
- **Auto Discovery**: 같은 네트워크 내에서 UDP 브로드캐스팅을 통해 실행 중인 서버를 자동으로 탐색 및 연결.
- **Security & Safety**: 위치 기반 인증, 악성 사용자 자동 차단 시스템, 공공 재난 문자 연동을 통해 안전한 커뮤니티 환경 제공.
- **Hybrid Communication**: 실시간 채팅(Socket.IO)과 화상 통화(WebRTC + Ngrok)를 동시에 지원.

---

## 🛠 기술 스택 (Tech Stack)

### 💻 Core & Client
| 기술 | 버전 | 설명 |
| :--- | :--- | :--- |
| **Java** | 17 LTS | 최신 언어 기능(Record, Switch Expression 등) 및 고성능 활용 |
| **Java Swing** | - | 데스크톱 네이티브 GUI 구현 |
| **FlatLaf** | 3.4 | 모던한 룩앤필(Look and Feel) 제공, 커스텀 **Cyberpunk Neon 테마** 적용 |
| **JavaFX** | 17.0.2 | WebRTC 화상 통화 화면 렌더링을 위한 `WebView` 컴포넌트 활용 |

### ☁️ Backend & Database
| 기술 | 버전 | 설명 |
| :--- | :--- | :--- |
| **MongoDB** | 5.1.0 | 사용자, 게시글, 신고 내역, 평점 데이터의 유연한 저장 (Sync Driver) |
| **BCrypt** | 0.4 | 사용자 비밀번호의 안전한 단방향 해싱 저장 |
| **Jetty** | 11.0.20 | 화상 통화 시그널링(WebSocket) 및 정적 리소스 서빙 |

### 📡 Network & Protocol
| 기술 | 버전 | 설명 |
| :--- | :--- | :--- |
| **Netty-SocketIO** | 2.0.7 | 고성능 실시간 랜덤 채팅 서버 구현 |
| **Socket.IO Client** | 2.1.1 | 클라이언트 채팅 메시지 송수신 |
| **Java HTTP Client** | Standard | 행정안전부 재난문자 REST API 비동기 연동 |
| **Ngrok** | - | 로컬 서버를 외부 HTTPS 주소로 터널링하여 WebRTC 통신 지원 |

---

## ✨ 주요 기능 (Key Features)

1. **🔐 스마트 인증 및 자동 제재**: 위치 기반 로그인, 신고율 10% 이상 시 자동 차단.
2. **💬 실시간 랜덤 채팅**: Socket.IO 기반 저지연 1:1 매칭 및 채팅.
3. **📹 WebRTC 화상 통화**: JavaFX WebView와 Jetty 서버를 연동한 화상 통화 (Ngrok 지원).
4. **📢 위치 기반 커뮤니티**: 동네 기반 게시글 노출, 좋아요/싫어요/신고/수정/삭제 기능.
5. **🚨 재난 안전 알림**: 행안부 API 연동 실시간 재난 문자 수신 (1시간 캐싱).
6. **📡 네트워크 자동 발견**: UDP Broadcast로 같은 네트워크 내 서버 자동 연결.

---

## 💻 설치 및 실행 (Getting Started)

운영체제에 맞는 설치 방법을 참고하세요.

### 🍎 macOS 사용자 (Mac)
터미널(Terminal)을 열고 아래 명령어를 입력하여 설치합니다. (Homebrew 필요)

**1. 필수 도구 설치**
```bash
# Java 17, Maven, Git 설치
brew install openjdk@17 maven git
2. 소스 코드 다운로드 (Clone)

Bash

# 원하는 폴더로 이동
cd Documents

# 코드 가져오기
git clone [https://github.com/heoeunjun/swing-mongo-auth.git](https://github.com/heoeunjun/swing-mongo-auth.git)

# 폴더 안으로 이동
cd swing-mongo-auth
🪟 Windows 사용자 (Windows)
PowerShell 또는 명령 프롬프트(CMD)를 사용하여 설치합니다.

1. 필수 도구 설치

Java 17 (JDK): 오라클 JDK 다운로드에서 Windows x64 Installer를 다운로드하여 설치합니다.

Git: Git for Windows에서 다운로드하여 설치합니다.

Maven:

Apache Maven 다운로드 페이지에서 Binary zip archive를 다운로드합니다.

압축을 풀고 원하는 위치(예: C:\Program Files\Maven)에 저장합니다.

환경 변수 편집을 열어 Path에 Maven의 bin 폴더 경로를 추가합니다.

💡 팁 (Winget 사용 시): 윈도우 10/11 사용자는 터미널에서 아래 명령어로 한 번에 설치할 수 있습니다.

PowerShell

winget install Oracle.JDK.17
winget install Git.Git
winget install Maven.Maven
설치 후 터미널을 껐다가 다시 켜야 적용됩니다.

2. 소스 코드 다운로드 (Clone)

PowerShell

# PowerShell 또는 Git Bash 실행
# 코드 가져오기
git clone [https://github.com/heoeunjun/swing-mongo-auth.git](https://github.com/heoeunjun/swing-mongo-auth.git)

# 폴더 안으로 이동
cd swing-mongo-auth
🚀 공통: 설정 및 실행 (Config & Run)
1. 빌드 (Build) 프로젝트 루트 경로에서 다음 명령어를 입력합니다.

Bash

mvn clean package -DskipTests
2. 실행 (Run)

Bash

# Maven으로 바로 실행
mvn exec:java
또는 생성된 JAR 파일로 직접 실행할 수 있습니다.

Bash

java -jar target/swing-mongo-auth-0.0.1-SNAPSHOT.jar
🏗 아키텍처 및 동작 원리
프로그램을 실행하면 Main.java가 자동으로 모드를 결정합니다.

네트워크 스캔 (3초): 같은 와이파이(네트워크) 안에 실행 중인 서버가 있는지 찾습니다.

클라이언트 모드: 서버를 발견하면 즉시 해당 IP로 연결하여 로그인 화면을 띄웁니다.

서버 모드: 서버가 없다면 내 컴퓨터를 서버로 만들고, 동시에 클라이언트 화면도 띄웁니다.

다른 친구들은 내 컴퓨터 IP로 자동 연결됩니다.

📮 문의처 (Contact)
Developer: 조우렬

Email: qkddj1234@gmail.com
