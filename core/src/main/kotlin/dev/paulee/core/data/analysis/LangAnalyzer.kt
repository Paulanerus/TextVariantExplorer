package dev.paulee.core.data.analysis

import dev.paulee.api.data.Language
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.ar.ArabicAnalyzer
import org.apache.lucene.analysis.bg.BulgarianAnalyzer
import org.apache.lucene.analysis.bn.BengaliAnalyzer
import org.apache.lucene.analysis.br.BrazilianAnalyzer
import org.apache.lucene.analysis.ca.CatalanAnalyzer
import org.apache.lucene.analysis.ckb.SoraniAnalyzer
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer
import org.apache.lucene.analysis.cz.CzechAnalyzer
import org.apache.lucene.analysis.da.DanishAnalyzer
import org.apache.lucene.analysis.de.GermanAnalyzer
import org.apache.lucene.analysis.el.GreekAnalyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.es.SpanishAnalyzer
import org.apache.lucene.analysis.et.EstonianAnalyzer
import org.apache.lucene.analysis.eu.BasqueAnalyzer
import org.apache.lucene.analysis.fa.PersianAnalyzer
import org.apache.lucene.analysis.fi.FinnishAnalyzer
import org.apache.lucene.analysis.fr.FrenchAnalyzer
import org.apache.lucene.analysis.ga.IrishAnalyzer
import org.apache.lucene.analysis.gl.GalicianAnalyzer
import org.apache.lucene.analysis.hi.HindiAnalyzer
import org.apache.lucene.analysis.hu.HungarianAnalyzer
import org.apache.lucene.analysis.hy.ArmenianAnalyzer
import org.apache.lucene.analysis.id.IndonesianAnalyzer
import org.apache.lucene.analysis.it.ItalianAnalyzer
import org.apache.lucene.analysis.ja.JapaneseAnalyzer
import org.apache.lucene.analysis.ko.KoreanAnalyzer
import org.apache.lucene.analysis.lt.LithuanianAnalyzer
import org.apache.lucene.analysis.lv.LatvianAnalyzer
import org.apache.lucene.analysis.ne.NepaliAnalyzer
import org.apache.lucene.analysis.nl.DutchAnalyzer
import org.apache.lucene.analysis.no.NorwegianAnalyzer
import org.apache.lucene.analysis.pl.PolishAnalyzer
import org.apache.lucene.analysis.pt.PortugueseAnalyzer
import org.apache.lucene.analysis.ro.RomanianAnalyzer
import org.apache.lucene.analysis.ru.RussianAnalyzer
import org.apache.lucene.analysis.sr.SerbianAnalyzer
import org.apache.lucene.analysis.sv.SwedishAnalyzer
import org.apache.lucene.analysis.ta.TamilAnalyzer
import org.apache.lucene.analysis.te.TeluguAnalyzer
import org.apache.lucene.analysis.th.ThaiAnalyzer
import org.apache.lucene.analysis.tr.TurkishAnalyzer
import org.apache.lucene.analysis.uk.UkrainianMorfologikAnalyzer
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.reflect.full.createInstance

internal object LangAnalyzer {

    private val EMPTY_PATH = Path("")

    fun new(lang: Language, wordList: Path = EMPTY_PATH, extend: Boolean = true): Analyzer{
        val clazz = when(lang){
            Language.ARABIC -> ArabicAnalyzer::class
            Language.BULGARIAN -> BulgarianAnalyzer::class
            Language.BENGALI -> BengaliAnalyzer::class
            Language.BRAZILIAN_PORTUGUESE -> BrazilianAnalyzer::class
            Language.CATALAN -> CatalanAnalyzer::class
            Language.SORANI_KURDISH -> SoraniAnalyzer::class
            Language.CZECH -> CzechAnalyzer::class
            Language.DANISH -> DanishAnalyzer::class
            Language.GERMAN -> GermanAnalyzer::class
            Language.GREEK -> GreekAnalyzer::class
            Language.ENGLISH -> EnglishAnalyzer::class
            Language.SPANISH -> SpanishAnalyzer::class
            Language.ESTONIAN -> EstonianAnalyzer::class
            Language.BASQUE -> BasqueAnalyzer::class
            Language.PERSIAN -> PersianAnalyzer::class
            Language.FINNISH -> FinnishAnalyzer::class
            Language.FRENCH -> FrenchAnalyzer::class
            Language.IRISH -> IrishAnalyzer::class
            Language.GALICIAN -> GalicianAnalyzer::class
            Language.HINDI -> HindiAnalyzer::class
            Language.HUNGARIAN -> HungarianAnalyzer::class
            Language.ARMENIAN -> ArmenianAnalyzer::class
            Language.INDONESIAN -> IndonesianAnalyzer::class
            Language.ITALIAN -> ItalianAnalyzer::class
            Language.LITHUANIAN -> LithuanianAnalyzer::class
            Language.LATVIAN -> LatvianAnalyzer::class
            Language.NEPALI -> NepaliAnalyzer::class
            Language.DUTCH -> DutchAnalyzer::class
            Language.NORWEGIAN -> NorwegianAnalyzer::class
            Language.PORTUGUESE -> PortugueseAnalyzer::class
            Language.ROMANIAN -> RomanianAnalyzer::class
            Language.RUSSIAN -> RussianAnalyzer::class
            Language.SERBIAN -> SerbianAnalyzer::class
            Language.SWEDISH -> SwedishAnalyzer::class
            Language.TAMIL -> TamilAnalyzer::class
            Language.TELUGU -> TeluguAnalyzer::class
            Language.THAI -> ThaiAnalyzer::class
            Language.TURKISH -> TurkishAnalyzer::class
            Language.POLISH -> PolishAnalyzer::class
            Language.CHINESE -> SmartChineseAnalyzer::class
            Language.JAPANESE -> JapaneseAnalyzer::class
            Language.KOREAN -> KoreanAnalyzer::class
            Language.UKRAINIAN -> UkrainianMorfologikAnalyzer::class
        }
        return clazz.createInstance()
    }
}