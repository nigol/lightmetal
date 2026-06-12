package lm.prompting.control;

import java.util.List;
import java.util.stream.Stream;

import lm.configuration.entity.Token;
import lm.prompting.entity.Turn;
import lm.tools.control.ToolCallParser;
import lm.tools.entity.Tool;

public sealed interface ChatTemplate permits Mistral4ChatTemplate, GemmaChatTemplate {

    String render(String system, List<Tool> tools, List<Turn> turns);

    ToolCallParser.Parsed parse(String generated);

    default List<String> stopSequences() {
        return List.of();
    }

    default Stream<Token> tagChannels(Stream<Token> tokens) {
        return tokens;
    }
}
