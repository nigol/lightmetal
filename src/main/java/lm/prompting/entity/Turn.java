package lm.prompting.entity;

public sealed interface Turn permits UserText, AssistantText, AssistantToolCalls, UserToolResults {}
