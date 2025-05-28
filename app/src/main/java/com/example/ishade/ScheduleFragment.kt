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
        // Formateador de fechas para logs más detallados
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        val alarmManager = requireActivity().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!scheduleItem.isEnabled) {
            Log.d("ScheduleFragment", "Horario ID ${scheduleItem.id} (${scheduleItem.getTimeString()}) no está habilitado, cancelando cualquier alarma existente.")
            cancelAlarm(scheduleItem)
            return
        }

        var triggerTimeMillis: Long? = null
        val intentAction = "com.example.ishade.SCHEDULE_ALARM.${scheduleItem.id}"
        // Usamos un Bundle para organizar los extras que irán al Intent
        val intentExtras = Bundle().apply {
            putLong("SCHEDULE_ID", scheduleItem.id)
            putInt("POSITION_PERCENT", scheduleItem.positionPercent)
            putString("TIME_STRING", scheduleItem.getTimeString())
        }
        var alarmTypeForDisplay = "" // Para logs y Toasts

        if (scheduleItem.daysOfWeek.isNotEmpty()) { // CASO: "Todos los días"
            alarmTypeForDisplay = "Todos los días"
            Log.d("ScheduleFragment", "Programando alarma '$alarmTypeForDisplay' para ID ${scheduleItem.id}")

            triggerTimeMillis = ScheduleItem.calculateNextTriggerTimeInMillis(
                hour = scheduleItem.hour,
                minute = scheduleItem.minute,
                selectedDaysOfWeek = scheduleItem.daysOfWeek
                // baseCalendar por defecto es Calendar.getInstance(), lo cual es correcto aquí.
            )
            // Para "Todos los días", pasamos el array de días para que se reprograme.
            intentExtras.putIntArray("DAYS_OF_WEEK", scheduleItem.daysOfWeek.toIntArray())

        } else { // CASO: "Solo para el día de hoy" (scheduleItem.daysOfWeek está vacío)
            alarmTypeForDisplay = "Solo hoy"
            Log.d("ScheduleFragment", "Programando alarma '$alarmTypeForDisplay' para ID ${scheduleItem.id}")

            val calendarToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, scheduleItem.hour)
                set(Calendar.MINUTE, scheduleItem.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (calendarToday.after(Calendar.getInstance())) { // Comprobar si la hora es futura para hoy
                triggerTimeMillis = calendarToday.timeInMillis
                // Para "Solo hoy", explícitamente pasamos un IntArray vacío para DAYS_OF_WEEK.
                // El receiver lo interpretará como "no reprogramar".
                intentExtras.putIntArray("DAYS_OF_WEEK", intArrayOf())
            } else {
                val currentTimeFormatted = sdf.format(Calendar.getInstance().time)
                Log.w("ScheduleFragment", "La hora ${scheduleItem.getTimeString()} para '$alarmTypeForDisplay' (ID ${scheduleItem.id}) ya pasó hoy (actual: $currentTimeFormatted). No se programará.")
                Toast.makeText(requireContext(), "La hora para '$alarmTypeForDisplay' (${scheduleItem.getTimeString()}) ya pasó hoy.", Toast.LENGTH_LONG).show()
                // No se programa; triggerTimeMillis permanecerá null.
            }
        }

        if (triggerTimeMillis == null) {
            Log.e("ScheduleFragment", "No se pudo determinar un tiempo de activación válido para el horario ID ${scheduleItem.id} ($alarmTypeForDisplay).")
            // El Toast de "hora pasada" (si fue el caso para 'Solo hoy') ya se mostró.
            // Mostrar un error genérico solo si fue por otra causa (ej. para 'Todos los días').
            if (scheduleItem.daysOfWeek.isNotEmpty()) {
                Toast.makeText(requireContext(), "Error al calcular la hora para el horario '$alarmTypeForDisplay'.", Toast.LENGTH_SHORT).show()
            }
            return // No continuar si no hay tiempo de activación.
        }

        val intent = Intent(requireContext(), ScheduleAlarmReceiver::class.java).apply {
            action = intentAction
            putExtras(intentExtras) // Añadir todos los extras desde el Bundle.
        }

        val requestCode = scheduleItem.id.toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w("ScheduleFragment", "PERMISO REQUERIDO para programar alarma exacta (ID ${scheduleItem.id}). La app no tiene permiso.")
                Toast.makeText(requireContext(), "Permiso para alarmas exactas necesario. Por favor, habilítalo en los ajustes de la app.", Toast.LENGTH_LONG).show()
                try {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    settingsIntent.data = Uri.fromParts("package", requireContext().packageName, null)
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    requireContext().startActivity(settingsIntent) // Usar requireContext() desde un Fragment
                    Log.i("ScheduleFragment","Intentando abrir ajustes de la app para permisos de alarma.")
                } catch (e: Exception) {
                    Log.e("ScheduleFragment", "Error al intentar abrir ajustes de permisos de alarma.", e)
                    Toast.makeText(requireContext(), "Por favor, habilita manualmente el permiso 'Alarmas y recordatorios' en los ajustes de la app.", Toast.LENGTH_LONG).show()
                }
                return // No intentar programar la alarma si no hay permiso.
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)

            val triggerTimeCalendar = Calendar.getInstance().apply { timeInMillis = triggerTimeMillis!! } // triggerTimeMillis no será null aquí.
            // Usamos la función de ScheduleItem para formatear los días para el Toast y el Log.
            val formattedDaysForToast = ScheduleItem.formatSelectedDays(scheduleItem.daysOfWeek, Locale.getDefault())
            Log.i("ScheduleFragment", "Alarma EXACTA ($alarmTypeForDisplay) programada para ID ${scheduleItem.id} (${scheduleItem.getTimeString()}). Próxima activación: ${sdf.format(triggerTimeCalendar.time)} (Epoch: $triggerTimeMillis). Días: $formattedDaysForToast")
            Toast.makeText(requireContext(), "Horario ($alarmTypeForDisplay) para ${scheduleItem.getTimeString()} programado ($formattedDaysForToast)", Toast.LENGTH_LONG).show()

        } catch (e: SecurityException) {
            Log.e("ScheduleFragment", "SecurityException al programar alarma exacta para ID ${scheduleItem.id}. El permiso puede haber sido revocado.", e)
            Toast.makeText(requireContext(), "Error de seguridad al programar la alarma. Verifica los permisos de la aplicación.", Toast.LENGTH_LONG).show()
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