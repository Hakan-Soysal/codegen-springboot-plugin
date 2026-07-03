package techgen.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * T8.1 birim testleri: DTO parse (tolerans) + {@link SpecRunner#assertAgainstSpec} +
 * {@link GeneratedApp#buildRequest}/{@link GeneratedApp#inspect}/
 * {@link GeneratedApp#tryGetSuccessFieldDecimal} + {@link SpecRunner#run} akışı (stub/invariant
 * iskelet/exception→FAIL). A3 denetimi: bu test dosyası "Success"/"NotProcessable" gibi taksonomi
 * adlarını ÖRNEK-VERİ olarak kullanabilir — yasak yalnız MAIN kaynak için geçerli (task §6.3).
 * Gerçek üretilmiş app'e karşı acceptance T8.2'nindir.
 */
class SpecRunnerTest {

    // ── 1-4: DTO parse — örnek spec biçimleri ──

    @Test
    void parsesThrowsSpec() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"throws","opId":"CreateInvoice",
                 "arrange":{}, "act":{"call":"CreateInvoice","with":{"amount":10}},
                 "assert":{"resultType":"NotProcessable","code":"CREDIT_LIMIT_EXCEEDED"}}
                """);
        assertEquals("throws", spec.construct());
        assertEquals("CreateInvoice", spec.opId());
        assertEquals("NotProcessable", spec.assert_().resultType());
        assertEquals("CREDIT_LIMIT_EXCEEDED", spec.assert_().code());
        assertFalse(spec.assert_().stub());
    }

    @Test
    void parsesValidationSpec() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"validation","opId":"CreateInvoice",
                 "arrange":{}, "act":{"call":"CreateInvoice","with":{}},
                 "assert":{"resultType":"NotValid"}}
                """);
        assertEquals("NotValid", spec.assert_().resultType());
        assertNull(spec.assert_().code());
    }

    @Test
    void parsesInvariantSpec() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"invariant","opId":"CreateInvoice",
                 "arrange":{"kind":"property"}, "act":{"call":"CreateInvoice","with":{"amount":10}},
                 "assert":{"field":"balance","op":">=","bound":0}}
                """);
        assertEquals("invariant", spec.construct());
        assertEquals("balance", spec.assert_().field());
        assertEquals(">=", spec.assert_().op());
        assertEquals(new BigDecimal("0"), spec.assert_().bound());
    }

    @Test
    void parsesStubSpec() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"saga","opId":"PlaceOrder",
                 "arrange":{}, "act":{"call":"PlaceOrder","with":{}},
                 "assert":{"stub":true,"expected":"v2'de saga compensate koşulacak"}}
                """);
        assertTrue(spec.assert_().stub());
        assertEquals("v2'de saga compensate koşulacak", spec.assert_().expected());
    }

    @Test
    void parseTolerantOfCommentsTrailingCommasAndCaseInsensitiveKeys() throws Exception {
        Spec spec = SpecJson.parse("""
                {
                  // yorum satırı — tolere edilmeli
                  "CONSTRUCT": "throws",
                  "OpId": "CreateInvoice",
                  "Arrange": {},
                  "ACT": {"Call": "CreateInvoice", "With": {"amount": 10,},},
                  "assert": {"ResultType": "NotProcessable", "Code": "X"},
                }
                """);
        assertEquals("throws", spec.construct());
        assertEquals("CreateInvoice", spec.opId());
        assertEquals("NotProcessable", spec.assert_().resultType());
        assertEquals("X", spec.assert_().code());
    }

    // ── 5-8: assertAgainstSpec birim ──

    private static Spec specWith(String resultType, String code) throws Exception {
        return SpecJson.parse("""
                {"construct":"throws","opId":"Op1","arrange":{},"act":{"call":"Op1","with":{}},
                 "assert":{"resultType":%s,"code":%s}}
                """.formatted(resultType == null ? "null" : "\"" + resultType + "\"",
                code == null ? "null" : "\"" + code + "\""));
    }

    @Test
    void assertAgainstSpec_matchingTypeAndCode_pass() throws Exception {
        Spec spec = specWith("NotProcessable", "X");
        SpecResult result = SpecRunner.assertAgainstSpec(spec, new ResultShape("NotProcessable", "X"));
        assertTrue(result.isPass());
    }

    @Test
    void assertAgainstSpec_typeMismatch_fail_detailContainsBothNames() throws Exception {
        Spec spec = specWith("NotProcessable", null);
        SpecResult result = SpecRunner.assertAgainstSpec(spec, new ResultShape("Success", null));
        assertTrue(result.isFail());
        assertTrue(result.detail().contains("NotProcessable"));
        assertTrue(result.detail().contains("Success"));
    }

    @Test
    void assertAgainstSpec_codeMismatch_fail() throws Exception {
        Spec spec = specWith("NotProcessable", "EXPECTED_CODE");
        SpecResult result = SpecRunner.assertAgainstSpec(spec, new ResultShape("NotProcessable", "OTHER_CODE"));
        assertTrue(result.isFail());
        assertTrue(result.detail().contains("EXPECTED_CODE"));
        assertTrue(result.detail().contains("OTHER_CODE"));
    }

    @Test
    void assertAgainstSpec_nullResultType_fail() throws Exception {
        Spec spec = specWith(null, null);
        SpecResult result = SpecRunner.assertAgainstSpec(spec, new ResultShape("Success", null));
        assertTrue(result.isFail());
        assertTrue(result.detail().contains("koşulamaz"));
    }

    // ── 9: buildRequest birim (BigDecimal/int/bool dönüşümleri + case-insensitive key) ──

    record FakeRequest(String name, BigDecimal amount, int qty, boolean active) {
    }

    static final class FakeHandler {
        public Object execute(FakeRequest req) {
            return req;
        }
    }

    @Test
    void buildRequest_convertsTypesAndMatchesKeysCaseInsensitively() throws Exception {
        JsonNode with = SpecJson.mapper().readTree(
                """
                {"NAME":"widget","amount":"12.50","QTY":3,"Active":true}
                """);
        Object built = GeneratedApp.buildRequest(new FakeHandler(), with);
        assertTrue(built instanceof FakeRequest);
        FakeRequest req = (FakeRequest) built;
        assertEquals("widget", req.name());
        assertEquals(0, new BigDecimal("12.50").compareTo(req.amount()));
        assertEquals(3, req.qty());
        assertTrue(req.active());
    }

    // ── 10-11: inspect birim ──

    @Test
    void inspect_valueShapedResult_returnsSimpleNameAndNullCode() {
        record Success(String value) {
        }
        ResultShape shape = GeneratedApp.inspect(new Success("hello"));
        assertEquals("Success", shape.resultType());
        assertNull(shape.code());
    }

    @Test
    void inspect_codeShapedResult_returnsSimpleNameAndCode() {
        record NotProcessable(String code, String message) {
        }
        ResultShape shape = GeneratedApp.inspect(new NotProcessable("ERR_X", "boom"));
        assertEquals("NotProcessable", shape.resultType());
        assertEquals("ERR_X", shape.code());
    }

    // ── 12-13: tryGetSuccessFieldDecimal birim ──

    @Test
    void tryGetSuccessFieldDecimal_readsFieldFromValueAccessor() {
        record Money(BigDecimal amount) {
        }
        record Success(Money value) {
        }
        Optional<BigDecimal> found = GeneratedApp.tryGetSuccessFieldDecimal(
                new Success(new Money(new BigDecimal("42.50"))), "amount");
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("42.50").compareTo(found.get()));
    }

    @Test
    void tryGetSuccessFieldDecimal_emptyWhenNoValueAccessor() {
        record NotProcessable(String code, String message) {
        }
        Optional<BigDecimal> found = GeneratedApp.tryGetSuccessFieldDecimal(
                new NotProcessable("ERR_X", "boom"), "amount");
        assertTrue(found.isEmpty());
    }

    // ── 14-16: SpecRunner.run akışı (stub/invariant iskelet/exception→FAIL/pass) ──

    record FakeOpRequest(String name) {
    }

    static final class FakeOpHandler {
        boolean shouldFail;

        public Object execute(FakeOpRequest req) {
            if (shouldFail) {
                throw new UnsupportedOperationException("boş seam (doldurulacak)");
            }
            record Success(String value) {
            }
            return new Success(req.name());
        }
    }

    private static GeneratedApp fakeApp(String beanName, Object handlerInstance) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(beanName, Object.class, () -> handlerInstance);
        ctx.refresh();
        GeneratedApp.URLClassLoaderHandle handle =
                GeneratedApp.URLClassLoaderHandle.of(System.getProperty("java.io.tmpdir"));
        return new GeneratedApp(handle, ctx);
    }

    @Test
    void run_stubSpec_returnsSkipped_withoutTouchingApp() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"saga","opId":"FakeOp","arrange":{},"act":{"call":"FakeOp","with":{}},
                 "assert":{"stub":true,"expected":"ertelenmiş"}}
                """);
        SpecResult result = new SpecRunner().run(spec, null);
        assertEquals(SpecStatus.SKIPPED, result.status());
    }

    @Test
    void run_invariantSpec_returnsSkipped_iskeletNotu() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"invariant","opId":"FakeOp","arrange":{"kind":"property"},
                 "act":{"call":"FakeOp","with":{"name":"x"}},
                 "assert":{"field":"balance","op":">=","bound":0}}
                """);
        try (GeneratedApp app = fakeApp("fakeOpHandler", new FakeOpHandler())) {
            SpecResult result = new SpecRunner().run(spec, app);
            assertEquals(SpecStatus.SKIPPED, result.status());
            assertTrue(result.detail().contains("T8.2"));
        }
    }

    @Test
    void run_handlerThrowsUnsupportedOperationException_returnsFail() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"throws","opId":"FakeOp","arrange":{},
                 "act":{"call":"FakeOp","with":{"name":"x"}},
                 "assert":{"resultType":"NotProcessable","code":"X"}}
                """);
        FakeOpHandler handler = new FakeOpHandler();
        handler.shouldFail = true;
        try (GeneratedApp app = fakeApp("fakeOpHandler", handler)) {
            SpecResult result = new SpecRunner().run(spec, app);
            assertEquals(SpecStatus.FAIL, result.status());
            assertTrue(result.detail().contains("UnsupportedOperationException"));
        }
    }

    @Test
    void run_successPath_returnsPass() throws Exception {
        Spec spec = SpecJson.parse("""
                {"construct":"validation","opId":"FakeOp","arrange":{},
                 "act":{"call":"FakeOp","with":{"name":"widget"}},
                 "assert":{"resultType":"Success"}}
                """);
        try (GeneratedApp app = fakeApp("fakeOpHandler", new FakeOpHandler())) {
            SpecResult result = new SpecRunner().run(spec, app);
            assertEquals(SpecStatus.PASS, result.status());
        }
    }

    // ── 17: classloader parent-delegation yönü ──

    @Test
    void classloaderDelegatesToParentFirst_sharedSpringTypeIsSameClassIdentity() throws Exception {
        GeneratedApp.URLClassLoaderHandle handle =
                GeneratedApp.URLClassLoaderHandle.of(System.getProperty("java.io.tmpdir"));
        try {
            Class<?> viaChild = Class.forName(
                    "org.springframework.context.annotation.AnnotationConfigApplicationContext",
                    true, handle.classLoader());
            assertSame(AnnotationConfigApplicationContext.class, viaChild,
                    "parent-delegating classloader paylaşılan Spring tipini child'da YENİDEN tanımlamamalı");
        } finally {
            handle.close();
        }
    }
}
