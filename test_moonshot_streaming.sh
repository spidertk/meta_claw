#!/bin/bash
# 测试 Moonshot API 流式 vs 非流式返回时间对比
# 用法: ./test_moonshot_streaming.sh

set -e

CONFIG_FILE=".meta-claw/config.yaml"

# 简单 YAML 解析：提取指定 provider 下的字段值
yaml_get() {
    local file="$1"
    local provider="$2"
    local key="$3"
    awk -v prov="$provider" -v k="$key" '
    BEGIN { in_provider=0; indent=0 }
    /^providers:/ { next }
    /^[a-zA-Z]/ && in_provider==1 { in_provider=0 }
    /^[ ]*'"$provider"':/ { in_provider=1; indent=index($0, prov)-1; next }
    in_provider==1 {
        # 计算当前行的缩进
        line=$0
        gsub(/^[ \t]+/, "", line)
        curr_indent=length($0)-length(line)
        if (curr_indent <= indent) { in_provider=0; next }
        # 匹配 key
        if (line ~ "^" k ":") {
            gsub(/^[^:]+:[ \t]*"?/, "", line)
            gsub(/"?[ \t]*$/, "", line)
            print line
            exit
        }
    }
    ' "$file"
}

if [[ -f "$CONFIG_FILE" ]]; then
    DEFAULT_PROVIDER=$(grep "^default_provider:" "$CONFIG_FILE" | sed 's/.*default_provider:[[:space:]]*\(.*\)/\1/' | tr -d ' "')
    DEFAULT_PROVIDER="${DEFAULT_PROVIDER:-moonshot}"
    API_KEY=$(yaml_get "$CONFIG_FILE" "$DEFAULT_PROVIDER" "api_key")
    BASE_URL=$(yaml_get "$CONFIG_FILE" "$DEFAULT_PROVIDER" "base_url")
    MODEL=$(yaml_get "$CONFIG_FILE" "$DEFAULT_PROVIDER" "model")
fi

# 回退到环境变量
API_KEY="${API_KEY:-${MOONSHOT_API_KEY:-}}"
BASE_URL="${BASE_URL:-https://api.moonshot.cn/}"
MODEL="${MODEL:-kimi-k2.6}"

# 规范化 base_url: 去掉末尾斜杠，确保只加一次 /v1
BASE_URL="${BASE_URL%/}"
if [[ ! "$BASE_URL" == */v1 ]]; then
    BASE_URL="${BASE_URL}/v1"
fi

if [[ -z "$API_KEY" ]]; then
    echo "错误: 找不到 API Key。请设置 MOONSHOT_API_KEY 环境变量或配置 .meta-claw/config.yaml"
    exit 1
fi

echo "============================================"
echo " Moonshot API 流式 vs 非流式 对比测试"
echo "============================================"
echo "Model:    $MODEL"
echo "Base URL: $BASE_URL"
echo "API Key:  ${API_KEY:0:12}..."
echo ""

PROMPT="你好，请简单自我介绍一下。"

# ---------- 测试 1: stream=true ----------
echo "【测试 1】stream=true (SSE 流式)"
echo "-------------------------------------------"

TMPFILE=$(mktemp)
START_TIME=$(perl -MTime::HiRes=time -e 'printf "%.0f", time * 1000')

# 使用 curl -N 禁用缓冲，逐行读取 SSE
curl -s -N "${BASE_URL}/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d "{
    \"model\": \"$MODEL\",
    \"messages\": [
      {\"role\": \"user\", \"content\": \"$PROMPT\"}
    ],
    \"stream\": true,
    \"temperature\":1
  }" | while IFS= read -r line; do
    NOW=$(perl -MTime::HiRes=time -e 'printf "%.0f", time * 1000')
    ELAPSED=$((NOW - START_TIME))
    if [[ "$line" == data:* ]]; then
        DATA="${line#data: }"
        if [[ "$DATA" != "[DONE]" && -n "$DATA" ]]; then
            CONTENT=$(echo "$DATA" | jq -r '.choices[0].delta.content // empty' 2>/dev/null || true)
            if [[ -n "$CONTENT" ]]; then
                printf "%5dms | len=%2d | %s\n" "$ELAPSED" "${#CONTENT}" "$CONTENT" >> "$TMPFILE"
            fi
        fi
    fi
done

END_TIME=$(perl -MTime::HiRes=time -e 'printf "%.0f", time * 1000')
TOTAL_TIME=$((END_TIME - START_TIME))

echo ""
if [[ -s "$TMPFILE" ]]; then
    CHUNK_COUNT=$(wc -l < "$TMPFILE" | tr -d ' ')
    FIRST_CHUNK_TIME=$(head -1 "$TMPFILE" | awk '{print $1}')
    TOTAL_CHARS=$(awk -F'len=' '{sum+=substr($2,1,3)} END {print sum}' "$TMPFILE")
    echo "📊 stream=true 统计:"
    echo "   首 chunk 时间: ${FIRST_CHUNK_TIME}"
    echo "   总 chunk 数:   $CHUNK_COUNT"
    echo "   总耗时:        ${TOTAL_TIME}ms"
    echo "   总字符数:      $TOTAL_CHARS"
    echo ""
    echo "📋 每个 chunk 详情:"
    cat "$TMPFILE"
else
    echo "⚠️ 没有收到任何 chunk 数据（可能是 API 返回了错误）"
fi

rm -f "$TMPFILE"

echo ""
echo "============================================"

# ---------- 测试 2: stream=false ----------
echo "【测试 2】stream=false (非流式)"
echo "-------------------------------------------"

START_TIME2=$(perl -MTime::HiRes=time -e 'printf "%.0f", time * 1000')

RESPONSE=$(curl -s "${BASE_URL}/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d "{
    \"model\": \"$MODEL\",
    \"messages\": [
      {\"role\": \"user\", \"content\": \"$PROMPT\"}
    ],
    \"stream\": false,
    \"temperature\": 1
  }")

END_TIME2=$(perl -MTime::HiRes=time -e 'printf "%.0f", time * 1000')
TOTAL_TIME2=$((END_TIME2 - START_TIME2))

# 检查是否返回了错误
ERROR_MSG=$(echo "$RESPONSE" | jq -r '.error.message // empty' 2>/dev/null || true)
if [[ -n "$ERROR_MSG" ]]; then
    echo "❌ API 错误: $ERROR_MSG"
    echo "原始响应: $RESPONSE"
else
    CONTENT2=$(echo "$RESPONSE" | jq -r '.choices[0].message.content // empty' 2>/dev/null || true)
    echo ""
    echo "📊 stream=false 统计:"
    echo "   总耗时:        ${TOTAL_TIME2}ms"
    echo "   总字符数:      ${#CONTENT2}"
    echo ""
    echo "📋 响应内容:"
    echo "$CONTENT2"
fi

echo ""
echo "============================================"
echo "【对比总结】"
if [[ -n "${FIRST_CHUNK_TIME:-}" ]]; then
    echo "   stream=true  首 chunk: $FIRST_CHUNK_TIME | 总耗时: ${TOTAL_TIME}ms"
fi
echo "   stream=false 总耗时:    ${TOTAL_TIME2}ms"
echo "============================================"
