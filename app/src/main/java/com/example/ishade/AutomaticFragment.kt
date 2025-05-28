package com.example.ishade

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import android.widget.Toast
import androidx.fragment.app.activityViewModels // Importar para activityViewModels
//import androidx.lifecycle.Observer                 // Importar para Observer
import com.example.ishade.databinding.FragmentAutomaticBinding
// Ya no necesitas las importaciones directas de Paho aquí

class AutomaticFragment : Fragment() {

    private var _binding: FragmentAutomaticBinding? = null
    private val binding get() = _binding!!

    // Obtener instancia del ViewModel compartido
    private val modeViewModel: ModeViewModel by activityViewModels()

    private val topicSensorLuz = "sensor/luz" // Topic para recibir datos del sensor
    // topicControl para enviar "AUTO" / "AUTO_OFF" se manejará vía ModeViewModel -> MqttHandler

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("AutomaticFragment", "onCreateView")
        _binding = FragmentAutomaticBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("AutomaticFragment", "onViewCreated")

        setupObservers()
        setupListeners()

        // Intentar conectar MQTT si es necesario. MqttHandler maneja si ya está conectado.
        MqttHandler.connect()
    }

    private fun setupObservers() {
        // Observar el estado de activación del Modo Automático desde el ViewModel
        modeViewModel.isAutomaticModeActive.observe(viewLifecycleOwner) { isActive ->
            Log.d("AutomaticFragment", "Observado isAutomaticModeActive: $isActive")
            binding.switchModoAutomatico.isChecked = isActive // Sincronizar el switch de la UI
            updateUiForModeState(isActive, MqttHandler.isConnected.value ?: false)
        }

        // Observar el estado de la conexión MQTT desde MqttHandler
        MqttHandler.isConnected.observe(viewLifecycleOwner) { isConnected ->
            Log.d("AutomaticFragment", "Observado MqttHandler.isConnected: $isConnected")
            updateUiForModeState(modeViewModel.isAutomaticModeActive.value ?: false, isConnected)
        }

        // Observar mensajes MQTT recibidos (para el sensor de luz)
        MqttHandler.receivedMessage.observe(viewLifecycleOwner) { messagePair ->
            val topic = messagePair.first
            val payload = messagePair.second

            // Solo actualizar si el modo automático está activo y el topic es el correcto
            if (topic == topicSensorLuz && modeViewModel.isAutomaticModeActive.value == true) {
                Log.d("AutomaticFragment", "Valor de Luz desde MQTT: $payload")
                binding.textviewLuzValue.text = payload
            }
        }
    }

    private fun setupListeners() {
        binding.switchModoAutomatico.setOnCheckedChangeListener { _, isChecked ->
            Log.d("AutomaticFragment", "Switch Modo Automático cambiado por usuario a: $isChecked")
            // Notificar al ViewModel sobre el cambio de estado.
            // El ViewModel se encargará de la lógica de exclusividad y de enviar "AUTO" / "AUTO_OFF"
            // a través de MqttHandler.
            modeViewModel.setAutomaticModeActive(isChecked)
        }
    }

    /**
     * Actualiza la UI (textos, suscripciones MQTT) basado en el estado del modo y la conexión.
     */
    private fun updateUiForModeState(isAutoModeActive: Boolean, isMqttConnected: Boolean) {
        if (!isMqttConnected) {
            binding.textviewAutoStatus.text = getString(R.string.broker_desconectado) // Usa strings.xml
            binding.textviewLuzValue.text = "---"
            MqttHandler.unsubscribeTopic(topicSensorLuz) // Asegurar desuscripción si no estamos conectados
            return
        }

        // Si estamos conectados al broker:
        if (isAutoModeActive) {
            binding.textviewAutoStatus.text = getString(R.string.auto_mode_activated) // Usa strings.xml
            MqttHandler.subscribeTopic(topicSensorLuz)
        } else {
            binding.textviewAutoStatus.text = getString(R.string.auto_mode_deactivated) // Usa strings.xml
            binding.textviewLuzValue.text = "---" // Limpiar valor de luz al desactivar modo
            MqttHandler.unsubscribeTopic(topicSensorLuz)
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d("AutomaticFragment", "onResume - Intentando conectar MQTT si es necesario")
        MqttHandler.connect()
        // La lógica de suscripción ahora está en updateUiForModeState,
        // que se dispara por los observers cuando cambian isAutomaticModeActive o isConnected.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("AutomaticFragment", "onDestroyView")
        // Es una buena práctica desuscribirse de topics específicos de este fragmento
        // cuando la vista se destruye, para evitar procesar mensajes innecesariamente.
        // MqttHandler.unsubscribeTopic se encarga de no fallar si no estaba suscrito o no conectado.
        MqttHandler.unsubscribeTopic(topicSensorLuz)
        Log.d("AutomaticFragment", "Intentando desuscribir de $topicSensorLuz en onDestroyView")
        _binding = null // Muy importante
    }
}