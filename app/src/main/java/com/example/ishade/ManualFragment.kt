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
import androidx.fragment.app.activityViewModels // Importar para activityViewModels
//import androidx.lifecycle.Observer // Importar para Observer
import com.example.ishade.databinding.FragmentManualBinding
// Ya no necesitas las importaciones directas de Paho aquí si todo se maneja vía MqttHandler

class ManualFragment : Fragment() {

    private var _binding: FragmentManualBinding? = null
    private val binding get() = _binding!!

    // Obtener instancia del ViewModel compartido
    private val modeViewModel: ModeViewModel by activityViewModels()

    private var isMotorMoving: Boolean = false
    private val topicControl = "cortina/control" // Topic del ESP32

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualBinding.inflate(inflater, container, false)
        Log.d("ManualFragment", "onCreateView")
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ManualFragment", "onViewCreated")

        setupObservers()
        setupListeners()

        // Intentar conectar MQTT si es necesario (MqttHandler se encargará de no reconectar si ya lo está)
        // Esto es útil si el usuario navega directamente a este fragment y la conexión no se inició globalmente.
        MqttHandler.connect()
    }

    private fun setupObservers() {
        // Observar el estado de activación del Modo Manual desde el ViewModel
        modeViewModel.isManualModeActive.observe(viewLifecycleOwner) { isActive ->
            Log.d("ManualFragment", "Observed isManualModeActive: $isActive")
            binding.switchActivarManual.isChecked = isActive
            actualizarEstadoControlesVisuales(isActive)
        }

        // Observar el estado de la conexión MQTT desde MqttHandler
        MqttHandler.isConnected.observe(viewLifecycleOwner) { isConnected ->
            Log.d("ManualFragment", "Observed MqttHandler.isConnected: $isConnected")
            // Actualizar el texto de estado del broker y potencialmente los controles
            actualizarEstadoControlesVisuales(binding.switchActivarManual.isChecked)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.switchActivarManual.setOnCheckedChangeListener { _, isChecked ->
            // Solo reaccionar al cambio si es diferente del estado actual en el ViewModel
            // para evitar bucles si la actualización del switch se hace programáticamente.
            // O, más simple, el ViewModel ya tiene una guarda if (_isManualModeActive.value == isActive) return
            Log.d("ManualFragment", "Switch Manual cambiado por usuario a: $isChecked")
            modeViewModel.setManualModeActive(isChecked)
        }

        binding.buttonSubirManual.setOnTouchListener { v, event ->
            if (binding.switchActivarManual.isChecked && MqttHandler.isConnected.value == true) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.textviewManualStatus.text = getString(R.string.manual_status_subiendo)
                        MqttHandler.publishMessage(topicControl, "SUBIR")
                        isMotorMoving = true
                        v.performClick() // No es necesario para onTouch si solo es para mantener presionado
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        binding.textviewManualStatus.text = getString(R.string.manual_status_detenido)
                        MqttHandler.publishMessage(topicControl, "DETENER")
                        isMotorMoving = false
                        true
                    }
                    else -> false
                }
            } else {
                if (MqttHandler.isConnected.value != true) {
                    Toast.makeText(requireContext(), "Broker Desconectado", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }

        binding.buttonBajarManual.setOnTouchListener { v, event ->
            if (binding.switchActivarManual.isChecked && MqttHandler.isConnected.value == true) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.textviewManualStatus.text = getString(R.string.manual_status_bajando)
                        MqttHandler.publishMessage(topicControl, "BAJAR")
                        isMotorMoving = true
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        binding.textviewManualStatus.text = getString(R.string.manual_status_detenido)
                        MqttHandler.publishMessage(topicControl, "DETENER")
                        isMotorMoving = false
                        true
                    }
                    else -> false
                }
            } else {
                if (MqttHandler.isConnected.value != true) {
                    Toast.makeText(requireContext(), "Broker Desconectado", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }

    private fun actualizarEstadoControlesVisuales(manualActivadoPorViewModel: Boolean) {
        val conectadoAlBroker = MqttHandler.isConnected.value ?: false

        // El switch ya se actualiza por el observer de modeViewModel.isManualModeActive
        // binding.switchActivarManual.isChecked = manualActivadoPorViewModel

        // Habilitar botones solo si el modo manual está activo Y estamos conectados al broker
        binding.buttonSubirManual.isEnabled = manualActivadoPorViewModel && conectadoAlBroker
        binding.buttonBajarManual.isEnabled = manualActivadoPorViewModel && conectadoAlBroker

        if (!conectadoAlBroker) {
            binding.textviewManualStatus.text = getString(R.string.broker_desconectado)
        } else {
            if (manualActivadoPorViewModel) {
                // Si el motor no se está moviendo activamente por un botón, mostrar "Modo Manual Activado"
                if (!isMotorMoving) {
                    binding.textviewManualStatus.text = getString(R.string.manual_status_modo_activado)
                }
            } else {
                binding.textviewManualStatus.text = getString(R.string.manual_status_modo_desactivado)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ManualFragment", "onResume - Intentando conectar MQTT si es necesario")
        // MqttHandler se encarga de no reconectar si ya lo está.
        // Es bueno asegurar que al menos se intente la conexión cuando el fragmento es visible.
        MqttHandler.connect()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("ManualFragment", "onDestroyView")
        if (isMotorMoving && MqttHandler.isConnected.value == true) {
            // Si el motor se estaba moviendo y el fragmento se destruye, enviar DETENER.
            MqttHandler.publishMessage(topicControl, "DETENER")
            Log.d("ManualFragment", "Enviando DETENER en onDestroyView porque el motor se estaba moviendo.")
        }
        isMotorMoving = false
        _binding = null // Muy importante para evitar fugas de memoria con ViewBinding
    }
}