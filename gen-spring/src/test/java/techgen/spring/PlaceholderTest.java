package techgen.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaceholderTest {

    @Test
    void moduleNameIsGenSpring() {
        assertEquals("gen-spring", Placeholder.moduleName());
    }
}
