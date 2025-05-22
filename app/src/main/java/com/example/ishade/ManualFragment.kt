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

class ManualFragment : Fragment() {

    private var _binding: FragmentManualBinding? = null
    private val binding get() = _binding!!

    // Variable para rastrear si un comando de movimiento está activo (por mantener presionado)
    private var isMotorMoving: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actualizarEstadoControles(binding.switchActivarManual.isChecked)

        binding.switchActivarManual.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstadoControles(isChecked)
            if (!isChecked && isMotorMoving) {
                enviarComandoMqtt("DETENER")
                binding.textviewManualStatus.text = "Modo Manual Desactivado - Movimiento Detenido"
                isMotorMoving = false
            }
            // TODO: Lógica global de activación/desactivación de modo
        }

        // Listener táctil para el botón Subir
        binding.buttonSubirManual.setOnTouchListener { v, event ->
            if (binding.switchActivarManual.isChecked) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.textviewManualStatus.text = "Subiendo..."
                        enviarComandoMqtt("SUBIR_CONTINUO")
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
            } else {
                false // Si el switch está apagado, no consumir el evento
            }
        }

        // Listener táctil para el botón Bajar
        binding.buttonBajarManual.setOnTouchListener { v, event ->
            if (binding.switchActivarManual.isChecked) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.textviewManualStatus.text = "Bajando..."
                        enviarComandoMqtt("BAJAR_CONTINUO")
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
            } else {
                false
            }
        }
    }

    private fun actualizarEstadoControles(manualActivado: Boolean) {
        binding.buttonSubirManual.isEnabled = manualActivado
        binding.buttonBajarManual.isEnabled = manualActivado

        if (manualActivado) {
            binding.textviewManualStatus.text = "Modo Manual Activado"
        } else {
            binding.textviewManualStatus.text = "Modo Manual Desactivado"

        }
    }

    private fun enviarComandoMqtt(comando: String) {
        // TODO: Implementar la lógica real para enviar el mensaje MQTT
        Log.d("MQTT", "Enviando comando: $comando")
        Toast.makeText(requireContext(), "Comando MQTT: $comando", Toast.LENGTH_SHORT).show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        // Si la vista se destruye y el motor se estaba moviendo, enviar comando de DETENER
        if (isMotorMoving) {
            enviarComandoMqtt("DETENER")
            isMotorMoving = false
        }
        _binding = null
    }
}