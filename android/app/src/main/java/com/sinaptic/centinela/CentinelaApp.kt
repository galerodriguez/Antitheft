package com.sinaptic.centinela

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.sinaptic.centinela.ui.MainActivity

/**
 * Cuenta las actividades visibles para saber cuándo la app pasa a segundo plano.
 * Al ir a segundo plano, re-bloquea (exige PIN la próxima vez que se abra).
 *
 * Como cuenta a nivel de app, pasar de MainActivity a PinActivity/LinkActivity NO cuenta como
 * "segundo plano" (siempre hay al menos una actividad visible), así que no genera bucles.
 */
class CentinelaApp : Application() {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++ }
            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    // App fue a segundo plano -> re-bloquear.
                    MainActivity.unlockedThisProcess = false
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
