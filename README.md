# B-Book

## 온라인 서점 플랫폼

B-Book은 Spring Boot 기반의 온라인 서점 플랫폼으로, 사용자 친화적인 인터페이스와 안전한 결제 시스템을 통해 온라인 도서 구매 경험을 향상시키는 것을 목표로 합니다.

![B-Book Logo](https://via.placeholder.com/150x50?text=B-Book)

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [설치 및 실행 방법](#설치-및-실행-방법)
- [환경 설정](#환경-설정)
- [API 문서](#api-문서)
- [기여자](#기여자)

## 주요 기능

### 1. 사용자 관리
- 일반 회원가입 및 로그인
- Google, Naver, Kakao를 통한 소셜 로그인
- 사용자 프로필 관리

### 2. 도서 관리 및 검색
- 도서 카탈로그 브라우징
- 도서 검색 및 필터링
- 도서 상세 정보 조회

### 3. 쇼핑 기능
- 장바구니 관리
- 위시리스트(찜하기) 기능
- 주문 및 결제 처리
- 구독 서비스 지원

### 4. 결제 및 환불
- Iamport를 통한 안전한 결제 처리
- 주문 취소 및 환불 프로세스
- 결제 내역 조회

### 5. 리뷰 시스템
- 도서 리뷰 작성 및 조회
- 리뷰 이미지 업로드
- 평점 시스템

### 6. 고객 지원
- 실시간 채팅 지원 (WebSocket)
- FAQ 시스템
- 이메일 문의

### 7. 관리자 기능
- 주문 관리
- 상품 관리
- 회원 관리
- 통계 및 보고서

### 8. 알림 시스템
- 이메일 알림
- Firebase를 통한 알림
- Slack을 통한 시스템 알림

## 기술 스택

### 백엔드
- **Spring Boot**: 애플리케이션의 핵심 프레임워크
- **Spring Security**: 인증 및 권한 관리
- **Spring Data JPA**: 데이터베이스 접근
- **Spring WebSocket**: 실시간 채팅 기능
- **OAuth2**: 소셜 로그인 (Google, Naver, Kakao)

### 데이터베이스
- **MySQL/MariaDB**: 주요 데이터베이스
- **Hibernate**: ORM 프레임워크

### 외부 서비스 통합
- **Iamport**: 결제 처리 및 환불
- **Firebase**: 알림 서비스
- **Slack**: 시스템 알림 및 모니터링
- **OpenAI API**: AI 기반 기능

### 프론트엔드
- **Thymeleaf**: 서버 사이드 템플릿 엔진
- **JavaScript/CSS**: 클라이언트 측 기능 및 스타일링

### 기타 도구 및 라이브러리
- **Lombok**: 반복적인 코드 작성 감소
- **RestTemplate/WebClient**: 외부 API 통신
- **JavaMailSender**: 이메일 발송

## 프로젝트 구조

```
com.bbook
├── BbookApplication.java        # 애플리케이션 진입점
├── client                       # 외부 API 클라이언트
│   ├── IamportClient.java       # 아임포트 결제 API 클라이언트
│   └── IamportResponse.java     # 아임포트 응답 모델
├── config                       # 설정 클래스
│   ├── AsyncConfig.java         # 비동기 처리 설정
│   ├── SecurityConfig.java      # 보안 설정
│   ├── WebSocketConfig.java     # 웹소켓 설정
│   └── ...
├── constant                     # 상수 및 열거형
│   ├── BookStatus.java
│   ├── OrderStatus.java
│   └── ...
├── controller                   # 컨트롤러
│   ├── BookController.java
│   ├── OrderController.java
│   └── ...
├── dto                          # 데이터 전송 객체
│   ├── BookDto.java
│   ├── OrderDto.java
│   └── ...
├── entity                       # 엔티티 클래스
│   ├── Book.java
│   ├── Member.java
│   ├── Order.java
│   └── ...
├── exception                    # 예외 클래스
│   └── ...
├── repository                   # 데이터 접근 계층
│   ├── BookRepository.java
│   ├── MemberRepository.java
│   └── ...
├── service                      # 비즈니스 로직
│   ├── BookService.java
│   ├── MemberService.java
│   └── ...
└── utils                        # 유틸리티 클래스
    └── ...
```

## 설치 및 실행 방법

### 사전 요구사항
- JDK 21
- MySQL/MariaDB
- Maven

### 설치 단계

1. 저장소 클론
```bash
git clone https://github.com/ahnjaewongg/B-Book.git
cd B-Book
```

2. 데이터베이스 설정
```sql
CREATE DATABASE bookshop;
```

3. 애플리케이션 설정
`src/main/resources/application.yml` 파일에서 데이터베이스 연결 정보를 수정합니다.

4. 애플리케이션 빌드 및 실행
```bash
mvn clean package
java -jar target/bbook-0.0.1-SNAPSHOT.jar
```

5. 웹 브라우저에서 접속
```
http://localhost:80
```

## 환경 설정

### 데이터베이스 설정
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/bookshop
    username: root
    password: 1234
```

### OAuth2 설정
소셜 로그인을 위한 OAuth2 설정은 `application-secret.yml` 파일에서 관리합니다.

### 결제 시스템 설정
아임포트 결제 시스템 연동을 위한 설정은 `application-secret.yml` 파일에서 관리합니다.

### 이메일 설정
이메일 발송을 위한 SMTP 설정은 `application.yml` 파일에서 관리합니다.

## 기여자

- [김지헌. 이종민. 안재원](https://github.com/username)

## 라이센스

이 프로젝트는 MIT 라이센스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

---

© 2025 B-Book Team. All Rights Reserved.
