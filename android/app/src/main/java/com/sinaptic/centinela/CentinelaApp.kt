package com.sinaptic.centinela

import android.app.Application

class CentinelaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicialización global (Firebase se auto-inicializa vía google-services).
    }
}
