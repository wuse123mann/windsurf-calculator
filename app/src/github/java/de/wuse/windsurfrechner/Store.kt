package de.wuse.windsurfrechner

import android.app.Activity
import android.content.Context

/** GitHub-Variante: kein Play Store, Premium ist immer freigeschaltet. */
object Store {
    fun connect(context: Context) = Unit
    fun buy(activity: Activity) = Unit
    fun restore(context: Context) = Unit
}
