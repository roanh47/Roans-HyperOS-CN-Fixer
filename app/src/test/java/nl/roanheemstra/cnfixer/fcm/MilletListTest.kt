package nl.roanheemstra.cnfixer.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MilletListTest {

    @Test
    fun `parse returns empty list for null`() {
        assertTrue(MilletList.parse(null).isEmpty())
    }

    @Test
    fun `parse returns empty list for the literal string null`() {
        assertTrue(MilletList.parse("null").isEmpty())
    }

    @Test
    fun `parse splits on comma and trims`() {
        val result = MilletList.parse("com.a, com.b ,com.c")
        assertEquals(listOf("com.a", "com.b", "com.c"), result)
    }

    @Test
    fun `parse removes duplicates but keeps order`() {
        val result = MilletList.parse("com.a,com.b,com.a")
        assertEquals(listOf("com.a", "com.b"), result)
    }

    @Test
    fun `appendGms adds gms when missing`() {
        val result = MilletList.appendGms("com.a,com.b")
        assertEquals(listOf("com.a", "com.b", MilletList.GMS), result)
    }

    @Test
    fun `appendGms does not duplicate gms`() {
        val result = MilletList.appendGms("com.a,${MilletList.GMS}")
        assertEquals(listOf("com.a", MilletList.GMS), result)
    }

    @Test
    fun `appendGms works on an empty value`() {
        assertEquals(listOf(MilletList.GMS), MilletList.appendGms(null))
        assertEquals(listOf(MilletList.GMS), MilletList.appendGms(""))
    }

    @Test
    fun `serialize joins with a comma and a space`() {
        assertEquals("com.a, com.b", MilletList.serialize(listOf("com.a", "com.b")))
    }
}
