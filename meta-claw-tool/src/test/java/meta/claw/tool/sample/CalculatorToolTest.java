package meta.claw.tool.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorToolTest {

    private final CalculatorTool calculator = new CalculatorTool();

    @Test
    void calculate_shouldAdd() {
        assertEquals("3", calculator.calculate("1 + 2"));
    }

    @Test
    void calculate_shouldSubtract() {
        assertEquals("5", calculator.calculate("10 - 5"));
    }

    @Test
    void calculate_shouldMultiply() {
        assertEquals("12", calculator.calculate("3 * 4"));
    }

    @Test
    void calculate_shouldDivide() {
        assertEquals("2.5", calculator.calculate("5 / 2"));
    }

    @Test
    void calculate_shouldHandleParentheses() {
        assertEquals("9", calculator.calculate("(1 + 2) * 3"));
    }

    @Test
    void calculate_shouldRespectPrecedence() {
        assertEquals("14", calculator.calculate("2 + 3 * 4"));
    }

    @Test
    void calculate_shouldReturnIntegerWhenPossible() {
        assertEquals("4", calculator.calculate("8 / 2"));
    }

    @Test
    void calculate_shouldHandleDivisionByZero() {
        String result = calculator.calculate("1 / 0");
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void calculate_shouldHandleEmptyExpression() {
        assertEquals("Error: empty expression", calculator.calculate(""));
        assertEquals("Error: empty expression", calculator.calculate("   "));
    }

    @Test
    void calculate_shouldHandleNullExpression() {
        assertEquals("Error: empty expression", calculator.calculate(null));
    }

    @Test
    void calculate_shouldHandleInvalidCharacter() {
        String result = calculator.calculate("1 + a");
        assertTrue(result.startsWith("Error:"));
    }
}
