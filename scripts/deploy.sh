#!/bin/bash
set -e

NEW_IMAGE=$1

if [ -z "$NEW_IMAGE" ]; then
  echo "Usage: ./deploy.sh <ECR_IMAGE>"
  exit 1
fi

# docker compose 프로젝트 이름을 항상 'app'으로 고정
# → 실행 경로가 달라도 네트워크 이름(app_app-network)이 일관되게 유지됨
# → mysql 컨테이너와 spring 컨테이너가 같은 네트워크에 붙을 수 있음
cd ~/app

# ECR 로그인 (EC2 IAM Role 사용 — AWS 크레덴셜 별도 전달 불필요)
aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin \
    $(echo $NEW_IMAGE | cut -d'/' -f1)

# 모든 docker compose 명령에서 ECR_IMAGE를 사용할 수 있도록 export
export ECR_IMAGE=$NEW_IMAGE

# 현재 active 색상 판별 (파일 없으면 blue가 active)
ACTIVE=$(cat ~/app/active_color 2>/dev/null || echo "blue")
if [ "$ACTIVE" = "blue" ]; then
  INACTIVE="green"
else
  INACTIVE="blue"
fi

echo "현재 active: $ACTIVE → 새 배포 대상: $INACTIVE"

# 새 컨테이너 시작 (--remove-orphans: 구버전 컨테이너 자동 정리)
docker compose up -d --remove-orphans spring-${INACTIVE}

# 헬스체크 (최대 60초)
echo "헬스체크 중..."
for i in $(seq 1 30); do

  # 컨테이너가 이미 종료됐으면 즉시 실패 (60초 기다릴 필요 없음)
  CONTAINER_STATUS=$(docker inspect --format='{{.State.Status}}' aidea-spring-${INACTIVE} 2>/dev/null || echo "not_found")
  if [ "$CONTAINER_STATUS" = "exited" ] || [ "$CONTAINER_STATUS" = "not_found" ]; then
    echo "컨테이너가 비정상 종료됨 — 최근 로그:"
    docker compose logs --tail=50 spring-${INACTIVE} || true
    docker compose stop spring-${INACTIVE} 2>/dev/null || true
    exit 1
  fi

  if docker compose exec spring-${INACTIVE} \
      wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "헬스체크 통과!"
    break
  fi
  if [ $i -eq 30 ]; then
    echo "헬스체크 타임아웃 → 롤백"
    docker compose stop spring-${INACTIVE}
    exit 1
  fi
  echo "헬스체크 대기 중... ($i/30)"
  sleep 2
done

# nginx upstream 전환 후 무중단 reload
sed -i "s/spring-${ACTIVE}:8080/spring-${INACTIVE}:8080/" \
  ~/app/nginx/conf.d/default.conf
docker compose exec nginx nginx -s reload

# 이전 컨테이너 종료
docker compose stop spring-${ACTIVE}

# active 색상 기록
echo $INACTIVE > ~/app/active_color

# .env의 ECR_IMAGE 갱신 (EC2 재시작 시에도 최신 이미지 사용)
sed -i "s|^ECR_IMAGE=.*|ECR_IMAGE=$NEW_IMAGE|" ~/app/.env

echo "배포 완료: $INACTIVE 가 active"
