#!/bin/bash
set -e

NEW_IMAGE=$1

if [ -z "$NEW_IMAGE" ]; then
  echo "Usage: ./deploy.sh <ECR_IMAGE>"
  exit 1
fi

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
  if docker compose exec spring-${INACTIVE} \
      wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "헬스체크 통과!"
    break
  fi
  if [ $i -eq 30 ]; then
    echo "헬스체크 실패 → 롤백"
    docker compose stop spring-${INACTIVE}
    exit 1
  fi
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
