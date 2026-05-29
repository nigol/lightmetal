package lm.backend.control;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import lm.logging.control.Log;

final class LlamaLog {

    private static final SymbolLookup LOOKUP = SymbolLookup.libraryLookup(
            "/opt/homebrew/opt/llama.cpp/lib/libllama.dylib", Arena.global());

    private static final MethodHandle LLAMA_LOG_SET = Linker.nativeLinker().downcallHandle(
            LOOKUP.findOrThrow("llama_log_set"),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

    private static final FunctionDescriptor CALLBACK_DESC =
            FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS, ADDRESS);

    static void install() {
        try {
            var target = MethodHandles.lookup().findStatic(
                    LlamaLog.class, "tick",
                    MethodType.methodType(void.class, int.class, MemorySegment.class, MemorySegment.class));
            var stub = Linker.nativeLinker().upcallStub(target, CALLBACK_DESC, Arena.global());
            LLAMA_LOG_SET.invoke(stub, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new IllegalStateException("failed to install llama log callback", t);
        }
    }

    static void done() {
        Log.progressDone();
    }

    private static void tick(int level, MemorySegment text, MemorySegment userData) {
        Log.progress();
    }
}
