// Firebase 초기화
firebase.initializeApp({
  apiKey: "AIzaSyDniXBACWTBr6GzWn-kUApAxW5JA7pgUxs",
  authDomain: "bbook-73ef7.firebaseapp.com",
  projectId: "bbook-73ef7",
  storageBucket: "bbook-73ef7.firebasestorage.app",
  messagingSenderId: "698907826494",
  appId: "1:698907826494:web:e03fdce42ba65c7e82746f",
  measurementId: "G-YJJFPT71QC"
});

const messaging = firebase.messaging();
// 알림 권한 요청 & Service Worker 등록
async function initializeFirebaseMessaging() {
  try {
    const permission = await Notification.requestPermission();
    if (permission === 'granted') {
      const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');
      // FCM 토큰 발급
      const token = await messaging.getToken({
        vapidKey: "BLVV2y4lBSp4dqntmM-i6WeQhzeJwYRssznEw8uY76ALx8V0aKStQHqW-DLWnphampmzxaA8vwi3Vf1aV-K9WLM",
        serviceWorkerRegistration: registration
      })
      // 토큰이 있으면 서버에 전송 
      if (token) {
        await sendTokenToServer(token);
      }
    }
  } catch (err) {
    console.error('Firebase 초기화 실패:', err);
  }
}

// 초기화 실행
initializeFirebaseMessaging();

// 포그라운드 메시지 수신
messaging.onMessage((payload) => {
  console.log('포그라운드 메시지 수신:', payload);
  showNotification(payload.notification.title, payload.notification.body);
});

function showNotification(title, body) {
  console.log('알림 표시 시도:', { title, body });
  if ('Notification' in window) {
    if (Notification.permission === 'granted') {
      new Notification(title, {
        body: body,
        icon: '/img/notification-icon.svg'
      });
      console.log('알림 표시 성공');
    } else {
      console.log('알림 권한이 없음:', Notification.permission);
    }
  } else {
    console.log('이 브라우저는 알림을 지원하지 않음');
  }
}

function sendTokenToServer(token) {
  fetch('/api/fcm/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ token: token })
  })
  .then(response => {
    console.log('토큰 전송 성공:', response);
  })
  .catch(error => {
    console.error('토큰 전송 실패:', error);
  });
} 