package lm.prompting.entity;

import java.util.List;

import lm.tools.entity.ToolCall;

public record AssistantToolCalls(String text, List<ToolCall> calls) implements Turn {}
