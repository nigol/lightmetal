import java.util.ServiceLoader;
import java.util.function.BinaryOperator;

import lm.generation.boundary.LightMetalText;

void main() {
    testSpiDiscovery();
    testReturnsLightMetalTextInstance();
    IO.println("[ok] LightMetalText tests");
}

void testSpiDiscovery() {
    var found = false;
    for (var op : ServiceLoader.load(BinaryOperator.class)) {
        if (op instanceof LightMetalText) {
            found = true;
            break;
        }
    }
    if (!found)
        throw new AssertionError(
                "LightMetalText not discoverable via ServiceLoader<BinaryOperator> — "
                        + "check META-INF/services/java.util.function.BinaryOperator is on the classpath");
}

void testReturnsLightMetalTextInstance() {
    var provider = ServiceLoader.load(BinaryOperator.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(LightMetalText.class::isInstance)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no LightMetalText registered"));
    if (!(provider instanceof BinaryOperator<?>))
        throw new AssertionError("registered provider does not implement BinaryOperator");
}
