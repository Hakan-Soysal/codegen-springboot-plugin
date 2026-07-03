package techgen.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaceholderTest {

    @Test
    void moduleNameIsConformance() {
        assertEquals("conformance", Placeholder.moduleName());
    }
}
