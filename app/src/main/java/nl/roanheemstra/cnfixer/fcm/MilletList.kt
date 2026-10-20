package nl.roanheemstra.cnfixer.fcm

/**
 * Xiaomi keeps a hidden comma-separated package list in Settings.System.
 * Packages on that list are exempt from PowerKeeper's freezing.
 * PowerKeeper rewrites the list, which is why entries must be repaired.
 */
object MilletList {
    const val KEY = "MILLET_NO_RESTRICT_APP"
    const val GMS = "com.google.android.gms"

    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw.trim() == "null") return emptyList()
        val seen = LinkedHashSet<String>()
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { seen.add(it) }
        return seen.toList()
    }

    fun appendGms(raw: String?): List<String> {
        val existing = parse(raw)
        return if (GMS in existing) existing else existing + GMS
    }

    fun serialize(packages: List<String>): String = packages.joinToString(", ")
}
