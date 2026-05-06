#!/bin/bash

# 测试 Moonshot API Key 是否有效

API_KEY="sk-XLCPmq58sRZgljmvYv7RdosP6kIK2flJLoqSBdYZo8V0ZddN"
BASE_URL="https://api.moonshot.cn/v1"

echo "测试 Moonshot API..."
echo "API Key 前缀: ${API_KEY:0:12}..."
echo "Base URL: $BASE_URL"
echo ""

# 发送测试请求
curl -X POST "$BASE_URL/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{
    "model": "kimi-k2.6",
    "messages": [
      {"role": "user", "content": "Hello"}
    ],
    "max_tokens": 10
  }' \
  -v

echo ""
echo "如果返回 401 错误，说明 API Key 无效或已过期"
echo "如果返回正常响应，说明 API Key 有效"
