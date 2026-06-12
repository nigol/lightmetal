package lm.prompting.entity;

import java.util.List;

public record UserToolResults(List<ToolResult> results) implements Turn {}
