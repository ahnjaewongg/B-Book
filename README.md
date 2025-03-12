# 📚 B-Book

**Spring Boot 기반 온라인 서점 플랫폼**  
  
사용자 친화적인 인터페이스와 안전한 결제 시스템을 통해  
온라인 도서 구매 경험을 향상시키는 것을 목표로 합니다.

</div>

---

## 📚 기술 스택

### 🔧 백엔드
![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=spring-security&logoColor=white)
![JPA](https://img.shields.io/badge/JPA_&_Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)

### 🔌 외부 서비스
![OAuth2](https://img.shields.io/badge/OAuth2-EB5424?style=for-the-badge&logo=auth0&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Slack](https://img.shields.io/badge/Slack-4A154B?style=for-the-badge&logo=slack&logoColor=white)
![Iamport](https://img.shields.io/badge/Iamport-0E83CD?style=for-the-badge&logo=stripe&logoColor=white)

### 🎨 프론트엔드
![Thymeleaf](https://img.shields.io/badge/Thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![CSS](https://img.shields.io/badge/CSS-1572B6?style=for-the-badge&logo=css3&logoColor=white)

---

## 🌟 주요 기능 및 담당자

### 📊 기본 기능

| 기능 | 설명 | 담당자 |
|------|------|--------|
| **사용자 관리** | 일반 회원가입 및 로그인, 소셜 로그인(Google, Naver, Kakao), 사용자 프로필 관리 | 이종민 |
| **도서 관리 및 검색** | 도서 카탈로그 브라우징, 도서 검색 및 필터링, 도서 상세 정보 조회 | 이종민 |
| **쇼핑 기능** | 장바구니 관리, 주문 및 결제 처리, 구독 서비스 지원 | 안재원 |
| **결제 및 환불** | Iamport를 통한 안전한 결제 처리, 주문 취소 및 환불 프로세스, 결제 내역 조회 | 안재원 |
| **리뷰 시스템** | 도서 리뷰 작성 및 조회, 리뷰 이미지 업로드, 평점 시스템 | 김지헌 |
| **고객 지원** | 실시간 채팅 지원(WebSocket), FAQ 시스템, 이메일 문의 | 이종민 |

### 💡 차별화된 특징 및 담당자

#### 🛒 쇼핑 및 결제 시스템 (안재원)
- 직관적인 장바구니 관리 인터페이스
- Iamport 연동을 통한 안전한 결제 처리
- 구독 서비스를 통한 정기 결제 시스템
- 주문 관리 및 통계 대시보드
- 실시간 주문 알림 (Firebase, Slack)

#### 📝 리뷰 및 상품 관리 (김지헌)
- 사용자 친화적인 리뷰 작성 시스템
- 이미지 첨부 기능이 있는 상세 리뷰
- 위시리스트(찜하기) 기능
- 상품 관리 인터페이스
- 상품 통계 및 분석 도구

#### 👤 사용자 및 고객 지원 (이종민)
- 다중 소셜 로그인 지원
- 직관적인 회원 관리 시스템
- 실시간 채팅 고객 지원
- 이메일 알림 시스템
- 관리자용 회원 관리 도구

---

## 🚀 설치 및 실행 방법

### 요구사항
- JDK 21
- MySQL/MariaDB
- Maven

<details>
<summary><b>설치 단계</b></summary>

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
</details>

---

## ⚙️ 환경 설정

<details>
<summary><b>데이터베이스 설정</b></summary>

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/bookshop
    username: root
    password: 1234
```
</details>

<details>
<summary><b>OAuth2 설정</b></summary>

소셜 로그인을 위한 OAuth2 설정은 `application-secret.yml` 파일에서 관리합니다.
</details>

<details>
<summary><b>결제 시스템 설정</b></summary>

아임포트 결제 시스템 연동을 위한 설정은 `application-secret.yml` 파일에서 관리합니다.
</details>

---

## 📁 프로젝트 구조
```
com.bbook
├── BbookApplication.java # 애플리케이션 진입점
├── client # 외부 API 클라이언트
│ ├── IamportClient.java # 아임포트 결제 API 클라이언트
│ └── IamportResponse.java # 아임포트 응답 모델
├── config # 설정 클래스
│ ├── AsyncConfig.java # 비동기 처리 설정
│ ├── SecurityConfig.java # 보안 설정
│ ├── WebSocketConfig.java # 웹소켓 설정
│ └── ...
├── constant # 상수 및 열거형
│ ├── BookStatus.java
│ ├── OrderStatus.java
│ └── ...
├── controller # 컨트롤러
│ ├── BookController.java
│ ├── OrderController.java
│ └── ...
├── dto # 데이터 전송 객체
│ ├── BookDto.java
│ ├── OrderDto.java
│ └── ...
├── entity # 엔티티 클래스
│ ├── Book.java
│ ├── Member.java
│ ├── Order.java
│ └── ...
├── repository # 데이터 접근 계층
│ ├── BookRepository.java
│ ├── MemberRepository.java
│ └── ...
├── service # 비즈니스 로직
│ ├── BookService.java
│ ├── MemberService.java
│ └── ...
└── utils # 유틸리티 클래스
└── ...
```

---

## 👥 기여자

### 안재원 [@ahnjaewongg](https://github.com/ahnjaewongg)
- 장바구니 관리 시스템
- 주문 및 결제 처리
- 구독 서비스 구현
- Iamport 결제 및 환불 시스템
- 주문 관리 및 통계
- Firebase 알림 시스템
- Slack 시스템 알림

### 김지헌
- 위시리스트(찜하기) 기능
- 리뷰 시스템 개발
- 상품 관리 인터페이스
- 상품 통계 및 분석 도구

### 이종민
- 사용자 관리 시스템
- 도서 관리 및 검색 기능
- 고객 지원 시스템
- 회원 관리 도구
- 이메일 알림 시스템

---

© 2023 B-Book | [GitHub](https://github.com/ahnjaewongg/B-Book)

<sub>이 프로젝트는 교육 목적으로 개발되었으며, 실제 서비스와는 관련이 없습니다.</sub>
