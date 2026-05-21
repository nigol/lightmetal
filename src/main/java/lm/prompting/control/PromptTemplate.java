package lm.prompting.control;

import java.util.List;

public interface PromptTemplate {

    public static String mistralInstruct(String userPrompt) {
        return "[INST] " + userPrompt + " [/INST]";
    }

    public static String mistralChat(String system, List<String> alternating) {
        if (alternating == null || alternating.isEmpty()) {
            return mistralInstruct("");
        }
        var sys = (system == null || system.isBlank()) ? "" : system.strip() + "\n\n";
        var sb = new StringBuilder();
        sb.append("<s>[INST] ").append(sys).append(alternating.get(0)).append(" [/INST]");
        for (var i = 1; i < alternating.size(); i++) {
            var turn = alternating.get(i);
            if (i % 2 == 1) {
                sb.append(' ').append(turn).append(" </s>");
            } else {
                sb.append("[INST] ").append(turn).append(" [/INST]");
            }
        }
        return sb.toString();
    }
}
