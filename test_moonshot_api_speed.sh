#!/bin/bash
# 测试 Moonshot API 响应速度（排除 Java 因素）
# 用法: ./test_moonshot_api_speed.sh

echo "=========================================="
echo "Moonshot API Performance Test"
echo "=========================================="
echo ""

# 从配置文件读取 API Key
CONFIG_FILE="$HOME/.meta-claw/config.yaml"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Config file not found: $CONFIG_FILE"
    echo "Please run 'meta-claw init' first."
    exit 1
fi

# 提取 moonshot API key（简单 grep，可能需要根据实际情况调整）
API_KEY=$(grep -A 5 "moonshot:" "$CONFIG_FILE" | grep "api_key:" | head -1 | sed 's/.*api_key:[[:space:]]*//' | tr -d '"' | tr -d "'")

if [ -z "$API_KEY" ] || [[ "$API_KEY" == *"your-api-key"* ]]; then
    echo "Error: API key not configured or still using placeholder."
    echo "Please update your API key in $CONFIG_FILE"
    exit 1
fi

echo "Using API key: ${API_KEY:0:8}..."
echo ""

BASE_URL="https://api.moonshot.cn/v1/chat/completions"
MODEL="moonshot-v1-8k"
TEST_COUNT=3

echo "Running $TEST_COUNT consecutive requests with 5s interval..."
echo ""

for i in $(seq 1 $TEST_COUNT); do
    echo "--- Request #$i ---"
    
    START_TIME=$(date +%s%N)
    
    # 发送流式请求
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL" \
        -H "Authorization: Bearer $API_KEY" \
        -H "Content-Type: application/json" \
        -d "{
            \"model\": \"$MODEL\",
            \"messages\": [{\"role\": \"user\", \"content\": \"你好，请简单回复\"}],
            \"stream\": true,
            \"temperature\": 0.7
        }" \
        --max-time 30)
    
    END_TIME=$(date +%s%N)
    
    # 计算耗时（毫秒）
    ELAPSED=$(( (END_TIME - START_TIME) / 1000000 ))
    
    # 提取 HTTP 状态码
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    
    echo "Response time: ${ELAPSED}ms"
    echo "HTTP Status: $HTTP_CODE"
    
    if [ "$HTTP_CODE" = "200" ]; then
        echo "Status: ✅ Success"
        
        # 判断性能等级
        if [ $ELAPSED -lt 2000 ]; then
            echo "Performance: 🟢 Excellent (< 2s)"
        elif [ $ELAPSED -lt 5000 ]; then
            echo "Performance: 🟡 Good (2-5s)"
        elif [ $ELAPSED -lt 10000 ]; then
            echo "Performance: 🟠 Slow (5-10s)"
        else
            echo "Performance: 🔴 Very Slow (> 10s) - API may be rate-limited"
        fi
    else
        echo "Status: ❌ Failed"
        echo "Response preview: $(echo "$RESPONSE" | head -5)"
    fi
    
    echo ""
    
    # 等待 5 秒再发起下一次请求
    if [ $i -lt $TEST_COUNT ]; then
        echo "Waiting 5 seconds before next request..."
        sleep 5
        echo ""
    fi
done

echo "=========================================="
echo "Test completed!"
echo "=========================================="
echo ""
echo "Analysis:"
echo "  - If all requests are slow (> 5s): Moonshot API server is overloaded or your API key is rate-limited"
echo "  - If only later requests are slow: You hit the rate limit, need to add delays between requests"
echo "  - If curl is fast but Java is slow: The problem is in Java client configuration"
echo ""
echo "Next steps:"
echo "  1. Check your API quota at https://platform.moonshot.cn/"
echo "  2. Consider upgrading to a paid plan if using free tier"
echo "  3. Try using a different API key"
echo "  4. Add request throttling in your application"
