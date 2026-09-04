#!/usr/bin/env bash
# Pretendard 폰트를 내려받아 app/src/main/res/font 에 배치한다.
#
# 저장소에는 이미 폰트가 커밋되어 있으므로 평소에는 실행할 필요가 없다.
# 버전을 올리거나 폰트 파일이 유실됐을 때만 사용한다.
#
# 폰트: Pretendard (c) 2021 Kil Hyung-jin — SIL Open Font License 1.1
# 배포처: https://github.com/orioncactus/pretendard
set -euo pipefail

VERSION="${1:-1.3.9}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FONT_DIR="$ROOT/app/src/main/res/font"
LICENSE_DIR="$ROOT/app/src/main/assets/licenses"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

URL="https://github.com/orioncactus/pretendard/releases/download/v${VERSION}/Pretendard-${VERSION}.zip"
echo "==> Pretendard v${VERSION} 다운로드"
curl -fsSL -o "$TMP/pretendard.zip" "$URL"

echo "==> 필요한 4개 굵기만 추출 (Regular/Medium/SemiBold/Bold)"
mkdir -p "$FONT_DIR" "$LICENSE_DIR"
unzip -q -o -j "$TMP/pretendard.zip" \
  "public/static/Pretendard-Regular.otf" \
  "public/static/Pretendard-Medium.otf" \
  "public/static/Pretendard-SemiBold.otf" \
  "public/static/Pretendard-Bold.otf" \
  -d "$TMP"
unzip -q -o -j "$TMP/pretendard.zip" "LICENSE.txt" -d "$TMP"

# Android 리소스 파일명 규칙: 소문자 + 밑줄
mv "$TMP/Pretendard-Regular.otf"  "$FONT_DIR/pretendard_regular.otf"
mv "$TMP/Pretendard-Medium.otf"   "$FONT_DIR/pretendard_medium.otf"
mv "$TMP/Pretendard-SemiBold.otf" "$FONT_DIR/pretendard_semibold.otf"
mv "$TMP/Pretendard-Bold.otf"     "$FONT_DIR/pretendard_bold.otf"
mv "$TMP/LICENSE.txt"             "$LICENSE_DIR/pretendard_ofl.txt"

echo "==> 완료"
ls -la "$FONT_DIR"
