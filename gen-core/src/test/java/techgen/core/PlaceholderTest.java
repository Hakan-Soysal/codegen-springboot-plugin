package techgen.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaceholderTest {

    @Test
    void moduleNameIsGenCore() {
        assertEquals("gen-core", Placeholder.moduleName());
    }
}
