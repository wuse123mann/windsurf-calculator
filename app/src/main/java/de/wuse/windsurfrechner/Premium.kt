package de.wuse.windsurfrechner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gratis: Rechner und Umrechner.
 * Premium (Einmalkauf): Spots mit stündlicher Segelempfehlung, Wind-Alarm, eigenes Material.
 *
 * In der GitHub-Variante ist Premium immer frei. In der Play-Variante kümmert sich [Store]
 * (src/play) um den Kauf über Google Play; das Ergebnis wird hier zwischengespeichert,
 * damit die App auch offline weiß, dass gekauft wurde.
 */
object Premium {
    /** Produkt-ID des Einmalkaufs – so in der Play Console anlegen. */
    const val PRODUCT_ID = "premium"

    private const val KEY = "premium_gekauft"

    private val _unlocked = MutableStateFlow(BuildConfig.PREMIUM_FREE)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** Preis aus Google Play (z. B. „3,99 €“), null solange unbekannt. */
    val price = MutableStateFlow<String?>(null)

    /** Kurzer Hinweis für die Kaufkarte (z. B. Fehler oder „Kauf ausstehend“). */
    val message = MutableStateFlow<String?>(null)

    fun init(context: Context) {
        _unlocked.value = isUnlocked(context)
        if (!BuildConfig.PREMIUM_FREE) Store.connect(context.applicationContext)
    }

    /** Für den Hintergrund-Alarm, ohne Billing-Verbindung. */
    fun isUnlocked(context: Context): Boolean =
        BuildConfig.PREMIUM_FREE ||
            context.getSharedPreferences(Storage.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    internal fun setPurchased(context: Context, purchased: Boolean) {
        context.getSharedPreferences(Storage.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, purchased).apply()
        _unlocked.value = BuildConfig.PREMIUM_FREE || purchased
        if (purchased) message.value = null
    }

    fun buy(context: Context) {
        val activity = context.findActivity() ?: return
        Store.buy(activity)
    }

    fun restore(context: Context) = Store.restore(context.applicationContext)
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
