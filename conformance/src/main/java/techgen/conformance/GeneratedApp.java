package techgen.conformance;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Üretilen app'i izole bir classloader'da yükler ve Spring context'ini bootstrap eder
 * (davranış sözleşmesi §A.3; referans {@code GeneratedApp.cs} tam — .NET AssemblyLoadContext /
 * DI host karşılığı; Java eşlemesi anılan dokümanın "Java eşlemesi" notu). Bu sınıf
 * ASSERTION TAŞIMAZ — yalnızca: op handler'ı resolve + {@code execute} çağır + dönen
 * {@code Result<T>}'yi yapısal olarak (alt-tip adı + varsa kod) açığa çıkar.
 *
 * <p><b>Senkron sapma (SPEC §8'de kayıtlı):</b> .NET'te {@code ExecuteAsync} + {@code Task}
 * awaits edilir; Java üretilen app'inde handler'lar senkron {@code execute} döner — burada
 * ek bir Task/await katmanı YOK.</p>
 *
 * <p><b>Classloader yönü (anti-pattern §8):</b> {@link java.net.URLClassLoader}'ın
 * VARSAYILAN parent-delegating davranışı kullanılır (runner'ın classloader'ı parent) — özel
 * child-first mantık YAZILMAZ. Bu sayede Spring/Jackson tipleri (bu modül ile app arasında)
 * TEK sınıf tanımı üzerinden paylaşılır; app'e özgü sınıflar (handler'lar, entity'ler, JPA
 * autoconfig sınıfları) yalnız child URL'lerinden gelir.</p>
 */
public final class GeneratedApp implements AutoCloseable {

