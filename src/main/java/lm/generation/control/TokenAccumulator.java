package lm.generation.control;

import java.util.function.Consumer;

import lm.configuration.entity.Token;

public final class TokenAccumulator implements Consumer<Token> {

    final StringBuilder text = new StringBuilder();
    long count;

    @Override
    public void accept(Token token) {
        text.append(token.text());
        count++;
    }

    public String text() {
        return text.toString();
    }

    public long count() {
        return count;
    }
}
