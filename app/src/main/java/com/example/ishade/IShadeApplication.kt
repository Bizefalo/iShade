package com.example.ishade // Asegúrate que este sea tu paquete correcto

import android.app.Application
import android.util.Log

class IShadeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("IShadeApplication", "Application onCreate - Inicializando MqttHandler")
        MqttHandler.init(applicationContext)
        // Opcional: Intentar conectar automáticamente cuando la app inicia
        // MqttHandler.connect()
        // O puedes dejar que los Fragments/Activities decidan cuándo conectar.
    }
}