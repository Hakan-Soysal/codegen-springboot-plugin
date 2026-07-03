package techgen.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3.1 — EmitWriter testleri (davranış sözleşmesi §3 INV-S, §6.1-6.2): GenHeader disposition,
 * write-only-if-changed (mtime), writeIfAbsent ezmeme, orphan prune (human dosyaya dokunmama
 * dahil), bozuk-provenance→prune-atlandı, migrateSeamIfFlat, provenance determinizmi.
 */
class EmitWriterTest {

    private static final Pattern LOWERCASE_HEX_SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    @Test
    void writeAlways_javaFileGetsGenHeader(@TempDir Path outDir) throws IOException {
        EmitWriter writer = new EmitWriter(outDir);

        writer.writeAlways("gen/java/app/Foo.java", "package app;\n");

        String content = Files.readString(outDir.resolve("gen/java/app/Foo.java"), StandardCharsets.UTF_8);
        assertTrue(content.startsWith(EmitWriter.GEN_HEADER));
        assertEquals(EmitWriter.GEN_HEADER + "package app;\n", content);
    }

    @Test
    void writeAlways_xmlAndPomDoNotGetGenHeader(@TempDir Path outDir) throws IOException {
        EmitWriter writer = new EmitWriter(outDir);

        writer.writeAlways("gen/parent/pom.xml", "<project/>\n");
        writer.writeAlways("gen/foo.xml", "<foo/>\n");

        String pom = Files.readString(outDir.resolve("gen/parent/pom.xml"), StandardCharsets.UTF_8);
        String xml = Files.readString(outDir.resolve("gen/foo.xml"), StandardCharsets.UTF_8);
        assertEquals("<project/>\n", pom);
        assertEquals("<foo/>\n", xml);
        assertFalse(pom.contains("auto-generated"));
        assertFalse(xml.contains("auto-generated"));
    }

    @Test
    void writeAlways_sameContentDoesNotTouchMtime(@TempDir Path outDir) throws IOException {
        EmitWriter writer = new EmitWriter(outDir);
        writer.writeAlways("gen/java/app/Foo.java", "package app;\n");
        Path file = outDir.resolve("gen/java/app/Foo.java");

        // mtime'ı elle 5 sn geriye al (flaky olmasın diye gerçek zaman aralığına güvenmiyoruz).
        FileTime past = FileTime.from(Instant.now().minus(5, ChronoUnit.SECONDS));
        Files.setLastModifiedTime(file, past);

        writer.writeAlways("gen/java/app/Foo.java", "package app;\n");

        assertEquals(past, Files.getLastModifiedTime(file));
    }

    @Test
    void writeAlways_differentContentRewritesFile(@TempDir Path outDir) throws IOException {
        EmitWriter writer = new EmitWriter(outDir);
        writer.writeAlways("gen/java/app/Foo.java", "package app;\n// v1\n");

        writer.writeAlways("gen/java/app/Foo.java", "package app;\n// v2\n");

        String content = Files.readString(outDir.resolve("gen/java/app/Foo.java"), StandardCharsets.UTF_8);
        assertEquals(EmitWriter.GEN_HEADER + "package app;\n// v2\n", content);
    }

    @Test
    void writeIfAbsent_existingFileWithDifferentContentIsNotOverwritten(@TempDir Path outDir) throws IOException {
        EmitWriter writer = new EmitWriter(outDir);
        Path target = outDir.resolve("src/main/java/app/Handler.java");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "human-written content, farklı", StandardCharsets.UTF_8);

        writer.writeIfAbsent("src/main/java/app/Handler.java", "gen-owned default content");

