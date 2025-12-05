#!/bin/bash
# ---------------------------------------------------------
# 사용법:
#   ./run-client.sh
#
# 기능:
#   1) 기존 out 디렉터리 삭제
#   2) ide/ 아래 모든 .java 컴파일
#   3) 클라이언트 실행 (CollabIDE)
# ---------------------------------------------------------

set -e

rm -rf out
mkdir -p out

echo "[1/2] 컴파일 중..."
javac -d out $(find ide -name "*.java")

echo "[2/2] 클라이언트 실행 중..."
java -cp out ide.app.CollabIDE