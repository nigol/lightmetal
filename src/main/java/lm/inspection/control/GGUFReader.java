package lm.inspection.control;

import module java.base;
import lm.inspection.entity.GGUFMetadata;

public interface GGUFReader {

    int MAGIC = 'G' | ('G' << 8) | ('U' << 16) | ('F' << 24);
    long MAX_HEADER_MAP = 256L * 1024 * 1024;
    int MIN_VERSION = 2;
    int MAX_VERSION = 3;

    static GGUFMetadata read(Path file) {
        try (var ch = FileChannel.open(file, StandardOpenOption.READ)) {
            var size = Math.min(ch.size(), MAX_HEADER_MAP);
            var buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, size).order(ByteOrder.LITTLE_ENDIAN);
            return parse(buf);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read GGUF: " + file, e);
        }
    }

    static GGUFMetadata parse(ByteBuffer buf) {
        var magic = buf.getInt();
        if (magic != MAGIC)
            throw new IllegalArgumentException("not a GGUF file (magic=0x" + Integer.toHexString(magic) + ")");
        var version = buf.getInt();
        if (version < MIN_VERSION || version > MAX_VERSION)
            throw new IllegalArgumentException("unsupported GGUF version: " + version + " (only v2 and v3 supported)");
        var tensorCount = buf.getLong();
        var kvCount = buf.getLong();
        var kvs = new LinkedHashMap<String, Object>();
        for (var i = 0L; i < kvCount; i++) {
            var key = readString(buf);
            kvs.put(key, Type.from(buf.getInt()).read(buf));
        }
        return new GGUFMetadata(version, tensorCount, Map.copyOf(kvs));
    }

    static String readString(ByteBuffer buf) {
        var bytes = new byte[boundedInt(buf.getLong(), "string")];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static Object[] readArray(ByteBuffer buf) {
        var elemType = Type.from(buf.getInt());
        var out = new Object[boundedInt(buf.getLong(), "array")];
        for (var i = 0; i < out.length; i++) out[i] = elemType.read(buf);
        return out;
    }

    private static int boundedInt(long len, String kind) {
        if (len < 0 || len > Integer.MAX_VALUE)
            throw new IllegalArgumentException("invalid " + kind + " length: " + len);
        return (int) len;
    }

    enum Type {
        U8(b -> Byte.toUnsignedInt(b.get())),
        I8(b -> (int) b.get()),
        U16(b -> Short.toUnsignedInt(b.getShort())),
        I16(b -> (int) b.getShort()),
        U32(b -> Integer.toUnsignedLong(b.getInt())),
        I32(ByteBuffer::getInt),
        F32(ByteBuffer::getFloat),
        BOOL(b -> b.get() != 0),
        STRING(GGUFReader::readString),
        ARRAY(GGUFReader::readArray),
        U64(ByteBuffer::getLong),
        I64(ByteBuffer::getLong),
        F64(ByteBuffer::getDouble);

        final Function<ByteBuffer, Object> reader;

        Type(Function<ByteBuffer, Object> reader) {
            this.reader = reader;
        }

        static Type from(int tag) {
            var all = values();
            if (tag < 0 || tag >= all.length)
                throw new IllegalArgumentException("unknown GGUF value type: " + tag);
            return all[tag];
        }

        Object read(ByteBuffer buf) {
            return reader.apply(buf);
        }
    }
}