    /**
     * app'in JPA/datasource altyapısını ayağa kaldırmak için gereken Spring Boot autoconfig
     * sınıfları (component-scan/{@code @EnableAutoConfiguration} kullanılmaz — anti-pattern §8;
     * bunun yerine gereken autoconfig sınıfları AÇIKÇA register edilir). App classpath'inde
     * entity yoksa bile spring-boot-starter-data-jpa her zaman parent POM'da olduğundan bu
     * sınıflar normalde bulunur; bulunamazsa (app'in kendi seam'i değiştiyse) sessizce atlanır.
     */
    private static final String[] JPA_AUTOCONFIG_CLASSES = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
    };

    private final URLClassLoaderHandle classLoader;
    private final AnnotationConfigApplicationContext context;

    /** Test'lerin gerçek {@code load()} (app classpath + GeneratedBootstrap) olmadan da bir
     * context içinden {@code resolveHandler}/{@code close} davranışını koşabilmesi için
     * paket-içi görünür — {@link #load(String)} üretim yolu, testler doğrudan bu ctor'u kullanır. */
    GeneratedApp(URLClassLoaderHandle classLoader, AnnotationConfigApplicationContext context) {
        this.classLoader = classLoader;
        this.context = context;
    }

    /**
     * Üretilen app'i {@code :}-ayrık classpath listesinden yükler (davranış sözleşmesi §5.3):
     * izole {@link java.net.URLClassLoader} (parent = bu sınıfın classloader'ı) → app'in
     * {@code app.GeneratedBootstrap} sınıfı → H2 in-memory datasource property'leri (benzersiz
     * ad) programatik environment'a eklenir → JPA autoconfig + bootstrap register → refresh.
     */
    public static GeneratedApp load(String appClasspath) throws Exception {
        URLClassLoaderHandle cl = URLClassLoaderHandle.of(appClasspath);
        Class<?> bootstrapClass = Class.forName("app.GeneratedBootstrap", true, cl.classLoader());

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.setClassLoader(cl.classLoader());

        String dbName = "conformance" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> h2Props = new LinkedHashMap<>();
        h2Props.put("spring.datasource.url", "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        h2Props.put("spring.datasource.driver-class-name", "org.h2.Driver");
        h2Props.put("spring.datasource.username", "sa");
        h2Props.put("spring.datasource.password", "");
        h2Props.put("spring.jpa.hibernate.ddl-auto", "update");
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("conformance-h2", h2Props));

        for (String autoconfigClassName : JPA_AUTOCONFIG_CLASSES) {
            try {
                ctx.register(Class.forName(autoconfigClassName, true, cl.classLoader()));
            } catch (ClassNotFoundException notPresent) {
                // app classpath'inde bu autoconfig yok — seam (bu app'in build zinciri farklı
                // kurulmuş olabilir); sessizce atlanır, entity'siz app'ler için zararsızdır.
            }
        }
        ctx.register(bootstrapClass);
        ctx.refresh();
        return new GeneratedApp(cl, ctx);
    }

    /**
     * Context'ten adı {@code {opId}Handler} olan VE {@code execute} metodu olan bean'i resolve
     * eder (davranış sözleşmesi §5.3; referans {@code GeneratedApp.cs:49-62} — Java'da DI scope
     * kavramı yok, context tekil/singleton bean'lerle çalışır).
     */
    public Object resolveHandler(String opId) {
        String expectedSimpleName = opId + "Handler";
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            if (bean.getClass().getSimpleName().equals(expectedSimpleName) && findExecuteMethod(bean) != null) {
                return bean;
            }
        }
        throw new IllegalStateException(expectedSimpleName + " context'ten resolve edilemedi");
    }

    /**
     * {@code act.with} (dil-nötr JSON) → handler'ın {@code execute} metodunun beklediği request
     * record'una çevirir (davranış sözleşmesi §5.3; referans {@code GeneratedApp.cs:66-104}).
     * Request tipi {@code execute}'un ilk parametresidir; canonical (tüm-bileşenli) ctor
     * kullanılır; JSON key'leri case-insensitive record bileşen adlarına eşlenir.
     */
    public static Object buildRequest(Object handler, JsonNode with) throws ReflectiveOperationException {
        Method execute = findExecuteMethod(handler);
        if (execute == null) {
            throw new IllegalStateException(handler.getClass().getSimpleName() + " için execute metodu yok");
        }
        Class<?> reqType = execute.getParameterTypes()[0];
        RecordComponent[] components = reqType.getRecordComponents();
        if (components == null) {
            throw new IllegalStateException(reqType.getName() + " bir record değil — canonical ctor çözülemez");
        }
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            paramTypes[i] = component.getType();
            JsonNode value = findFieldCaseInsensitive(with, component.getName());
            args[i] = convert(value, component.getType());
        }
        return reqType.getDeclaredConstructor(paramTypes).newInstance(args);
    }

    /**
     * {@code execute(request)} invoke eder (senkron — bkz. sınıf Javadoc'u) ve {@code Result<T>}
     * objesini döndürür (davranış sözleşmesi §5.3; referans {@code GeneratedApp.cs:107-114}).
     */
    public static Object act(Object handler, Object request) throws ReflectiveOperationException {
        Method execute = findExecuteMethod(handler);
        if (execute == null) {
            throw new IllegalStateException(handler.getClass().getSimpleName() + " için execute metodu yok");
        }
        try {
            return execute.invoke(handler, request);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause() != null ? wrapped.getCause() : wrapped;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new ReflectiveOperationException(cause);
        }
    }

    /**
     * Dönen Result objesinin yapısal görünümü (davranış sözleşmesi §5.3; referans
     * {@code GeneratedApp.cs:118-131}): alt-tip basit adı + (varsa) kod-taşıyan sıfır-parametreli
     * erişimcinin ({@code code()}) değeri. BU DEĞERLER burada assert EDİLMEZ (A3 değişmezi) —
     * yalnız {@link SpecRunner} tarafından spec'in beklentisiyle karşılaştırılmak üzere okunur.
     */
    public static ResultShape inspect(Object result) {
        String simpleName = result.getClass().getSimpleName();
        String code = invokeNoArgAccessorAsString(result, "code");
        return new ResultShape(simpleName, code);
    }

    /**
     * Kod-taşımayan, {@code value()} erişimcisi olan Result alt-tipi (persist edilen entity'yi
     * taşıyan yapı) için: {@code value()} → içindeki {@code field}'i (case-insensitive
     * record bileşen/getter) oku → {@link BigDecimal}'e çevir (davranış sözleşmesi §5.3; referans
     * {@code GeneratedApp.cs:135-155}). Yapısal kontrol tip-adı literal'i İÇERMEZ (A3 değişmezi) —
     * yalnızca "bu alt-tipin {@code value()} erişimcisi var mı" sorusu sorulur.
     */
    public static Optional<BigDecimal> tryGetSuccessFieldDecimal(Object result, String field) {
        Method valueAccessor = findNoArgMethod(result.getClass(), "value");
        if (valueAccessor == null) {
            return Optional.empty();
        }
        Object value;
        try {
            value = valueAccessor.invoke(result);
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
        if (value == null) {
            return Optional.empty();
        }
        Object raw = readFieldCaseInsensitive(value, field);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(toBigDecimal(raw));
    }

    @Override
    public void close() {
        try {
            context.close();
        } finally {
            classLoader.close();
        }
    }

    // ── yardımcılar ──

    private static Method findExecuteMethod(Object handler) {
        return findMethodByName(handler.getClass(), "execute");
    }

    private static Method findMethodByName(Class<?> cls, String name) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> cls, String name) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    private static String invokeNoArgAccessorAsString(Object target, String name) {
        Method m = findNoArgMethod(target.getClass(), name);
        if (m == null) {
            return null;
        }
        try {
            Object value = m.invoke(target);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object readFieldCaseInsensitive(Object target, String field) {
        RecordComponent[] components = target.getClass().getRecordComponents();
        if (components != null) {
            for (RecordComponent c : components) {
                if (c.getName().equalsIgnoreCase(field)) {
                    try {
                        return c.getAccessor().invoke(target);
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                }
            }
        }
        String getterName = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (Method m : target.getClass().getMethods()) {
            if (m.getParameterCount() == 0
                    && (m.getName().equalsIgnoreCase(field) || m.getName().equalsIgnoreCase(getterName))) {
                try {
                    return m.invoke(target);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(raw.toString());
    }

    private static JsonNode findFieldCaseInsensitive(JsonNode with, String name) {
        if (with == null || !with.isObject()) {
            return null;
        }
        var it = with.fields();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Object convert(JsonNode node, Class<?> target) throws ReflectiveOperationException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (target == String.class) {
            return node.asText();
        }
        if (target == BigDecimal.class) {
            // node string-kodlanmış bir decimal olabilir (JSON'da "12.50"); JsonNode.decimalValue()
            // sayısal-olmayan node'larda sessizce ZERO döner — bu yüzden metin yolu AÇIKÇA denenir.
            return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
        }
        if (target == int.class || target == Integer.class) {
            return node.asInt();
        }
        if (target == long.class || target == Long.class) {
            return node.asLong();
        }
        if (target == double.class || target == Double.class) {
            return node.asDouble();
        }
        if (target == boolean.class || target == Boolean.class) {
            return node.asBoolean();
        }
        if (target == UUID.class) {
            return UUID.fromString(node.asText());
        }
        try {
            return SpecJson.mapper().treeToValue(node, target);
        } catch (Exception e) {
            throw new ReflectiveOperationException(e);
        }
    }

    /** İzole, parent-delegating {@link java.net.URLClassLoader} sarmalayıcısı. */
    static final class URLClassLoaderHandle {
        private final java.net.URLClassLoader delegate;

        private URLClassLoaderHandle(java.net.URLClassLoader delegate) {
            this.delegate = delegate;
        }

        static URLClassLoaderHandle of(String colonSeparatedClasspath) {
            String[] parts = colonSeparatedClasspath.split(":");
            URL[] urls = new URL[parts.length];
            for (int i = 0; i < parts.length; i++) {
                urls[i] = toUrl(parts[i]);
            }
            // parent = bu sınıfı yükleyen classloader (runner'ın CL'i) — Spring/Jackson paylaşılır.
            // VARSAYILAN URLClassLoader parent-delegating'tir; child-first ÖZEL mantık YAZILMAZ.
            return new URLClassLoaderHandle(
                    new java.net.URLClassLoader(urls, GeneratedApp.class.getClassLoader()));
        }

        private static URL toUrl(String path) {
            try {
                return java.nio.file.Path.of(path).toAbsolutePath().toUri().toURL();
            } catch (Exception e) {
                throw new IllegalArgumentException("geçersiz classpath girdisi: " + path, e);
            }
        }

        java.net.URLClassLoader classLoader() {
            return delegate;
        }

        void close() {
            try {
                delegate.close();
            } catch (Exception ignored) {
                // kapatma en-iyi-gayret; test/CI sürecinin sonunda JVM zaten kapanır.
            }
        }
    }
}
