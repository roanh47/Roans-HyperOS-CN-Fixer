package nl.roanheemstra.cnfixer.assist

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Invisible activity that fires Circle to Search and closes again.
 *
 * It exists so the Quick Settings tile and any automation app have something to
 * launch: a tile click has to collapse the shade, and the system only does that
 * when the click starts an activity.
 */
class TriggerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ok = CircleToSearch.trigger(this)
        if (!ok) {
            Toast.makeText(
                this,
                "Circle to Search did not start. Check that Google is the default assistant.",
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }
}
