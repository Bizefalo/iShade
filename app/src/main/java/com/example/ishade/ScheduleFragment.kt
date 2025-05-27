package com.example.ishade

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ishade.databinding.FragmentScheduleBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import androidx.core.content.edit
import android.os.Build
import android.net.Uri
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Locale

class ScheduleFragment : Fragment(), AddScheduleDialogFragment.AddScheduleDialogListener {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleAdapter: ScheduleAdapter
    private val scheduledItemsList = mutableListOf<ScheduleItem>()

    private val PREFS_NAME = "schedule_prefs"
    private val SCHEDULE_KEY = "schedules_list"
    private val gson = Gson()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ScheduleFragment", "onCreateView")
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ScheduleFragment", "onViewCreated")

        setupRecyclerView()
        loadSchedulesFromPrefs()

        binding.buttonAddSchedule.setOnClickListener {
            val dialog = AddScheduleDialogFragment.newInstance()
            dialog.listener = this
            dialog.show(parentFragmentManager, AddScheduleDialogFragment.TAG)
            Log.d("ScheduleFragment", "Botón Añadir Horario presionado, mostrando diálogo.")
        }
    }

    private fun setupRecyclerView() {
        Log.d("ScheduleFragment", "setupRecyclerView")
        scheduleAdapter = ScheduleAdapter(
            ArrayList(scheduledItemsList),
            onScheduleToggle = { scheduleItem, isEnabled ->
                val itemIndex = scheduledItemsList.indexOfFirst { it.id == scheduleItem.id }
                if (itemIndex != -1) {
                    scheduledItemsList[itemIndex].isEnabled = isEnabled
                    saveSchedulesToPrefs() // Guardar el cambio de estado
                    if (isEnabled) {
                        setAlarm(scheduledItemsList[itemIndex]) // Programar si se activa
                    } else {
                        cancelAlarm(scheduledItemsList[itemIndex]) // Cancelar si se desactiva
                    }
                    val status = if (isEnabled) "activado" else "desactivado"
                    Toast.makeText(requireContext(), "Horario ${scheduleItem.getTimeString()} ${status}", Toast.LENGTH_SHORT).show()
                    Log.d("ScheduleFragment", "Horario ${scheduleItem.id} toggle: $isEnabled")
                }
            },
            onEditClick = { scheduleItem ->
                // TODO: Abrir diálogo para editar este scheduleItem
                Toast.makeText(requireContext(), "Editar horario: ${scheduleItem.getTimeString()} (pendiente)", Toast.LENGTH_SHORT).show()
                Log.d("ScheduleFragment", "Editar horario: ${scheduleItem.id}")
            },
            onDeleteClick = { scheduleItem ->
                Toast.makeText(requireContext(), "Eliminar horario: ${scheduleItem.getTimeString()}", Toast.LENGTH_SHORT).show()
                Log.d("ScheduleFragment", "Eliminar horario: ${scheduleItem.id}")

                val position = scheduledItemsList.indexOfFirst { it.id == scheduleItem.id }
                if (position != -1) {
                    val removedItem = scheduledItemsList.removeAt(position)
                    scheduleAdapter.updateSchedules(ArrayList(scheduledItemsList))
                    updateEmptyViewVisibility()
                    saveSchedulesToPrefs() // Guardar la lista sin el ítem
                    cancelAlarm(removedItem) // Cancelar la alarma del ítem eliminado
                }
            }
        )
        binding.recyclerviewSchedules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    private fun updateEmptyViewVisibility() {
        if (scheduledItemsList.isEmpty()) {
            binding.textviewNoSchedules.visibility = View.VISIBLE
            binding.recyclerviewSchedules.visibility = View.GONE
        } else {
            binding.textviewNoSchedules.visibility = View.GONE
            binding.recyclerviewSchedules.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("ScheduleFragment", "onDestroyView")
        _binding = null
    }

    override fun onScheduleAdded(scheduleItem: ScheduleItem) {
        Log.d("ScheduleFragment", "Nuevo horario recibido: ${scheduleItem.getTimeString()} - ${scheduleItem.positionPercent}%")
        scheduledItemsList.add(scheduleItem)
        scheduleAdapter.updateSchedules(ArrayList(scheduledItemsList))
        updateEmptyViewVisibility()
        saveSchedulesToPrefs() // Guardar la lista actualizada

        if (scheduleItem.isEnabled) { // Solo programar alarma si el horario está habilitado al crearse
            setAlarm(scheduleItem)
        }
        Toast.makeText(requireContext(), "Nuevo horario añadido", Toast.LENGTH_SHORT).show()
    }

    private fun saveSchedulesToPrefs() {
        Log.d("ScheduleFragment", "Guardando ${scheduledItemsList.size} horarios en SharedPreferences")
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            val json = gson.toJson(scheduledItemsList)
            putString(SCHEDULE_KEY, json)
        }
    }

    private fun loadSchedulesFromPrefs() {
        Log.d("ScheduleFragment", "Cargando horarios desde SharedPreferences")
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(SCHEDULE_KEY, null)
        scheduledItemsList.clear() // Limpiar antes de cargar
        if (json != null) {
            try {
                val type = object : TypeToken<MutableList<ScheduleItem>>() {}.type
                val loadedSchedules: MutableList<ScheduleItem> = gson.fromJson(json, type)
                scheduledItemsList.addAll(loadedSchedules)
                Log.d("ScheduleFragment", "Cargados ${scheduledItemsList.size} horarios.")
            } catch (e: Exception) {
                Log.e("ScheduleFragment", "Error al parsear horarios desde JSON", e)
                // Opcional: borrar SharedPreferences si están corruptas
                // prefs.edit().remove(SCHEDULE_KEY).apply()
            }
        } else {
            Log.d("ScheduleFragment", "No se encontraron horarios guardados.")
            // Opcional: cargar ejemplos si no hay nada guardado y la lista está vacía
            // loadSampleSchedules()
        }
        scheduleAdapter.updateSchedules(ArrayList(scheduledItemsList))
        updateEmptyViewVisibility()
    }

    private fun setAlarm(scheduleItem: ScheduleItem) {
        // Formateador de fechas para logs
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        if (!scheduleItem.isEnabled) {
            Log.d("ScheduleFragment", "Horario ID ${scheduleItem.id} (${scheduleItem.getTimeString()}) no está habilitado, no se programa alarma.")
            cancelAlarm(scheduleItem)
            return
        }

        // Asumimos que scheduleItem.daysOfWeek es Set<Int> con constantes de Calendar.DAY_OF_WEEK
        if (scheduleItem.daysOfWeek.isEmpty()) {
            Log.d("ScheduleFragment", "Horario ID ${scheduleItem.id} (${scheduleItem.getTimeString()}) no tiene días de repetición, no se programa alarma.")
            cancelAlarm(scheduleItem)
            return
        }

        val alarmManager = requireActivity().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Calcular el próximo tiempo de disparo
        val triggerTimeMillis = ScheduleItem.calculateNextTriggerTimeInMillis( // <--- LLAMADA ACTUALIZADA
            hour = scheduleItem.hour,
            minute = scheduleItem.minute,
            selectedDaysOfWeek = scheduleItem.daysOfWeek
            // Aquí no pasamos baseCalendar, así que usará Calendar.getInstance() por defecto,
            // lo cual es correcto para cuando se programa una alarma por primera vez desde el Fragment.
        )

        if (triggerTimeMillis == null) {
            Log.e("ScheduleFragment", "No se pudo calcular el tiempo de activación para el horario ID ${scheduleItem.id}. No se programará la alarma.")
            Toast.makeText(requireContext(), "Error al calcular la hora del horario.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), ScheduleAlarmReceiver::class.java).apply {
            action = "com.example.ishade.SCHEDULE_ALARM.${scheduleItem.id}"
            putExtra("SCHEDULE_ID", scheduleItem.id)
            putExtra("POSITION_PERCENT", scheduleItem.positionPercent)
            putExtra("TIME_STRING", scheduleItem.getTimeString()) // Hora original para mostrar
            // Pasa el conjunto de días seleccionados para que el receiver pueda reprogramar correctamente.
            // Convertimos el Set<Int> a IntArray para pasarlo en el Intent.
            putExtra("DAYS_OF_WEEK", scheduleItem.daysOfWeek.toIntArray())
        }

        val requestCode = scheduleItem.id.toInt() // Considera una estrategia de ID más robusta si los IDs son Longs muy grandes.
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // CRÍTICO para Android 12 (S) y superior, especialmente en Android 14.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w("ScheduleFragment", "PERMISO REQUERIDO: La app no tiene permiso para programar alarmas exactas. ID Horario: ${scheduleItem.id}.")
                Toast.makeText(requireContext(), "Permiso para alarmas exactas necesario. Por favor, habilítalo en ajustes.", Toast.LENGTH_LONG).show()

                // Intenta guiar al usuario a los ajustes de la app para que pueda otorgar el permiso.
                // Esto puede variar ligeramente entre versiones de Android y fabricantes.
                try {
                    // Para API 31 (Android 12) se introdujo ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    // pero puede que no esté disponible o no funcione en todos los casos si targetSdk es bajo.
                    // Una opción más genérica es abrir los detalles de la app.
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    settingsIntent.data = Uri.fromParts("package", requireContext().packageName, null)
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(settingsIntent) // O mostrar un diálogo con estas instrucciones
                    Log.i("ScheduleFragment","Intentando abrir ajustes de la app para permisos de alarma.")
                } catch (e: Exception) {
                    Log.e("ScheduleFragment", "Error al intentar abrir ajustes de permisos.", e)
                    Toast.makeText(requireContext(), "Por favor, habilita manualmente el permiso 'Alarmas y recordatorios' en los ajustes de la app.", Toast.LENGTH_LONG).show()
                }
                // NO programes la alarma si no tienes el permiso, ya que no funcionará o será impredecible.
                // Opcionalmente, podrías usar una alarma no exacta aquí como fallback si el diseño lo permite.
                return // Salir para no intentar programar una alarma exacta sin permiso.
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )

            val triggerTimeCalendar = Calendar.getInstance().apply { timeInMillis = triggerTimeMillis }
            Log.i("ScheduleFragment", "Alarma EXACTA programada para ID ${scheduleItem.id} (${scheduleItem.getTimeString()}). Próxima activación: ${sdf.format(triggerTimeCalendar.time)} (Epoch: $triggerTimeMillis)")
            Toast.makeText(requireContext(), "Horario para ${scheduleItem.getTimeString()} programado (${ScheduleItem.formatSelectedDays(scheduleItem.daysOfWeek, Locale.getDefault())})", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Log.e("ScheduleFragment", "SecurityException al programar alarma exacta para ID ${scheduleItem.id}. El permiso puede haber sido revocado.", e)
            Toast.makeText(requireContext(), "Error de seguridad al programar alarma. Verifica los permisos.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("ScheduleFragment", "Excepción general al programar alarma para ID ${scheduleItem.id}.", e)
            Toast.makeText(requireContext(), "Error inesperado al programar horario.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm(scheduleItem: ScheduleItem) {
        val alarmManager = requireActivity().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ScheduleAlarmReceiver::class.java).apply{
            action = "com.example.ishade.SCHEDULE_ALARM.${scheduleItem.id}"
        }
        val requestCode = scheduleItem.id.toInt()
        // Es crucial usar FLAG_NO_CREATE al intentar obtener un PendingIntent solo para cancelarlo,
        // si no estás seguro de si existe. Pero para asegurar que se cancela el mismo que se creó,
        // es mejor recrearlo con los mismos parámetros (como FLAG_UPDATE_CURRENT) y luego cancelarlo.
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.i("ScheduleFragment", "Alarma cancelada para ID ${scheduleItem.id} (${scheduleItem.getTimeString()})")
        Toast.makeText(requireContext(), "Alarma para ${scheduleItem.getTimeString()} cancelada", Toast.LENGTH_SHORT).show()
    }
}