package techgen.conformance;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T8.2 §A.5 acceptance testleri (referans {@code conformance-adapter/Tests/ConformanceAcceptanceTests.cs}):
 * {@link SpecRunner}'ın GERÇEKTEN koştuğunu ve assertion'ın SPEC'TE olduğunu (paket gizleyemez)
 * gerçek execution + gözlem ile doğrular (LLM-judge YOK; tek generic runner). SPEC'ler burada
 * fixture manifest'ten (T3.3 ailesi) türetilmiş AYNEN JSON metinleridir — bu test dosyası onları
 * ÜRETMEZ, yalnızca tüketir.
 *
 * <p>Fixture: {@code CreateInvoice} — {@code customerId}/{@code amount}; {@code throws}=
 * DuplicateInvoice (idempotent key=customerId); {@code validation}=amount&gt;0;
 * {@code Invoice.amount} invariant &gt;=0 (fixtures/manifest.json).</p>
 */
class ConformanceAcceptanceTest {

    // T3.3 — throws(DuplicateInvoice) → NotProcessable. (manifest.json türevli)
    private static final String THROWS_SPEC = """
            {
              "construct": "throws",
              "opId": "CreateInvoice",
              "arrange": { "kind": "duplicate", "on": "CreateInvoice", "key": ["customerId"] },
              "act": { "call": "CreateInvoice", "with": { "customerId": "c-1", "amount": 100 } },
              "assert": {
                "resultType": "NotProcessable",
                "code": "DuplicateInvoice",
                "source": "manifest.json#errors[id=DuplicateInvoice].resultType=NotProcessable + operation[CreateInvoice].throws[0]"
              }
            }
            """;

    // T3.3 — validation(amount > 0) → NotValid. (manifest.json türevli)
    private static final String VALIDATION_SPEC = """
            {
              "construct": "validation",
              "opId": "CreateInvoice",
              "arrange": {},
              "act": { "call": "CreateInvoice", "with": { "customerId": "c-1", "amount": 0 } },
              "assert": {
                "resultType": "NotValid",
                "violated": "amount > 0",
                "source": "manifest.json#operation[CreateInvoice].validation[0].ast (cmp > amount 0) -> sinir-disi amount=0"
              }
            }
            """;

    // T3.3 — invariant(Invoice.amount >= 0) → property test. (manifest.json#entities[Invoice].invariants[0])
    private static final String INVARIANT_SPEC = """
            {
              "construct": "invariant",
              "opId": "CreateInvoice",
              "arrange": { "kind": "property" },
              "act": { "call": "CreateInvoice", "with": { "customerId": "c-1", "amount": 1 } },
              "assert": {
                "field": "amount", "op": ">=", "bound": 0,
                "source": "manifest.json#entities[Invoice].invariants[0].ast (cmp >= amount 0)"
              }
            }
            """;

    // DOĞRU seam (throwaway): dup -> DuplicateInvoice (NotProcessable), amount<=0 -> NotValid, else Success.
    private static final String CORRECT_SEAM = """
                    if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
                        return new NotValid<>(Map.of("amount", "amount > 0"));
                    }
                    if (!seen.add(request.customerId())) {
                        return duplicateInvoice("customer " + request.customerId() + " zaten faturali");
                    }
                    Invoice invoice = new Invoice();
                    invoice.setId("inv-1");
                    invoice.setCustomerId(request.customerId());
                    invoice.setAmount(request.amount());
                    return new Success<>(invoice);
            """;

    // YANLIŞ seam (throwaway): dup -> generic ServerError (DuplicateInvoice DEĞİL). throws spec FAIL olmalı.
    private static final String WRONG_SEAM = """
                    if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
                        return new NotValid<>(Map.of("amount", "amount > 0"));
                    }
                    if (!seen.add(request.customerId())) {
                        return new ServerError<>("generic hata");
                    }
                    Invoice invoice = new Invoice();
                    invoice.setId("inv-1");
                    invoice.setCustomerId(request.customerId());
                    invoice.setAmount(request.amount());
                    return new Success<>(invoice);
            """;

    private static List<Spec> parseAll(String... jsons) throws Exception {
        List<Spec> out = new java.util.ArrayList<>();
        for (String json : jsons) {
            out.add(SpecJson.parse(json));
        }
        return out;
    }

    // §A.5 test 1 — doldurulmuş (doğru) seam → TÜM CreateInvoice spec'leri PASS.
    @Test
    void filledCorrectSeam_allSpecsPass(@TempDir Path dir) throws Exception {
        AppFixture.emit(dir);
        AppFixture.fillCreateInvoiceSeam(dir, CORRECT_SEAM);
        String appClasspath = AppFixture.buildAndClasspath(dir);

        try (GeneratedApp app = GeneratedApp.load(appClasspath)) {
            SpecRunner runner = new SpecRunner();
            List<SpecResult> results = List.of(
                    runner.run(SpecJson.parse(THROWS_SPEC), app),
                    runner.run(SpecJson.parse(VALIDATION_SPEC), app));

            for (SpecResult r : results) {
                assertTrue(r.isPass(), "Beklenen PASS ama: " + r);
            }
            assertEquals(2, results.stream().filter(SpecResult::isPass).count());
        }
    }

    // §A.5 test 2 — kasıtlı YANLIŞ seam (DuplicateInvoice yerine ServerError) → throws spec FAIL;
    // validation spec'i hâlâ PASS (yanlış-seam yalnız dup kolunu bozdu) — runner seçici. (A3 kanıtı)
    @Test
    void wrongSeam_throwsSpecFails_butValidationSpecStillPasses(@TempDir Path dir) throws Exception {
        AppFixture.emit(dir);
        AppFixture.fillCreateInvoiceSeam(dir, WRONG_SEAM);
        String appClasspath = AppFixture.buildAndClasspath(dir);

        try (GeneratedApp app = GeneratedApp.load(appClasspath)) {
            SpecRunner runner = new SpecRunner();
            SpecResult throwsResult = runner.run(SpecJson.parse(THROWS_SPEC), app);
            assertTrue(throwsResult.isFail(),
                    "throws spec FAIL olmalıydı (yanlış seam ServerError döndürdü) ama: " + throwsResult);
            // Kanıt: FAIL nedeni spec'in beklediği resultType ile gözlenenin uyuşmaması (assertion SPEC'te).
            assertTrue(throwsResult.detail().contains("NotProcessable"));
            assertTrue(throwsResult.detail().contains("ServerError"));

            SpecResult validationResult = runner.run(SpecJson.parse(VALIDATION_SPEC), app);
            assertTrue(validationResult.isPass(), "validation PASS olmalıydı: " + validationResult);
        }
    }

    // §A.5 test 3 — invariant property üreteci GERÇEKTEN N tur koşar (doğru seam amount'u echo eder
    // -> invariant korunur -> PASS).
    @Test
    void invariantPropertyGenerator_reallyRunsRoundsAndHolds(@TempDir Path dir) throws Exception {
        AppFixture.emit(dir);
        AppFixture.fillCreateInvoiceSeam(dir, CORRECT_SEAM);
        String appClasspath = AppFixture.buildAndClasspath(dir);

        try (GeneratedApp app = GeneratedApp.load(appClasspath)) {
            SpecResult inv = new SpecRunner().run(SpecJson.parse(INVARIANT_SPEC), app);
            assertTrue(inv.isPass(), "invariant property PASS olmalıydı: " + inv);
            assertTrue(inv.detail().contains("50 property"), "üreteç gerçekten N tur koşmalı: " + inv.detail());
        }
    }
}
