package techgen.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaceholderTest {

    @Test
    void moduleNameIsGenCli() {
        assertEquals("gen-cli", Placeholder.moduleName());
    }
}
