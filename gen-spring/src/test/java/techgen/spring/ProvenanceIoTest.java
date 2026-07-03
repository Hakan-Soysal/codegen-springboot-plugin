package techgen.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2.4 — ProvenanceIo testleri (davranış sözleşmesi §5): yaz-oku round-trip, bozuk dosya → null,
 * ordinal sıralama determinizmi.
 */
class ProvenanceIoTest {

    @Test
    void writeThenTryRead_roundTripsEqual(@TempDir Path outDir) throws IOException {
        Provenance provenance = new Provenance("techgen-spring", "0.1.0-SNAPSHOT", List.of(
                new ProvenanceEntry("src/main/java/app/Foo.java", "Generated", "abc123"),
                new ProvenanceEntry("src/main/java/app/Bar.java", "Generated", "def456")));

        ProvenanceIo.write(outDir, provenance);
        Provenance read = ProvenanceIo.tryRead(outDir);

        assertEquals("techgen-spring", read.generator());
        assertEquals("0.1.0-SNAPSHOT", read.version());
        assertEquals(2, read.files().size());
        assertEquals(
                List.of(
                        new ProvenanceEntry("src/main/java/app/Bar.java", "Generated", "def456"),
                        new ProvenanceEntry("src/main/java/app/Foo.java", "Generated", "abc123")),
                read.files());
    }

    @Test
    void write_producesFileEndingWithSingleNewline(@TempDir Path outDir) throws IOException {
        ProvenanceIo.write(outDir, new Provenance("techgen-spring", "0.1.0", List.of()));

        String content = Files.readString(outDir.resolve(ProvenanceIo.FILE_NAME), StandardCharsets.UTF_8);

        assertTrue(content.endsWith("\n"));
        assertTrue(!content.endsWith("\n\n"));
    }

    @Test
    void write_leavesNoTempFilesBehind(@TempDir Path outDir) throws IOException {
        ProvenanceIo.write(outDir, new Provenance("techgen-spring", "0.1.0", List.of()));

        try (var stream = Files.list(outDir)) {
            List<Path> files = stream.toList();
            assertEquals(1, files.size());
            assertEquals(ProvenanceIo.FILE_NAME, files.get(0).getFileName().toString());
        }
    }

    @Test
    void tryRead_missingFile_returnsNull(@TempDir Path outDir) {
        assertNull(ProvenanceIo.tryRead(outDir));
    }

    @Test
    void tryRead_corruptFile_returnsNull(@TempDir Path outDir) throws IOException {
        Files.writeString(outDir.resolve(ProvenanceIo.FILE_NAME), "{ this is not valid json ]]]");

        assertNull(ProvenanceIo.tryRead(outDir));
    }

    @Test
    void tryRead_wrongSchema_returnsNull(@TempDir Path outDir) throws IOException {
        // Geçerli JSON ama Provenance şemasına uymuyor (files bir obje, dizi değil).
        Files.writeString(outDir.resolve(ProvenanceIo.FILE_NAME),
                "{\"generator\":\"x\",\"version\":\"y\",\"files\":{\"a\":1}}\n");

        assertNull(ProvenanceIo.tryRead(outDir));
    }

    @Test
    void write_sortsEntriesByPathOrdinalRegardlessOfInputOrder(@TempDir Path outDir) throws IOException {
        Provenance unsorted = new Provenance("techgen-spring", "0.1.0", List.of(
                new ProvenanceEntry("z.java", "Generated", "1"),
                new ProvenanceEntry("a.java", "Generated", "2"),
                new ProvenanceEntry("m/b.java", "Generated", "3")));

        ProvenanceIo.write(outDir, unsorted);
        Provenance read = ProvenanceIo.tryRead(outDir);

        List<String> paths = read.files().stream().map(ProvenanceEntry::path).toList();
        assertEquals(List.of("a.java", "m/b.java", "z.java"), paths);
    }

    @Test
    void write_isDeterministicAcrossRepeatedCallsWithSameInput(@TempDir Path outDir) throws IOException {
        Provenance provenance = new Provenance("techgen-spring", "0.1.0", List.of(
                new ProvenanceEntry("b.java", "Generated", "2"),
                new ProvenanceEntry("a.java", "Generated", "1")));

        ProvenanceIo.write(outDir, provenance);
        String first = Files.readString(outDir.resolve(ProvenanceIo.FILE_NAME));

        ProvenanceIo.write(outDir, provenance);
        String second = Files.readString(outDir.resolve(ProvenanceIo.FILE_NAME));

        assertEquals(first, second);
    }

    @Test
    void write_onlyIncludesEntriesGivenByCaller_doesNotFilterItself(@TempDir Path outDir) throws IOException {
        // ProvenanceIo yalnız IO yapar; "yalnız Generated yazılır" filtrelemesi çağıranın
        // sorumluluğudur (T3.1). Bu test IO katmanının filtreleme YAPMADIĞINI doğrular.
        Provenance provenance = new Provenance("techgen-spring", "0.1.0", List.of(
                new ProvenanceEntry("seam.java", "HumanSeam", "1")));

        ProvenanceIo.write(outDir, provenance);
        Provenance read = ProvenanceIo.tryRead(outDir);

        assertEquals(1, read.files().size());
        assertEquals("HumanSeam", read.files().get(0).clazz());
    }
}
