package nl.roanheemstra.cnfixer.buttons

/**
 * What a remapped key does. Stored as a single string so the preference stays
 * readable in DataStore.
 */
sealed interface RemapAction {

    data object Nothing : RemapAction
    data object ScreenOff : RemapAction
    data object CircleToSearch : RemapAction
    data class LaunchApp(val packageName: String, val label: String) : RemapAction

    fun encode(): String = when (this) {
        Nothing -> "none"
        ScreenOff -> "screen_off"
        CircleToSearch -> "circle"
        is LaunchApp -> "app:$packageName:$label"
    }

    companion object {
        val ALL_LABELS = listOf("Nothing", "Turn the screen off", "Circle to Search", "Pick an app")

        fun decode(raw: String?): RemapAction {
            if (raw.isNullOrBlank()) return Nothing
            return when {
                raw == "screen_off" -> ScreenOff
                raw == "circle" -> CircleToSearch
                raw.startsWith("app:") -> {
                    val rest = raw.removePrefix("app:")
                    val packageName = rest.substringBefore(':')
                    val label = rest.substringAfter(':', packageName)
                    if (packageName.isBlank()) Nothing else LaunchApp(packageName, label)
                }

                else -> Nothing
            }
        }

        fun describe(raw: String?): String = when (val action = decode(raw)) {
            Nothing -> "Nothing"
            ScreenOff -> "Turn the screen off"
            CircleToSearch -> "Circle to Search"
            is LaunchApp -> action.label
        }
    }
}

/** Linux key code for the power key, used to tell it apart in the event stream. */
const val KEY_POWER_CODE = 116

/** Sleep, the key the system itself uses to turn the panel off. */
const val KEYCODE_SLEEP = 223
