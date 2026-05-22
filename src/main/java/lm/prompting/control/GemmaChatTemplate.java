package lm.prompting.control;

import java.util.List;

import lm.http.entity.AnthropicMessagesRequest.Turn;
import lm.tools.control.ToolCallParser;
import lm.tools.entity.Tool;

public final class GemmaChatTemplate implements ChatTemplate {

    @Override
    public String render(String system, List<Tool> tools, List<Turn> turns) {
        throw new UnsupportedOperationException("gemma4 template not implemented yet");
    }

    @Override
    public ToolCallParser.Parsed parse(String generated) {
        return new ToolCallParser.Text(generated == null ? "" : generated);
    }
}