        String content = Files.readString(target, StandardCharsets.UTF_8);
        assertEquals("human-written content, farklı", content);
    }

    @Test
    void writeIfAbsent_missingFileIsCreated(@TempDir Path outDir) throws IOException {
        EmitWriter writer = new EmitWriter(outDir);

        writer.writeIfAbsent("src/main/java/app/Handler.java", "default content\n");

        String content = Files.readString(outDir.resolve("src/main/java/app/Handler.java"), StandardCharsets.UTF_8);
        assertEquals("default content\n", content);
    }

    @Test
    void migrateSeamIfFlat_movesOldFlatFileToNewSliceWhenNewMissing(@TempDir Path outDir) throws IOException {
        Path oldFlat = outDir.resolve("src/main/java/app/FooHandler.java");
        Path newSlice = outDir.resolve("src/main/java/app/foo/FooHandler.java");
        Files.createDirectories(oldFlat.getParent());
        Files.writeString(oldFlat, "human logic body\n", StandardCharsets.UTF_8);
        EmitWriter writer = new EmitWriter(outDir);

        writer.migrateSeamIfFlat(oldFlat, newSlice);

        assertFalse(Files.exists(oldFlat));
        assertTrue(Files.exists(newSlice));
        assertEquals("human logic body\n", Files.readString(newSlice, StandardCharsets.UTF_8));
    }

    @Test
    void migrateSeamIfFlat_doesNothingWhenNewSliceAlreadyExists(@TempDir Path outDir) throws IOException {
        Path oldFlat = outDir.resolve("src/main/java/app/FooHandler.java");
        Path newSlice = outDir.resolve("src/main/java/app/foo/FooHandler.java");
        Files.createDirectories(oldFlat.getParent());
        Files.writeString(oldFlat, "old body\n", StandardCharsets.UTF_8);
        Files.createDirectories(newSlice.getParent());
        Files.writeString(newSlice, "already migrated body\n", StandardCharsets.UTF_8);
        EmitWriter writer = new EmitWriter(outDir);

        writer.migrateSeamIfFlat(oldFlat, newSlice);

        assertTrue(Files.exists(oldFlat), "yeni yol zaten varsa eski yola dokunulmamalı");
        assertEquals("already migrated body\n", Files.readString(newSlice, StandardCharsets.UTF_8));
    }

    @Test
    void prune_removesOrphanKeepsCurrentGeneratedAndHumanSeam(@TempDir Path outDir) throws IOException {
        // run1: A + B (Generated) + C (human, writeIfAbsent)
        EmitWriter run1 = new EmitWriter(outDir);
        run1.writeAlways("gen/java/app/A.java", "class A {}\n");
        run1.writeAlways("gen/java/app/orphan/B.java", "class B {}\n");
        run1.writeIfAbsent("src/main/java/app/C.java", "class C { /* human */ }\n");
        run1.finishAndPrune("0.1.0-test");

        Path a = outDir.resolve("gen/java/app/A.java");
        Path b = outDir.resolve("gen/java/app/orphan/B.java");
        Path c = outDir.resolve("src/main/java/app/C.java");
        assertTrue(Files.exists(a));
        assertTrue(Files.exists(b));
        assertTrue(Files.exists(c));

        // run2: yalnız A yazılır → B orphan → silinmeli, boş "orphan" dizini silinmeli, C (human) dokunulmamalı.
        EmitWriter run2 = new EmitWriter(outDir);
        run2.writeAlways("gen/java/app/A.java", "class A {}\n");
        run2.finishAndPrune("0.1.0-test");

        assertTrue(Files.exists(a), "bu run'da yazılan A silinmemeli");
        assertFalse(Files.exists(b), "önceki run'ın Generated'ı ama bu run'da yazılmayan B silinmeli (orphan prune)");
        assertFalse(Files.exists(b.getParent()), "B silindikten sonra artık-boş 'orphan' dizini de silinmeli");
        assertTrue(Files.exists(c), "human dosyası (writeIfAbsent) prune'dan ASLA etkilenmemeli");
        assertEquals("class C { /* human */ }\n", Files.readString(c, StandardCharsets.UTF_8),
                "human dosyasının içeriği de değişmemeli");
    }

    @Test
    void prune_corruptProvenanceSkipsPruneButWritesNewValidProvenance(@TempDir Path outDir) throws IOException {
        // Önceden üretilmiş B dosyası diskte var ama provenance.json BOZUK (parse edilemiyor).
        Path b = outDir.resolve("gen/java/app/B.java");
        Files.createDirectories(b.getParent());
        Files.writeString(b, "class B {}\n", StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve(ProvenanceIo.FILE_NAME), "{ this is not valid json ]]]", StandardCharsets.UTF_8);

        EmitWriter writer = new EmitWriter(outDir);
        writer.writeAlways("gen/java/app/A.java", "class A {}\n");
        writer.finishAndPrune("0.1.0-test");

        assertTrue(Files.exists(b), "bozuk provenance → prune ATLANIR, B silinmemeli");
        Provenance rewritten = ProvenanceIo.tryRead(outDir);
        assertEquals("techgen-spring", rewritten.generator());
        assertEquals(1, rewritten.files().size());
        assertEquals("gen/java/app/A.java", rewritten.files().get(0).path());
    }

    @Test
    void provenanceFile_ordinalSortedLowercaseHexShaTrailingNewline(@TempDir Path outDir) throws IOException {
        EmitWriter writer = new EmitWriter(outDir);
        writer.writeAlways("gen/java/app/Zeta.java", "class Zeta {}\n");
        writer.writeAlways("gen/java/app/Alpha.java", "class Alpha {}\n");

        writer.finishAndPrune("0.1.0-test");

        String raw = Files.readString(outDir.resolve(ProvenanceIo.FILE_NAME), StandardCharsets.UTF_8);
        assertTrue(raw.endsWith("\n"));
        assertFalse(raw.endsWith("\n\n"));

        Provenance provenance = ProvenanceIo.tryRead(outDir);
        List<String> paths = provenance.files().stream().map(ProvenanceEntry::path).toList();
        assertEquals(List.of("gen/java/app/Alpha.java", "gen/java/app/Zeta.java"), paths);
        for (ProvenanceEntry entry : provenance.files()) {
            assertTrue(LOWERCASE_HEX_SHA256.matcher(entry.sha256()).matches(),
                    "sha256 lowercase-hex 64 karakter olmalı: " + entry.sha256());
            assertEquals("Generated", entry.clazz());
        }
    }
}
