package meta.claw.tool.sample;

import meta.claw.tool.annotation.Tool;
import meta.claw.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 示例工具：安全计算器。
 * 仅支持 + - * / 和括号，不支持函数调用或变量，避免脚本注入风险。
 */
@Component
public class CalculatorTool {

    @Tool(name = "calculator", description = "Evaluate a simple math expression with + - * / and parentheses")
    public String calculate(
            @ToolParam(description = "Math expression like '1 + 2 * (3 - 4)'") String expression) {
        if (expression == null || expression.isBlank()) {
            return "Error: empty expression";
        }
        try {
            double result = evaluate(expression);
            // 如果结果是整数，返回整数形式
            if (result == Math.floor(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private double evaluate(String expr) {
        String tokens = expr.replaceAll("\\s+", "");
        Deque<Double> values = new ArrayDeque<>();
        Deque<Character> ops = new ArrayDeque<>();

        for (int i = 0; i < tokens.length(); i++) {
            char ch = tokens.charAt(i);
            if (ch >= '0' && ch <= '9' || ch == '.') {
                StringBuilder num = new StringBuilder();
                while (i < tokens.length() && (tokens.charAt(i) >= '0' && tokens.charAt(i) <= '9' || tokens.charAt(i) == '.')) {
                    num.append(tokens.charAt(i));
                    i++;
                }
                i--;
                values.push(Double.parseDouble(num.toString()));
            } else if (ch == '(') {
                ops.push(ch);
            } else if (ch == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') {
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                }
                ops.pop(); // pop '('
            } else if (isOperator(ch)) {
                while (!ops.isEmpty() && precedence(ops.peek()) >= precedence(ch)) {
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                }
                ops.push(ch);
            } else {
                throw new IllegalArgumentException("Invalid character: " + ch);
            }
        }

        while (!ops.isEmpty()) {
            values.push(applyOp(ops.pop(), values.pop(), values.pop()));
        }
        return values.pop();
    }

    private boolean isOperator(char ch) {
        return ch == '+' || ch == '-' || ch == '*' || ch == '/';
    }

    private int precedence(char op) {
        return switch (op) {
            case '+', '-' -> 1;
            case '*', '/' -> 2;
            default -> 0;
        };
    }

    private double applyOp(char op, double b, double a) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> {
                if (b == 0) throw new ArithmeticException("Division by zero");
                yield a / b;
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        };
    }
}
