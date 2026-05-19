package lm.tokenization.control;

import static lm.backend.ffm.llama_h.llama_h.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public final class Tokenizer {

    private static final int PIECE_BUF = 256;

    private final MemorySegment vocab;

    public Tokenizer(MemorySegment vocab) {
        this.vocab = vocab;
    }

    public int[] tokenize(String text, boolean addBos, boolean parseSpecial) {
        try (var arena = Arena.ofConfined()) {
            var utf8 = text.getBytes(StandardCharsets.UTF_8);
            var textSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, utf8);
            var capacity = utf8.length + 16;
            for (var attempt = 0; attempt < 2; attempt++) {
                var tokens = arena.allocate(ValueLayout.JAVA_INT, capacity);
                var written = llama_tokenize(vocab, textSeg, utf8.length, tokens, capacity, addBos, parseSpecial);
                if (written >= 0) {
                    var result = new int[written];
                    MemorySegment.copy(tokens, ValueLayout.JAVA_INT, 0, result, 0, written);
                    return result;
                }
                capacity = -written;
            }
            throw new IllegalStateException("tokenize: buffer sizing failed for text of length " + utf8.length);
        }
    }

    public String tokenToPiece(int token, boolean special) {
        try (var arena = Arena.ofConfined()) {
            var buf = arena.allocate(ValueLayout.JAVA_BYTE, PIECE_BUF);
            var written = llama_token_to_piece(vocab, token, buf, PIECE_BUF, 0, special);
            if (written < 0) {
                var bigger = arena.allocate(ValueLayout.JAVA_BYTE, -written);
                written = llama_token_to_piece(vocab, token, bigger, -written, 0, special);
                buf = bigger;
            }
            if (written <= 0) return "";
            var bytes = new byte[written];
            MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 0, bytes, 0, written);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public String detokenize(int[] tokens, boolean removeSpecial) {
        var out = new StringBuilder();
        for (var t : tokens) out.append(tokenToPiece(t, !removeSpecial));
        return out.toString();
    }
}
