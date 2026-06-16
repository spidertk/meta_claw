#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

COMPILE_CMD=(mvn clean compile)
VERIFY_CMD=(
  mvn test
  -pl meta-claw-core,meta-claw-store,meta-claw-cli,meta-claw-bootstrap,meta-claw-tool
  -am
  -Dtest=VesselConfigLoaderTest,VesselManagerTest,SystemPromptBuilderTest,JsonlShortMemoryStoreTest,FileLongMemoryStoreTest,ChatCommandTest,MessageFlowIntegrationTest,ConfigurableHitlPolicyTest,HitlSubSystemTest,AgentExecutorHitlTest,StreamingAgentExecutorTest,SkillRegistryTest,SkillSubSystemTest,ReadSkillToolTest,MetricsSubSystemTest,MetricsRecorderTest,ToolRegistryTest,ToolSubSystemTest,ShellToolTest,FileToolTest,GitToolTest,AgentEngineFactoryTest,NativeAgentEngineTest,AlibabaEngineSmokeTest,SpiMessageConverterTest,LlmClientProviderManagerTest,SpringAiAlibabaAgentEngineTest,SpringAiAlibabaAgentEngineStreamTest,MetaClawAgentMetricsHookTest,MetaClawModelMetricsHookTest,MetaClawHitlHookTest,SaaMultiAgentFactoryTest,SpringAiAlibabaAgentEngineMultiAgentTest
  -Dsurefire.failIfNoSpecifiedTests=false
)
START_CMD=(mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests)

echo "==> 当前目录: $PWD"

# 查找并校验 Java 21
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d "/Users/kai/.local/jdks/jdk-21.0.10+7/Contents/Home" ]; then
    export JAVA_HOME="/Users/kai/.local/jdks/jdk-21.0.10+7/Contents/Home"
  fi
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
  if [[ ! "$JAVA_VERSION" =~ ^21\. ]]; then
    echo "错误：JAVA_HOME 指向的 Java 版本为 $JAVA_VERSION，但需要 Java 21。" >&2
    exit 1
  fi
else
  echo "错误：未找到 Java 21。请设置 JAVA_HOME 指向 JDK 21。" >&2
  exit 1
fi
echo "==> 使用 Java: $JAVA_HOME ($JAVA_VERSION)"

# 查找 Maven
if command -v mvn >/dev/null 2>&1; then
  MVN="$(command -v mvn)"
elif [ -x "/Users/kai/.local/tools/apache-maven-3.9.15/bin/mvn" ]; then
  MVN="/Users/kai/.local/tools/apache-maven-3.9.15/bin/mvn"
else
  echo "错误：未找到 mvn。请将 Maven 加入 PATH，或安装到 /Users/kai/.local/tools/apache-maven-3.9.15。" >&2
  exit 127
fi
echo "==> 使用 Maven: $MVN"

# 用检测到的 mvn 替换命令中的 mvn
COMPILE_CMD=("$MVN" clean compile)
VERIFY_CMD=("$MVN" test -pl meta-claw-core,meta-claw-store,meta-claw-cli,meta-claw-bootstrap,meta-claw-tool -am -Dtest=VesselConfigLoaderTest,VesselManagerTest,SystemPromptBuilderTest,JsonlShortMemoryStoreTest,FileLongMemoryStoreTest,ChatCommandTest,MessageFlowIntegrationTest,ConfigurableHitlPolicyTest,HitlSubSystemTest,AgentExecutorHitlTest,StreamingAgentExecutorTest,SkillRegistryTest,SkillSubSystemTest,ReadSkillToolTest,MetricsSubSystemTest,MetricsRecorderTest,ToolRegistryTest,ToolSubSystemTest,ShellToolTest,FileToolTest,GitToolTest,AgentEngineFactoryTest,NativeAgentEngineTest,AlibabaEngineSmokeTest,SpiMessageConverterTest,LlmClientProviderManagerTest,SpringAiAlibabaAgentEngineTest,SpringAiAlibabaAgentEngineStreamTest,MetaClawAgentMetricsHookTest,MetaClawModelMetricsHookTest,MetaClawHitlHookTest,SaaMultiAgentFactoryTest,SpringAiAlibabaAgentEngineMultiAgentTest -Dsurefire.failIfNoSpecifiedTests=false)
START_CMD=("$MVN" spring-boot:run -pl meta-claw-bootstrap -DskipTests)

echo "==> 编译全仓库"
"${COMPILE_CMD[@]}"

echo "==> 运行 P0 验证"
"${VERIFY_CMD[@]}"

echo "==> 启动命令"
printf '    %q' "${START_CMD[@]}"
printf '\n'

if [ "${RUN_START_COMMAND:-0}" = "1" ]; then
  echo "==> 启动应用"
  exec "${START_CMD[@]}"
fi

echo "如果希望 init.sh 直接启动应用，请设置 RUN_START_COMMAND=1。"
