# 백엔드 변경사항 및 프론트엔드 연동 가이드

## 1. 백엔드 변경사항

### 1-1. 초대 관련 (InvitationController / InvitationService)

#### 변경: `POST /api/invitations/accept`
- GET → POST로 변경
- query param → request body로 변경

```json
// Request Body
{ "token": "초대 토큰" }

// Response
{
  "success": true,
  "message": "팀스페이스에 참여하였습니다.",
  "data": { "teamspaceId": "ts_abc123" }
}
```

#### 추가: `GET /api/invitations/accept?token=`
- 이메일 링크 클릭 시 브라우저에서 직접 접근하는 엔드포인트
- 인증 불필요 (permitAll)

| 상태 | 처리 |
|---|---|
| 비로그인 | `{frontendUrl}/invite?token=...` 으로 리다이렉트 |
| 로그인 + 정상 | `{frontendUrl}/teamspaces/{teamspaceId}` 로 리다이렉트 |
| 로그인 + 이미 멤버 | `{frontendUrl}/teamspaces/{teamspaceId}` 로 리다이렉트 |
| 기타 오류 | `{frontendUrl}/?error={errorCode}` 로 리다이렉트 |

> **이메일 검증**: 초대받은 이메일과 로그인된 계정 이메일이 다르면 `INVITATION_NOT_FOUND` 반환

#### 추가: `POST /api/teamspaces/{teamspaceId}/invitations` (일괄 초대)
- OWNER만 호출 가능
- 최대 8명

```json
// Request Body
{ "emails": ["a@example.com", "b@example.com"] }

// Response
{
  "success": true,
  "message": "초대가 발송되었습니다.",
  "data": [
    { "email": "a@example.com", "status": "SENT" },
    { "email": "b@example.com", "status": "ALREADY_MEMBER" }
  ]
}
```

---

### 1-2. 로그아웃 (AuthController)

#### 변경: `POST /api/auth/logout`
- JWT 쿠키 삭제 + `https://github.com/logout` 으로 리다이렉트
- JSON 응답 없음 → 리다이렉트 응답

> **주의**: fetch로 호출하면 안 됨. 브라우저가 리다이렉트를 따라가야 함

---

### 1-3. GitHub OAuth 로그인

#### 변경: `/oauth2/authorization/github`
- `login=` 파라미터 자동 추가 → GitHub 로그인 화면 강제 표시
- GitHub OAuth 취소/실패 시 → `{frontendUrl}/login?error=oauth2` 로 리다이렉트

---

### 1-4. 버그 수정

| 위치 | 내용 |
|---|---|
| `MemberService.cancelInvitation` | 초대 취소 시 실제로 DB에서 삭제 안 되던 버그 수정 |
| `AuthService.logout` | 로그아웃 시 DB refresh_token 미삭제 버그 수정 |
| `HttpCookieOAuth2AuthorizationRequestRepository` | OAuth 취소 시 역직렬화 실패로 500 에러 나던 버그 수정 |

---

## 2. 프론트엔드 처리 필요 사항

### 2-1. `/invite?token=...` 라우트 추가 (필수)

이메일 링크 클릭 → 비로그인 상태일 때 이 페이지로 리다이렉트됨

```
흐름:
1. URL에서 token 파라미터 추출
2. GET /api/auth/me 호출
   - 200 (로그인 상태): POST /api/invitations/accept { token } 호출
                         → data.teamspaceId 로 /teamspaces/{id} 이동
   - 401 (비로그인):    token을 localStorage 또는 sessionStorage에 저장
                         → GitHub 로그인으로 이동 (/oauth2/authorization/github)
3. 로그인 완료 후:
   - 저장된 token 꺼내서 POST /api/invitations/accept { token } 호출
   - data.teamspaceId 로 /teamspaces/{id} 이동
```

### 2-2. `/login?error=oauth2` 처리 (필수)

GitHub OAuth 취소 또는 실패 시 이 URL로 리다이렉트됨

```javascript
const params = new URLSearchParams(window.location.search);
if (params.get('error') === 'oauth2') {
  // 에러 메시지 표시 또는 재로그인 유도
}
```

### 2-3. 로그아웃 버튼 처리 (필수)

`POST /api/auth/logout`은 리다이렉트 응답이므로 fetch가 아닌 form submit 또는 location 이동으로 처리

```javascript
// 방법 1: form submit
const form = document.createElement('form');
form.method = 'POST';
form.action = 'http://localhost:8080/api/auth/logout';
document.body.appendChild(form);
form.submit();

// 방법 2: fetch (credentials 포함, redirect 허용)
fetch('http://localhost:8080/api/auth/logout', {
  method: 'POST',
  credentials: 'include',
  redirect: 'follow'
}).then(() => {
  window.location.href = 'https://github.com/logout';
});
```

### 2-4. 멤버 목록에서 PENDING 상태 표시

`GET /api/teamspaces/{teamspaceId}/members` 응답에 PENDING 멤버 포함

```json
{
  "userId": null,
  "name": null,
  "email": "invited@example.com",
  "role": "MEMBER",
  "status": "PENDING",
  "profileImageUrl": null
}
```

---

## 3. 환경변수 (.env)

```
smtpEmail=Gmail 주소
smtpPassword=Gmail 앱 비밀번호 (계정 비밀번호 아님)
```

> Gmail 앱 비밀번호: Google 계정 → 보안 → 2단계 인증 → 앱 비밀번호
