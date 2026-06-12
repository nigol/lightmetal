import java.util.ServiceLoader;
import java.util.function.UnaryOperator;

import lm.generation.boundary.LightMetalChat;

void main() {
    testSpiDiscovery();
    testReturnsLightMetalChatInstance();
    IO.println("[ok] LightMetalChat tests");
}

void testSpiDiscovery() {
    var found = false;
    for (var op : ServiceLoader.load(UnaryOperator.class)) {
        if (op instanceof LightMetalChat) {
            found = true;
            break;
        }
    }
    if (!found)
        throw new AssertionError(
                "LightMetalChat not discoverable via ServiceLoader<UnaryOperator> — "
                        + "check META-INF/services/java.util.function.UnaryOperator is on the classpath");
}

void testReturnsLightMetalChatInstance() {
    var provider = ServiceLoader.load(UnaryOperator.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(LightMetalChat.class::isInstance)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no LightMetalChat registered"));
    if (!(provider instanceof UnaryOperator<?>))
        throw new AssertionError("registered provider does not implement UnaryOperator");
}
