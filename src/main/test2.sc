import java.util.Locale

import com.ibm.icu.text.RuleBasedNumberFormat

val nf = new RuleBasedNumberFormat(Locale.forLanguageTag("uk"),
  RuleBasedNumberFormat.SPELLOUT);
println(nf.format(173121));
// один миллион двести тридцать четыре тысячи пятьсот шестьдесят семь