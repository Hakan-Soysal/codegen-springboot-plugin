package techgen.conformance;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T8.2 — {@link Main} CLI birim testleri: usage/parse/load hata yolları (davranış sözleşmesi
 * §A.4). Gerçek {@link GeneratedApp} yükleme yolu (0/1 exit) T8.2 acceptance testlerinde
 * ({@link ConformanceAcceptanceTest}) {@link SpecRunner} üzerinden zaten koşulur — burada
 * yalnız {@code Main}'in kendi arg/dosya/parse hata sözleşmesi doğrulanır.
 */
class MainTest {

    private record Captured(int exit, String out, String err) {
    }

    private static Captured run(String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int exit = Main.run(args, new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        return new Captured(exit, outBuf.toString(StandardCharsets.UTF_8), errBuf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void missingArgs_exitsTwo_withUsage() {
        Captured c = run("only-one-arg");
        assertEquals(2, c.exit());
        assertTrue(c.err().contains("usage:"), "stderr:\n" + c.err());
    }

    @Test
    void noArgs_exitsTwo_withUsage() {
        Captured c = run();
        assertEquals(2, c.exit());
        assertTrue(c.err().contains("usage:"), "stderr:\n" + c.err());
    }

    @Test
    void specsPathNeitherFileNorDirectory_exitsTwo() {
        Captured c = run("irrelevant-classpath", "/no/such/path/should/exist");
        assertEquals(2, c.exit());
        assertTrue(c.err().contains("ERROR"), "stderr:\n" + c.err());
    }

    @Test
    void specsDirectoryWithoutJsonFiles_exitsTwo(@TempDir Path emptyDir) {
        Captured c = run("irrelevant-classpath", emptyDir.toString());
        assertEquals(2, c.exit());
        assertTrue(c.err().contains("bulunamadı"), "stderr:\n" + c.err());
    }

    @Test
    void malformedSpecJson_exitsTwo(@TempDir Path dir) throws Exception {
        Path spec = dir.resolve("bad.json");
        Files.writeString(spec, "{ bu gecerli json degil");

        Captured c = run("irrelevant-classpath", spec.toString());
        assertEquals(2, c.exit());
        assertTrue(c.err().contains("parse edilemedi"), "stderr:\n" + c.err());
    }

    @Test
    void appClasspathCannotLoad_exitsTwo(@TempDir Path dir) throws Exception {
        Path spec = dir.resolve("stub.json");
        Files.writeString(spec, """
                {"construct":"saga","opId":"X","arrange":{},"act":{"call":"X","with":{}},
                 "assert":{"stub":true,"expected":"deferred"}}
                """);

        Captured c = run("/no/such/app/classes", spec.toString());
        assertEquals(2, c.exit(), "stdout:\n" + c.out() + "\nstderr:\n" + c.err());
        assertTrue(c.err().contains("yüklenemedi"), "stderr:\n" + c.err());
    }
}
