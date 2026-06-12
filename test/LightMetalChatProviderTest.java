import java.util.ServiceLoader;
import java.util.function.UnaryOperator;

import lm.generation.boundary.LightMetalChatProvider;

void main() {
    testSpiDiscovery();
    testReturnsLightMetalChatProviderInstance();
    IO.println("[ok] LightMetalChatProvider tests");
}

void testSpiDiscovery() {
    var found = false;
    for (var op : ServiceLoader.load(UnaryOperator.class)) {
        if (op instanceof LightMetalChatProvider) {
            found = true;
            break;
        }
    }
    if (!found)
        throw new AssertionError(
                "LightMetalChatProvider not discoverable via ServiceLoader<UnaryOperator> — "
                        + "check META-INF/services/java.util.function.UnaryOperator is on the classpath");
}

void testReturnsLightMetalChatProviderInstance() {
    var provider = ServiceLoader.load(UnaryOperator.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(LightMetalChatProvider.class::isInstance)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no LightMetalChatProvider registered"));
    if (!(provider instanceof UnaryOperator<?>))
        throw new AssertionError("registered provider does not implement UnaryOperator");
}
