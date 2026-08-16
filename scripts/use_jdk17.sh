#!/usr/bin/env bash
# Switch current shell to JDK 17 (Temurin). Requires: brew install --cask temurin@17
set -euo pipefail

JAVA17_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
if [[ -z "$JAVA17_HOME" ]]; then
  echo "未找到 JDK 17。请先在本机终端执行（需输入密码）："
  echo "  brew install --cask temurin@17"
  echo "安装完成后重新 source 本脚本或执行："
  echo "  export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
  exit 1
fi

export JAVA_HOME="$JAVA17_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME"
java -version
