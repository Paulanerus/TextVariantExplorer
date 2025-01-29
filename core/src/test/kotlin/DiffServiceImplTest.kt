import dev.paulee.api.data.Change
import dev.paulee.core.data.DiffServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DiffServiceImplTest {

    companion object {
        private lateinit var diffService: DiffServiceImpl

        @BeforeAll
        @JvmStatic
        fun setUp() {
            this.diffService = DiffServiceImpl()
        }
    }

    @Test
    fun getDiffSingle() {
        val changeOne = diffService.getDiff("A normal sentence.", "A normal sentence, with something extra.")
        val resultOne =
            Change("A normal sentence**, with something extra**.", listOf("**, with something extra**" to 17..42))

        assertFalse(changeOne == null)
        assertEquals(changeOne, resultOne)

        val changeTwo = diffService.getDiff("A normal sentence, with something extra.", "A normal sentence.")
        val resultTwo =
            Change("A normal sentence~~, with something extra~~.", listOf("~~, with something extra~~" to 17..42))

        assertFalse(changeTwo == null)
        assertEquals(changeTwo, resultTwo)
    }

    @Test
    fun getDiffMultiple() {
        val changes = diffService.getDiff(
            listOf(
                "A normal sentence.",
                "A normal sentence, with something extra.",
                "A normal sen."
            )
        )

        val result = listOf(
            Change("A normal sentence**, with something extra**.", listOf("**, with something extra**" to 17..42)),
            Change("A normal sen~~tence~~.", listOf("~~tence~~" to 12..20))
        )

        assertFalse(changes.isEmpty())

        assertEquals(changes, result)
    }

    @Test
    fun oldValue() {
        val change =
            Change("A normal sentence**, with something extra**.", listOf("**, with something extra**" to 17..42))

        assertEquals("A normal sentence.", diffService.oldValue(change))
    }

    @Test
    fun newValue() {
        val change =
            Change("A normal sentence**, with something extra**.", listOf("**, with something extra**" to 17..42))

        assertEquals("A normal sentence, with something extra.", diffService.newValue(change))
    }

}