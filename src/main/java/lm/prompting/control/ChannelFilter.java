package lm.prompting.control;

import java.util.ArrayList;
import java.util.List;

import lm.configuration.entity.Token;

// Tags streamed tokens with their channel for templates whose generation prompt
// prefills the thinking marker: the model starts inside the thought channel, so
// every token is "thought" until the close marker switches to "final". The marker
// may be split across token pieces — the trailing bytes that could be a partial
// match stay buffered until disambiguated; flush() emits the leftovers when the
// stream ends.
final class ChannelFilter {

    private final String closeMarker;
    private final StringBuilder pending = new StringBuilder();
    private String channel;
    private int lastId;

    ChannelFilter(String closeMarker, boolean enableThinking) {
        this.closeMarker = closeMarker;
        this.channel = enableThinking ? "thought" : "final";
    }

    List<Token> consume(Token in) {
        lastId = in.id();
        pending.append(in.text());
        var out = new ArrayList<Token>();
        while (true) {
            var idx = pending.indexOf(closeMarker);
            if (idx >= 0) {
                if (idx > 0) {
                    out.add(new Token(in.id(), pending.substring(0, idx), channel));
                }
                pending.delete(0, idx + closeMarker.length());
                channel = "final";
                continue;
            }
            var safeLen = pending.length() - (closeMarker.length() - 1);
            if (safeLen > 0) {
                out.add(new Token(in.id(), pending.substring(0, safeLen), channel));
                pending.delete(0, safeLen);
            }
            return out;
        }
    }

    List<Token> flush() {
        if (pending.isEmpty()) return List.of();
        var out = List.of(new Token(lastId, pending.toString(), channel));
        pending.setLength(0);
        return out;
    }
}
