package lm.configuration.entity;

public record Token(int id, String text, String channel) {
    public Token(int id, String text) {
        this(id, text, "");
    }
}
