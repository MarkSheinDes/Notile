package app.notile

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes

class NotileApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        App.ctx = this
    }
}

/** Process-wide context, so any code (not just composables) can read localized strings. */
object App {
    @Volatile var ctx: Context? = null
}

/** Localized text by resource id. Empty in local unit tests, where there is no Android context. */
fun str(@StringRes id: Int, vararg args: Any): String {
    val c = App.ctx ?: return ""
    return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
}
