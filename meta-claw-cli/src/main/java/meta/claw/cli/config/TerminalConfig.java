package meta.claw.cli.config;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * CLI 终端与行读取器配置。
 * <p>把 Terminal / LineReader 注册为 Spring bean，供 ChatCommand、CliHitlGate 等
 * 需要直接操作控制台的组件复用，避免重复构造导致输入流竞争。</p>
 */
@Configuration
public class TerminalConfig {

    @Bean
    public Terminal terminal() throws IOException {
        return TerminalBuilder.builder()
                .system(true)
                .dumb(true)
                .build();
    }

    @Bean
    public LineReader lineReader(Terminal terminal) {
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }
}
