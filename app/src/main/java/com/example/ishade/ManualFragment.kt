package com.example.ishade

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.ishade.databinding.FragmentManualBinding
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class ManualFragment : Fragment() {

    private var _binding: FragmentManualBinding? = null
    private val binding get() = _binding!!

    private var isMotorMoving: Boolean = false
    private lateinit var mqttClient: MqttAndroidClient

    // Usamos la IP que me proporcionaste
    private val brokerUri = "tcp://192.168.0.8:1883"
    private val clientId = "ishadeAppManual-${System.currentTimeMillis()}" // Client ID único
    private val topicControl = "cortina/control" // Topic del ESP32

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualBinding.inflate(inflater, container, false)
        // Usar applicationContext para el cliente MQTT para evitar fugas de memoria
        // si el fragmento se destruye y recrea pero el servicio MQTT sigue activo.
        setupMqttClient(requireActivity().applicationContext)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actualizarEstadoControles(binding.switchActivarManual.isChecked)
        connectMqtt() // Intentar conectar al broker

        binding.switchActivarManual.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstadoControles(isChecked)
            if (!isChecked && isMotorMoving) {
                enviarComandoMqtt("DETENER")
                binding.textviewManualStatus.text = "Modo Manual Desactivado - Movimiento Detenido"
                isMotorMoving = false
            }

            if(isChecked){
                // TODO: Lógica para informar al sistema que el Modo Manual se ha activado.
                // TODO: Verificar si otro modo está activo y, si es así, gestionarlo (mostrar advertencia, etc.)
                Toast.makeText(requireContext(), "Modo Manual seleccionado para activar", Toast.LENGTH_SHORT).show()
            } else {
                // TODO: Lógica para informar al sistema que el Modo Manual se ha desactivado.
                Toast.makeText(requireContext(), "Modo Manual deseleccionado", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonSubirManual.setOnTouchListener { v, event ->
            if (binding.switchActivarManual.isChecked) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.textviewManualStatus.text = "Subiendo..."
                        enviarComandoMqtt("SUBIR")
                        isMotorMoving = true
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        binding.textviewManualStatus.text = "Movimiento Detenido"
                        enviarComandoMqtt("DETENER")
                        isMotorMoving = false
                        true
                    }
                    else -> false
                }
            } else { false }
        }

        binding.buttonBajarManual.setOnTouchListener { v, event ->
            if (binding.switchActivarManual.isChecked) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.textviewManualStatus.text = "Bajando..."
                        enviarComandoMqtt("BAJAR")
                        isMotorMoving = true
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        binding.textviewManualStatus.text = "Movimiento Detenido"
                        enviarComandoMqtt("DETENER")
                        isMotorMoving = false
                        true
                    }
                    else -> false
                }
            } else { false }
        }
    }

    private fun setupMqttClient(context: android.content.Context) {
        mqttClient = MqttAndroidClient(context, brokerUri, clientId)
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.i("MQTT", "Conexión completa al broker: $serverURI. Reconexión: $reconnect")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Conectado al Broker MQTT", Toast.LENGTH_SHORT).show()
                    actualizarEstadoControles(binding.switchActivarManual.isChecked) // Actualiza texto de estado
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e("MQTT", "Conexión perdida: ${cause?.toString()}", cause)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Conexión MQTT perdida", Toast.LENGTH_SHORT).show()
                    binding.textviewManualStatus.text = "Broker Desconectado"
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d("MQTT", "Mensaje recibido: ${message?.toString()} en topic: $topic")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d("MQTT", "Entrega de mensaje completa, token: $token")
            }
        })
    }

    private fun connectMqtt() {
        if (::mqttClient.isInitialized && !mqttClient.isConnected) {
            try {
                val options = MqttConnectOptions()
                options.isAutomaticReconnect = true
                options.isCleanSession = true
                // options.connectionTimeout = 10 // segundos
                // options.keepAliveInterval = 20 // segundos

                mqttClient.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT", "Conexión iniciada exitosamente.")
                        // El Toast y la actualización de UI se manejan en connectComplete
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT", "Fallo al iniciar conexión: ${exception?.message}", exception)
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Fallo al conectar al Broker", Toast.LENGTH_LONG).show()
                            binding.textviewManualStatus.text = "Error conexión Broker"
                        }
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT", "Excepción MqttException al intentar conectar: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("MQTT", "Excepción general al intentar conectar: ${e.message}", e)
            }
        } else if (::mqttClient.isInitialized && mqttClient.isConnected) {
            Log.i("MQTT", "Cliente MQTT ya está conectado.")
            // Actualizar UI por si acaso, aunque connectComplete ya lo hace
            activity?.runOnUiThread {
                actualizarEstadoControles(binding.switchActivarManual.isChecked)
            }
        } else {
            Log.e("MQTT", "mqttClient no inicializado antes de conectar.")
        }
    }

    private fun enviarComandoMqtt(comando: String) {
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                val message = MqttMessage()
                message.payload = comando.toByteArray()
                message.qos = 1
                message.isRetained = false
                mqttClient.publish(topicControl, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT", "Comando '$comando' publicado a $topicControl")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT", "Fallo al publicar '$comando': ${exception?.message}", exception)
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Error al enviar comando", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT", "Excepción MqttException al publicar: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("MQTT", "Excepción general al publicar: ${e.message}", e)
            }
        } else {
            Log.w("MQTT", "No se puede enviar comando '$comando', cliente no conectado o no inicializado.")
            Toast.makeText(requireContext(), "No conectado al Broker MQTT", Toast.LENGTH_SHORT).show()
            // Podrías intentar reconectar aquí
            // connectMqtt()
        }
    }

    private fun actualizarEstadoControles(manualActivado: Boolean) {
        binding.buttonSubirManual.isEnabled = manualActivado
        binding.buttonBajarManual.isEnabled = manualActivado

        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            binding.textviewManualStatus.text = if (manualActivado) "Modo Manual Activado" else "Modo Manual Desactivado"
        } else {
            binding.textviewManualStatus.text = "Broker Desconectado"
        }
    }

    override fun onResume() {
        super.onResume()
        // Intentar conectar si no está conectado cuando el fragmento se vuelve visible
        connectMqtt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isMotorMoving) {
            // Es mejor verificar si está conectado antes de intentar enviar
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                enviarComandoMqtt("DETENER")
            }
            isMotorMoving = false
        }
        // Considera si realmente quieres desconectar aquí.
        // Si la app va a tener otros fragmentos usando MQTT,
        // quizás quieras manejar la conexión a nivel de Actividad o un Service.
        // Por ahora, para simplificar, no lo desconectamos explícitamente aquí,
        // confiando en que la conexión se pierda o se maneje al cerrar la app.
        // Si desconectas aquí, necesitarías reconectar en onResume/onViewCreated.
        /*
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                // mqttClient.unregisterResources() // Des-registrar recursos primero
                // mqttClient.close() // Cierra el cliente pero no necesariamente desconecta del servicio
                mqttClient.disconnect(null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT", "Cliente MQTT desconectado exitosamente desde onDestroyView.")
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MQTT", "Fallo al desconectar MQTT desde onDestroyView.", exception)
                    }
                })
            } catch (e: MqttException) {
                Log.e("MQTT", "Error al desconectar MQTT en onDestroyView", e)
            }
        }
        */
        _binding = null
    }
}