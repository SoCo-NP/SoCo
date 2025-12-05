#!/bin/bash
# ---------------------------------------------------------
# 사용법:
#   ./run-server.sh
#
# 기능:
#   1) 기존 out 디렉터리 삭제
#   2) ide/ 아래 모든 .java 컴파일
#   3) 서버 실행 (기본 포트 6000)
# ---------------------------------------------------------

set -e  # 중간에 에러 나면 바로 종료

echo "out파일 삭제/재생성..."
rm -rf out
mkdir -p out

echo "[1/2] 컴파일 중..."
javac -d out $(find ide -name "*.java")

echo "[2/2] 서버 실행 중... (포트 6000)"
java -cp out ide.server.CollabServer 6000