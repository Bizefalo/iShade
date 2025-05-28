package com.example.ishade // Asegúrate que este sea tu paquete correcto

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.core.content.edit

class ModeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ModeViewModel"
        const val PREFS_MODE_STATE = "mode_state_prefs"
        const val KEY_MANUAL_MODE_ACTIVE = "manual_mode_active"
        const val KEY_AUTOMATIC_MODE_ACTIVE = "automatic_mode_active"
        const val KEY_SCHEDULED_MODE_ACTIVE = "scheduled_mode_active" // Para el switch de Fijar Horario
    }

    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences(PREFS_MODE_STATE, Context.MODE_PRIVATE)

    // LiveData para el estado del Modo Manual
    private val _isManualModeActive = MutableLiveData<Boolean>()
    val isManualModeActive: LiveData<Boolean> = _isManualModeActive

    // LiveData para el estado del Modo Automático
    private val _isAutomaticModeActive = MutableLiveData<Boolean>()
    val isAutomaticModeActive: LiveData<Boolean> = _isAutomaticModeActive

    // LiveData para el estado del Modo Horarios Programados
    private val _isScheduledModeActive = MutableLiveData<Boolean>()
    val isScheduledModeActive: LiveData<Boolean> = _isScheduledModeActive

    init {
        // Cargar estados iniciales desde SharedPreferences
        _isManualModeActive.value = sharedPreferences.getBoolean(KEY_MANUAL_MODE_ACTIVE, false)
        _isAutomaticModeActive.value = sharedPreferences.getBoolean(KEY_AUTOMATIC_MODE_ACTIVE, false)
        // Para el modo programado, es buena idea que por defecto esté activado si ya hay horarios,
        // o desactivado si es la primera vez. Por ahora, lo dejaremos en false por defecto.
        // O podría ser true si el usuario quiere que los horarios se activen por defecto.
        _isScheduledModeActive.value = sharedPreferences.getBoolean(KEY_SCHEDULED_MODE_ACTIVE, true) // Default true para horarios

        Log.d(TAG, "Estados iniciales cargados: Manual=${_isManualModeActive.value}, Auto=${_isAutomaticModeActive.value}, Horarios=${_isScheduledModeActive.value}")
    }

    /**
     * Activa o desactiva el Modo Manual.
     * Si se activa, los otros modos se desactivan.
     */
    fun setManualModeActive(isActive: Boolean) {
        if (_isManualModeActive.value == isActive) return // Sin cambios

        _isManualModeActive.value = isActive
        sharedPreferences.edit { putBoolean(KEY_MANUAL_MODE_ACTIVE, isActive) }
        Log.i(TAG, "Modo Manual establecido a: $isActive")

        if (isActive) {
            // Desactivar otros modos
            setAutomaticModeActiveInternal(false, sendMqtt = true) // Envía AUTO_OFF
            setScheduledModeActiveInternal(false)
            MqttHandler.publishMessage("cortina/control", "MANUAL") // Informa al ESP32
        }
        // Si se desactiva el modo manual, no se envía un comando MQTT específico aquí,
        // ya que otro modo tomará el control o la cortina quedará en estado neutral.
        // El ESP32 recibe "DETENER" cuando los botones manuales se sueltan.
    }

    /**
     * Activa o desactiva el Modo Automático.
     * Si se activa, los otros modos se desactivan.
     */
    fun setAutomaticModeActive(isActive: Boolean) {
        setAutomaticModeActiveInternal(isActive, sendMqtt = true)
    }

    private fun setAutomaticModeActiveInternal(isActive: Boolean, sendMqtt: Boolean) {
        if (_isAutomaticModeActive.value == isActive) return // Sin cambios

        _isAutomaticModeActive.value = isActive
        sharedPreferences.edit { putBoolean(KEY_AUTOMATIC_MODE_ACTIVE, isActive) }
        Log.i(TAG, "Modo Automático establecido a: $isActive")

        if (isActive) {
            // Desactivar otros modos
            setManualModeActiveInternal(false)
            setScheduledModeActiveInternal(false)
            if (sendMqtt) MqttHandler.publishMessage("cortina/control", "AUTO")
        } else {
            if (sendMqtt) MqttHandler.publishMessage("cortina/control", "AUTO_OFF")
        }
    }

    /**
     * Activa o desactiva el Modo Horarios Programados.
     * Si se activa, los otros modos se desactivan.
     */
    fun setScheduledModeActive(isActive: Boolean) {
        setScheduledModeActiveInternal(isActive)
    }

    private fun setScheduledModeActiveInternal(isActive: Boolean) {
        if (_isScheduledModeActive.value == isActive) return // Sin cambios

        _isScheduledModeActive.value = isActive
        sharedPreferences.edit().putBoolean(KEY_SCHEDULED_MODE_ACTIVE, isActive).apply()
        Log.i(TAG, "Modo Horarios Programados establecido a: $isActive")

        if (isActive) {
            // Desactivar otros modos
            setManualModeActiveInternal(false)
            // Cuando se activan los horarios, el modo automático del ESP32 también debería desactivarse.
            // El ESP32 ya desactiva su modo automático cuando recibe SETPOS_X.
            // Si el modo automático de la app estaba activo, se enviará AUTO_OFF.
            setAutomaticModeActiveInternal(false, sendMqtt = true)
        }
        // Si se desactiva el modo horarios (isActive = false),
        // ScheduleFragment observará este cambio y deberá cancelar todas las alarmas.
    }

    // Funciones internas para evitar bucles de llamadas MQTT al desactivar modos
    private fun setManualModeActiveInternal(isActive: Boolean) {
        if (_isManualModeActive.value == isActive) return
        _isManualModeActive.value = isActive
        sharedPreferences.edit { putBoolean(KEY_MANUAL_MODE_ACTIVE, isActive) }
        Log.d(TAG, "Interno: Modo Manual establecido a: $isActive")
        // No enviamos MQTT "MANUAL" desde aquí para evitar bucles, el método público lo hace.
    }
}