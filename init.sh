#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

VERIFY_CMD=(mvn clean test)
START_CMD=(mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests)

echo "==> 当前目录: $PWD"
echo "==> 检查 Maven"
if ! command -v mvn >/dev/null 2>&1; then
  echo "错误：未找到 mvn。请先安装 Maven，再重新运行 ./init.sh。" >&2
  exit 127
fi

echo "==> 运行基础验证"
"${VERIFY_CMD[@]}"

echo "==> 启动命令"
printf '    %q' "${START_CMD[@]}"
printf '\n'

if [ "${RUN_START_COMMAND:-0}" = "1" ]; then
  echo "==> 启动应用"
  exec "${START_CMD[@]}"
fi

echo "如果希望 init.sh 直接启动应用，请设置 RUN_START_COMMAND=1。"
