import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UtilityTest {
    @Test
    fun splitStr() {
        val strOne = "The table 'Data List' has 121.432 entries"
        val arrayOne = listOf("The", "table", "'Data List'", "has", "121.432", "entries")

        val strTwo = "This_table_is_pretty_huge"
        val arrayTwo = listOf("This", "table", "is", "pretty", "huge")

        assertEquals(dev.paulee.core.splitStr(strOne, delimiter = ' ', quoteCharacters = arrayOf('\'')), arrayOne)
        assertEquals(dev.paulee.core.splitStr(strTwo, delimiter = '_'), arrayTwo)
    }

    @Test
    fun normalizeDataSource() {
        val sourceOne = "file 1.csv"
        val sourceTwo = "file 2"

        assertEquals("file_1", dev.paulee.core.normalizeDataSource(sourceOne))

        assertEquals("file_2", dev.paulee.core.normalizeDataSource(sourceTwo))
    }
}