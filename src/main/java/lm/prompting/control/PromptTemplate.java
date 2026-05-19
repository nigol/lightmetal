package lm.prompting.control;

public final class PromptTemplate {

    private PromptTemplate() {}

    public static String mistralInstruct(String userPrompt) {
        return "[INST] " + userPrompt + " [/INST]";
    }
}
