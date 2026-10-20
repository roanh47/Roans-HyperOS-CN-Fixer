package nl.roanheemstra.cnfixer.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmCommandsTest {

    @Test
    fun `greezer restore disables the explicit limiter and re-adds gms`() {
        val commands = FcmCommands.greezerRestore()
        assertEquals(2, commands.size)
        assertEquals(listOf("dumpsys", "greezer", "IM", "GMS", "disable"), commands[0])
        assertEquals(listOf("dumpsys", "greezer", "LM", "add", MilletList.GMS), commands[1])
    }

    @Test
    fun `autostart uses the miui op codes`() {
        val commands = FcmCommands.autostart("com.example.app", allow = true)
        assertEquals(2, commands.size)
        assertTrue(commands[0].contains("10008"))
        assertTrue(commands[1].contains("10053"))
        assertTrue(commands.all { it.last() == "allow" })
    }

    @Test
    fun `autostart deny uses ignore`() {
        val commands = FcmCommands.autostart("com.example.app", allow = false)
        assertTrue(commands.all { it.last() == "ignore" })
    }

    @Test
    fun `unrestricted battery adds to the deviceidle whitelist`() {
        val commands = FcmCommands.battery("com.example.app", BatteryPolicy.UNRESTRICTED)
        assertEquals(
            listOf("dumpsys", "deviceidle", "whitelist", "+com.example.app"),
            commands.last(),
        )
    }

    @Test
    fun `optimized battery removes from the deviceidle whitelist`() {
        val commands = FcmCommands.battery("com.example.app", BatteryPolicy.OPTIMIZED)
        assertEquals(
            listOf("dumpsys", "deviceidle", "whitelist", "-com.example.app"),
            commands.last(),
        )
    }

    @Test
    fun `restricted battery ignores background app ops`() {
        val commands = FcmCommands.battery("com.example.app", BatteryPolicy.RESTRICTED)
        val runInBackground = commands.first { it.contains("RUN_IN_BACKGROUND") }
        assertEquals("ignore", runInBackground.last())
    }
}
