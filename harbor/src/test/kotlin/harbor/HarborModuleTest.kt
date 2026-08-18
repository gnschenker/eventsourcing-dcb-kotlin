package harbor

import kotlin.test.Test
import kotlin.test.assertEquals

class HarborModuleTest {
    @Test
    fun `the harbor module is on the classpath`() {
        assertEquals("harbor", this::class.java.packageName)
    }
}
