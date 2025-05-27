package com.example.ishade

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.ishade.databinding.FragmentAutomaticBinding
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class AutomaticFragment : Fragment() {

    private var _binding: FragmentAutomaticBinding? = null
    private val binding get() = _binding!!

    private lateinit var mqttClient: MqttAndroidClient

    // Configuración MQTT (ajusta la IP si es necesario)
    private val brokerUri = "tcp://192.168.0.8:1883" // IP de tu PC con Mosquitto
    private val clientId = "ishadeAppAutomatic-${System.currentTimeMillis()}"
    private val topicSensorLuz = "sensor/luz" // Topic para recibir datos del sensor
    private val topicControl = "cortina/control" // Topic para enviar comandos de modo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("AutomaticFragment", "onCreateView")
        _binding = FragmentAutomaticBinding.inflate(inflater, container, false)
        setupMqttClient(requireActivity().applicationContext)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("AutomaticFragment", "onViewCreated")

        connectMqtt() // Intentar conectar al broker

        binding.switchModoAutomatico.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enviarComandoMqtt(topicControl, "AUTO") // Comando para activar modo AUTO en ESP32
                binding.textviewAutoStatus.text = "Modo Automático Activado"
                // Aquí podrías querer suscribirte al topic del sensor si no lo hiciste al conectar
                // o si solo quieres recibir datos cuando el modo está activo.
                // Por ahora, la suscripción se hace en connectComplete.
            } else {
                enviarComandoMqtt(topicControl, "AUTO_OFF") // Comando para desactivar modo AUTO en ESP32
                binding.textviewAutoStatus.text = "Modo Automático Desactivado"
                binding.textviewLuzValue.text = "---" // Limpiar valor de luz al desactivar
            }
            // TODO: Implementar lógica de gestión de modo global (si AUTO_ON, otros modos se desactivan)
        }
    }

    private fun setupMqttClient(context: Context) {
        Log.d("AutomaticFragment", "setupMqttClient")
        mqttClient = MqttAndroidClient(context, brokerUri, clientId)
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.i("MQTT_AutoFrag", "Conexión completa al broker: $serverURI")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Conectado al Broker (Automático)", Toast.LENGTH_SHORT).show()
                    // Suscribirse al topic del sensor de luz una vez conectados
                    subscribeToTopic(topicSensorLuz)
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e("MQTT_AutoFrag", "Conexión perdida: ${cause?.toString()}", cause)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Conexión MQTT perdida (Automático)", Toast.LENGTH_SHORT).show()
                    binding.textviewLuzValue.text = "Error Broker"
                    binding.textviewAutoStatus.text = "Broker Desconectado"
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                try {
                    val payload = message?.payload?.toString(Charsets.UTF_8)
                    Log.d("MQTT_AutoFrag", "Mensaje recibido en $topic: $payload")
                    if (topic == topicSensorLuz && payload != null) {
                        activity?.runOnUiThread {
                            binding.textviewLuzValue.text = payload
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MQTT_AutoFrag", "Error al procesar mensaje: ${e.message}", e)
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d("MQTT_AutoFrag", "Entrega de mensaje completa, token: $token")
            }
        })
    }

    private fun connectMqtt() {
        Log.d("AutomaticFragment", "connectMqtt - Intentando conectar")
        if (::mqttClient.isInitialized && !mqttClient.isConnected) {
            try {
                val options = MqttConnectOptions()
                options.isAutomaticReconnect = true
                options.isCleanSession = true
                mqttClient.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT_AutoFrag", "Conexión MQTT iniciada (connect onSuccess).")
                        // La confirmación visual y suscripción se maneja en connectComplete
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT_AutoFrag", "Fallo al iniciar conexión MQTT: ${exception?.message}", exception)
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Fallo al conectar (Automático)", Toast.LENGTH_LONG).show()
                            binding.textviewLuzValue.text = "Error Broker"
                            binding.textviewAutoStatus.text = "Error Conexión Broker"
                        }
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT_AutoFrag", "Excepción MqttException al conectar: ${e.message}", e)
            }
        } else if (::mqttClient.isInitialized && mqttClient.isConnected) {
            Log.i("MQTT_AutoFrag", "Ya conectado. Re-suscribiéndose por si acaso.")
            subscribeToTopic(topicSensorLuz) // Asegurar suscripción si ya estaba conectado
        } else {
            Log.e("MQTT_AutoFrag", "mqttClient no inicializado antes de conectar.")
        }
    }

    private fun subscribeToTopic(topic: String, qos: Int = 0) {
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                mqttClient.subscribe(topic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT_AutoFrag", "Suscrito exitosamente a $topic")
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Suscrito a $topic", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT_AutoFrag", "Fallo al suscribirse a $topic: ${exception?.message}", exception)
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Fallo al suscribir a $topic", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT_AutoFrag", "Excepción MqttException al suscribir: ${e.message}", e)
            }
        } else {
            Log.w("MQTT_AutoFrag", "No se puede suscribir, cliente no conectado o no inicializado.")
        }
    }

    private fun enviarComandoMqtt(topic: String, comando: String, qos: Int = 1) {
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                val message = MqttMessage()
                message.payload = comando.toByteArray(Charsets.UTF_8)
                message.qos = qos
                message.isRetained = false
                mqttClient.publish(topic, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT_AutoFrag", "Comando '$comando' publicado a $topic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT_AutoFrag", "Fallo al publicar '$comando': ${exception?.message}", exception)
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Error al enviar comando AUTO", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT_AutoFrag", "Excepción MqttException al publicar: ${e.message}", e)
            }
        } else {
            Log.w("MQTT_AutoFrag", "No se puede enviar comando '$comando', cliente no conectado.")
            Toast.makeText(requireContext(), "No conectado al Broker (Automático)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("AutomaticFragment", "onResume - Intentando conectar si es necesario")
        connectMqtt() // Intenta conectar/asegurar conexión cuando el fragmento es visible
    }

    override fun onDestroyView() {
        Log.d("AutomaticFragment", "onDestroyView")
        // Considera si desuscribirte o desconectar aquí.
        // Si quieres que la conexión y suscripción persistan mientras la app está abierta
        // (aunque este fragmento no esté visible), necesitarías una gestión de cliente diferente (Service/ViewModel global).
        // Por ahora, para simplificar y evitar problemas si el fragmento se recrea mucho,
        // podríamos desconectar para limpiar.
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                // Desuscribirse de los topics antes de desconectar
                mqttClient.unsubscribe(topicSensorLuz, null, object: IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT_AutoFrag", "Desuscrito de $topicSensorLuz exitosamente.")
                        // Proceder a desconectar después de desuscribir exitosamente
                        disconnectMqttClient()
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT_AutoFrag", "Fallo al desuscribir de $topicSensorLuz.", exception)
                        // Aún así intentar desconectar
                        disconnectMqttClient()
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT_AutoFrag", "Error al intentar desuscribir/desconectar en onDestroyView", e)
                disconnectMqttClient() // Asegurar intento de desconexión
            }
        }
        _binding = null
        super.onDestroyView()
    }

    private fun disconnectMqttClient() {
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                Log.d("MQTT_AutoFrag", "Intentando desconectar cliente MQTT...")
                mqttClient.disconnect(null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT_AutoFrag", "Cliente MQTT desconectado exitosamente.")
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT_AutoFrag", "Fallo al desconectar MQTT.", exception)
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT_AutoFrag", "Excepción MqttException al desconectar: ${e.message}", e)
            }
        }
    }

    // Remueve el código de newInstance y ARG_PARAM que venía con la plantilla
    // si no lo estás usando para pasar argumentos al crear el fragmento.
}