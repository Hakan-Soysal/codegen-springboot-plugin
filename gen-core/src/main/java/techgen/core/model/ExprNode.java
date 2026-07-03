package techgen.core.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Canonical Expr AST. Discriminated union, JSON'da ayrımcı üç alan: {@code node}
 * (binary/agg/call), {@code path} (PathNode), {@code kind} (literal/duration).
 * Ayrım alan-VARLIĞINA göredir; standart polimorfizm anotasyonları ({@code @JsonTypeInfo}/
 * {@code @JsonSubTypes}) bunu ifade edemez — bkz. {@link ExprNodeDeserializer}.
 *
 * <p>Not (T2.1): izinli record'lar T1.1'in kendi notuna göre ("Her record ayrı public dosya
 * olabilir") ayrı {@code public} dosyalara taşındı — {@code techgen.core.predicate} paketindeki
 * {@code ExprWalk} (ve T3.5 Java render'ı) bu tipleri paket-dışından pattern-match etmek
 * zorunda; CoreTemplate1'de bu, C# top-level tiplerinin assembly-genelinde görünür olmasına
 * karşılık gelir. Java'da paket-dışı görünürlük için public şart — davranış DEĞİŞMEDİ, yalnız
 * erişim genişledi (aynı paket, aynı modül).</p>
 */
@JsonDeserialize(using = ExprNodeDeserializer.class)
@JsonSerialize(using = ExprNodeSerializer.class)
public sealed interface ExprNode permits BinaryNode, AggNode, CallNode, PathNode, LiteralNode, DurationNode {}
