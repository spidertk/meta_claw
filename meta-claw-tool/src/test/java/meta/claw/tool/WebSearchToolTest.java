package meta.claw.tool;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSearchToolTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private static HttpClient mockClientFor(HttpResponse<String> response) throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return mockClient;
    }

    @Test
    void fetchPageStripsHtmlAndTruncates() throws Exception {
        WebSearchTool tool = new WebSearchTool();
        HttpClient mockClient = mockClientFor(mockResponse(200, "<html><body><p>Hello World</p></body></html>"));
        ReflectionTestUtils.setField(tool, "httpClient", mockClient);

        String result = tool.fetchPage("https://example.com", 20);
        assertTrue(result.contains("Hello World"), "expected plain text but got: " + result);
        assertFalse(result.contains("<p>"), "expected HTML tags stripped but got: " + result);
    }

    @Test
    void searchWebParsesDuckDuckGoHtml() throws Exception {
        WebSearchTool tool = new WebSearchTool();
        String html = """
                <a class="result__a" href="https://example.com/page">Example Page</a>
                <a class="result__snippet">This is a snippet.</a>
                """;
        HttpClient mockClient = mockClientFor(mockResponse(200, html));
        ReflectionTestUtils.setField(tool, "httpClient", mockClient);

        String result = tool.searchWeb("test", 1);
        assertTrue(result.contains("Example Page"), "expected title but got: " + result);
        assertTrue(result.contains("https://example.com/page"), "expected URL but got: " + result);
        assertTrue(result.contains("This is a snippet"), "expected snippet but got: " + result);
    }

    @Test
    void fetchPageReturnsErrorOnHttpFailure() throws Exception {
        WebSearchTool tool = new WebSearchTool();
        HttpClient mockClient = mockClientFor(mockResponse(404, "Not Found"));
        ReflectionTestUtils.setField(tool, "httpClient", mockClient);

        String result = tool.fetchPage("https://example.com/missing", 100);
        assertTrue(result.startsWith("Error"), "expected error but got: " + result);
    }
}
