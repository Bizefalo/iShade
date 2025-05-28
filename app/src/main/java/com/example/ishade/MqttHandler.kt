package com.example.ishade // Asegúrate que este sea tu paquete correcto

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

object MqttHandler {

    private const val TAG = "MqttHandler"
    private lateinit var mqttAndroidClient: MqttAndroidClient
    private lateinit var applicationContext: Context

    // Configuración del Broker (usa tus valores)
    private const val BROKER_URL = "tcp://192.168.0.8:1883" // La IP de tu PC donde corre Mosquitto
    private var CLIENT_ID = "ishadeAndroidApp"          // Un ID de cliente único para la app

    // LiveData para observar el estado de la conexión desde la UI
    private val _isConnected = MutableLiveData<Boolean>().apply { postValue(false) }
    val isConnected: LiveData<Boolean> = _isConnected

    // LiveData para mensajes recibidos (si necesitas que la UI reaccione a ellos)
    // Guardará un par: el topic y el payload del mensaje
    private val _receivedMessage = MutableLiveData<Pair<String, String>>()
    val receivedMessage: LiveData<Pair<String, String>> = _receivedMessage


    /**
     * Inicializa el MqttHandler. Debe llamarse una vez, idealmente desde tu clase Application.
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext // Guardar el contexto de la aplicación
        CLIENT_ID = MqttClient.generateClientId() // Genera un ID de cliente único y aleatorio
        mqttAndroidClient = MqttAndroidClient(applicationContext, BROKER_URL, CLIENT_ID)
        setupCallback()
        Log.i(TAG, "MqttHandler inicializado con ClientID: $CLIENT_ID")
    }

    private fun setupCallback() {
        mqttAndroidClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _isConnected.postValue(true)
                Log.i(TAG, "Conexión MQTT completa. Broker: $serverURI. Reconexión: $reconnect")
                // Aquí puedes re-suscribirte a topics necesarios automáticamente después de conectar/reconectar
                // Ejemplo: si el modo automático necesita datos del sensor:
                // subscribeTopic("sensor/luz")
            }

            override fun connectionLost(cause: Throwable?) {
                _isConnected.postValue(false)
                Log.e(TAG, "Conexión MQTT perdida: ${cause?.toString()}", cause)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val msgPayload = String(it.payload, Charsets.UTF_8)
                    Log.d(TAG, "Mensaje MQTT recibido - Topic: '$topic', Mensaje: '$msgPayload'")
                    _receivedMessage.postValue(Pair(topic ?: "desconocido", msgPayload))
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d(TAG, "Entrega de mensaje MQTT completa. Token: ${token?.messageId}")
            }
        })
    }

    fun connect() {
        if (!::mqttAndroidClient.isInitialized) {
            Log.e(TAG, "MqttHandler no inicializado. Llama a init() primero desde tu clase Application.")
            return
        }
        if (mqttAndroidClient.isConnected) {
            Log.i(TAG, "Ya conectado al broker MQTT.")
            _isConnected.postValue(true) // Asegurar que LiveData esté actualizado
            return
        }

        try {
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true // Intentar reconectar automáticamente
                isCleanSession = true       // Iniciar sesión limpia (no recordar suscripciones previas del broker)
                connectionTimeout = 10      // Tiempo de espera para la conexión en segundos
                keepAliveInterval = 20      // Intervalo para mensajes de keep-alive en segundos
            }
            Log.d(TAG, "Intentando conectar al broker: $BROKER_URL")
            mqttAndroidClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Intento de conexión MQTT iniciado exitosamente.")
                    // El estado de _isConnected se actualiza en connectComplete()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    _isConnected.postValue(false)
                    Log.e(TAG, "Fallo al iniciar conexión MQTT: ${exception?.message}", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MqttException durante el intento de conexión: ${e.message}", e)
            _isConnected.postValue(false)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción general durante el intento de conexión: ${e.message}", e)
            _isConnected.postValue(false)
        }
    }

    fun publishMessage(topic: String, message: String, qos: Int = 1, retained: Boolean = false) {
        if (!::mqttAndroidClient.isInitialized || !mqttAndroidClient.isConnected) {
            Log.w(TAG, "Cliente MQTT no conectado o no inicializado. No se puede publicar: '$message' a '$topic'")
            // Opcional: podrías intentar conectar aquí si no está conectado:
            // connect()
            return
        }
        try {
            val mqttMessage = MqttMessage()
            mqttMessage.payload = message.toByteArray(Charsets.UTF_8)
            mqttMessage.qos = qos
            mqttMessage.isRetained = retained
            mqttAndroidClient.publish(topic, mqttMessage, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Mensaje '$message' publicado a '$topic' exitosamente.")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Fallo al publicar mensaje '$message' a '$topic': ${exception?.message}", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MqttException al publicar: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción general al publicar: ${e.message}", e)
        }
    }

    fun subscribeTopic(topic: String, qos: Int = 0) {
        if (!::mqttAndroidClient.isInitialized || !mqttAndroidClient.isConnected) {
            Log.w(TAG, "Cliente MQTT no conectado o no inicializado. No se puede suscribir a '$topic'")
            return
        }
        try {
            mqttAndroidClient.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Suscrito a '$topic' exitosamente.")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Fallo al suscribir a '$topic': ${exception?.message}", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MqttException al suscribir: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción general al suscribir: ${e.message}", e)
        }
    }

    fun unsubscribeTopic(topic: String) {
        if (!::mqttAndroidClient.isInitialized || !mqttAndroidClient.isConnected) {
            Log.w(TAG, "Cliente MQTT no conectado o no inicializado. No se puede desuscribir de '$topic'")
            return
        }
        try {
            mqttAndroidClient.unsubscribe(topic, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Desuscrito de '$topic' exitosamente.")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Fallo al desuscribir de '$topic': ${exception?.message}", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MqttException al desuscribir: ${e.message}", e)
        }
    }

    fun disconnect() {
        if (!::mqttAndroidClient.isInitialized || !mqttAndroidClient.isConnected) {
            Log.i(TAG, "Cliente MQTT no conectado o no inicializado. No es necesario desconectar.")
            return
        }
        try {
            Log.d(TAG, "Intentando desconectar cliente MQTT...")
            mqttAndroidClient.disconnect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    _isConnected.postValue(false)
                    Log.i(TAG, "Cliente MQTT desconectado exitosamente.")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Fallo al desconectar MQTT: ${exception?.message}", exception)
                    // El estado de la conexión podría ser incierto, pero connectionLost debería activarse.
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MqttException al desconectar: ${e.message}", e)
        }
    }
}