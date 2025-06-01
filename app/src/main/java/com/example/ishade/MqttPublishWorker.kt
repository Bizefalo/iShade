package com.example.ishade

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MqttPublishWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "MqttPublishWorker"
        const val KEY_TOPIC = "key_topic"
        const val KEY_PAYLOAD = "key_payload"
        const val KEY_QOS = "key_qos"
        const val KEY_RETAINED = "key_retained"
    }

    override suspend fun doWork(): Result {
        val topic = inputData.getString(KEY_TOPIC)
        val payload = inputData.getString(KEY_PAYLOAD)
        val qos = inputData.getInt(KEY_QOS, 1) // Default QoS 1
        val retained = inputData.getBoolean(KEY_RETAINED, false) // Default not retained

        if (topic.isNullOrBlank() || payload.isNullOrBlank()) {
            Log.e(TAG, "Topic o Payload inválidos. No se puede publicar.")
            return Result.failure()
        }

        Log.d(TAG, "Intentando publicar: '$payload' a '$topic'")

        // Asegurar que MqttHandler esté inicializado (debería estarlo por Application class)
        // MqttHandler.init(applicationContext) // No es necesario si ya se hizo en Application

        // Intentar conectar si no está conectado y esperar un poco
        if (MqttHandler.isConnected.value != true) {
            Log.d(TAG, "MQTT no conectado, intentando conectar desde el Worker...")
            MqttHandler.connect() // Inicia la conexión

            // Esperar un tiempo razonable para la conexión
            // Esto es una forma simple; una observación de LiveData sería más reactiva pero más compleja en un Worker.
            val connectionTimeoutMillis = 15000L // 15 segundos de espera
            val startTime = System.currentTimeMillis()
            while (MqttHandler.isConnected.value != true && (System.currentTimeMillis() - startTime) < connectionTimeoutMillis) {
                delay(500) // Esperar 500ms y reintentar chequeo
                Log.d(TAG, "Esperando conexión MQTT en Worker...")
            }
        }

        if (MqttHandler.isConnected.value == true) {
            Log.i(TAG, "MQTT Conectado. Publicando desde Worker: '$payload' a '$topic'")
            MqttHandler.publishMessage(topic, payload, qos, retained)
            return Result.success()
        } else {
            Log.e(TAG, "Fallo al conectar MQTT desde el Worker después de esperar. Reintentando trabajo.")
            return Result.retry() // Indicar a WorkManager que reintente más tarde
        }
    }
}