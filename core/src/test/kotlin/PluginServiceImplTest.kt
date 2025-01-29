import dev.paulee.api.data.*
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.PluginMetadata
import dev.paulee.api.plugin.Tag
import dev.paulee.api.plugin.Taggable
import dev.paulee.core.plugin.PluginServiceImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class PluginServiceImplTest {

    @PreFilter("quote", "", "")
    @Variant("quote", ["quoteEnglish"])
    @DataSource("quotes")
    data class Quote(val quote: String, val quoteEnglish: String, val author: String)

    class DemoPlugin: IPlugin {
        override fun init(storageProvider: IStorageProvider) {
        }
    }

    @RequiresData("TestData", [Quote::class])
    @PluginMetadata("Test", "1.0.0")
    class PluginDemo: IPlugin, Taggable {
        override fun init(storageProvider: IStorageProvider) {
        }

        @ViewFilter("TestViewFilter", fields = ["quote"])
        override fun tag(field: String, value: String): Map<String, Tag> = emptyMap()
    }

    companion object {
        private lateinit var pluginService: PluginServiceImpl

        @BeforeAll
        @JvmStatic
        fun setUp() {
            this.pluginService = PluginServiceImpl()
        }
    }

    @Test
    fun getPluginMetadata() {
        val metadataOne = pluginService.getPluginMetadata(PluginDemo())

        assertNotNull(metadataOne)

        assertEquals(metadataOne, PluginMetadata("Test", "1.0.0"))

        val metadataTwo = pluginService.getPluginMetadata(DemoPlugin())

        assertNull(metadataTwo)
    }

    @Test
    fun getDataInfo() {
        val dataInfoOne = pluginService.getDataInfo(PluginDemo())

        assertNotNull(dataInfoOne)

        assertEquals(dataInfoOne, RequiresData("TestData", arrayOf(Quote::class)))

        val dataInfoTwo = pluginService.getDataInfo(DemoPlugin())

        assertNull(dataInfoTwo)
    }

    @Test
    fun getViewFilter() {
        val viewFilterOne = pluginService.getViewFilter(PluginDemo())

        assertNotNull(viewFilterOne)

        assertEquals(viewFilterOne, ViewFilter("TestViewFilter", fields = ["quote"]))

        val viewFilterTwo = pluginService.getDataInfo(DemoPlugin())

        assertNull(viewFilterTwo)
    }

    @Test
    fun getVariants() {
        val variant = pluginService.getVariants(RequiresData("TestData", arrayOf(Quote::class)))

        assertTrue(variant.isNotEmpty())

        assertEquals(variant, setOf("quotes"))
    }

    @Test
    fun getPreFilters() {
        val preFilter = pluginService.getPreFilters(RequiresData("TestData", arrayOf(Quote::class)))

        assertTrue(preFilter.isNotEmpty())

        assertEquals(preFilter, setOf("quotes"))
    }
}