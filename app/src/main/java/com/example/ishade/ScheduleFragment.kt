package com.example.ishade

import android.content.Context
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

class ScheduleFragment : Fragment(), AddScheduleDialogFragment.AddScheduleDialogListener {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleAdapter: ScheduleAdapter
    private val scheduledItemsList = mutableListOf<ScheduleItem>()

    // Constantes para SharedPreferences
    private val PREFS_NAME = "schedule_prefs"
    private val SCHEDULE_KEY = "schedules_list"
    private val gson = Gson() // Instancia de Gson

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
        loadSchedulesFromPrefs() // Cargar horarios guardados en lugar de los de ejemplo

        binding.buttonAddSchedule.setOnClickListener {
            val dialog = AddScheduleDialogFragment.newInstance()
            dialog.listener = this
            dialog.show(parentFragmentManager, AddScheduleDialogFragment.TAG)
            Log.d("ScheduleFragment", "Botón Añadir Horario presionado, mostrando diálogo.")
        }
    }

    private fun setupRecyclerView() {
        // ... (el código de setupRecyclerView se mantiene igual, solo asegúrate que usa scheduledItemsList) ...
        // ... te lo pongo completo para claridad ...
        Log.d("ScheduleFragment", "setupRecyclerView")
        scheduleAdapter = ScheduleAdapter(
            ArrayList(scheduledItemsList),
            onScheduleToggle = { scheduleItem, isEnabled ->
                scheduleItem.isEnabled = isEnabled
                saveSchedulesToPrefs() // Guardar cambios
                // TODO: Reprogramar/Cancelar la alarma
                val status = if (isEnabled) "activado" else "desactivado"
                Toast.makeText(requireContext(), "Horario ${scheduleItem.getTimeString()} ${status}", Toast.LENGTH_SHORT).show()
            },
            onEditClick = { scheduleItem ->
                // TODO: Abrir diálogo para editar
                Toast.makeText(requireContext(), "Editar horario: ${scheduleItem.getTimeString()} (pendiente)", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { scheduleItem ->
                Toast.makeText(requireContext(), "Eliminar horario: ${scheduleItem.getTimeString()} (pendiente)", Toast.LENGTH_SHORT).show()
                val position = scheduledItemsList.indexOf(scheduleItem)
                if (position != -1) {
                    scheduledItemsList.removeAt(position)
                    scheduleAdapter.updateSchedules(ArrayList(scheduledItemsList))
                    updateEmptyViewVisibility()
                    saveSchedulesToPrefs() // Guardar cambios
                    // TODO: Cancelar la alarma
                }
            }
        )
        binding.recyclerviewSchedules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    // Ya no necesitamos loadSampleSchedules si cargamos desde SharedPreferences
    // private fun loadSampleSchedules() { ... }

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

    // Método de la interfaz AddScheduleDialogListener
    override fun onScheduleAdded(scheduleItem: ScheduleItem) {
        Log.d("ScheduleFragment", "Nuevo horario recibido: ${scheduleItem.getTimeString()} - ${scheduleItem.positionPercent}%")
        scheduledItemsList.add(scheduleItem)
        scheduleAdapter.updateSchedules(ArrayList(scheduledItemsList))
        updateEmptyViewVisibility()
        saveSchedulesToPrefs() // Guardar la lista actualizada
        // TODO: Programar la alarma con AlarmManager
        Toast.makeText(requireContext(), "Nuevo horario añadido", Toast.LENGTH_SHORT).show()
    }

    // Nuevas funciones para guardar y cargar SharedPreferences
    private fun saveSchedulesToPrefs() {
        Log.d("ScheduleFragment", "Guardando ${scheduledItemsList.size} horarios en SharedPreferences")
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            val json = gson.toJson(scheduledItemsList) // Convertir lista a JSON
            putString(SCHEDULE_KEY, json)
        } // Guardar asincrónicamente
    }

    private fun loadSchedulesFromPrefs() {
        Log.d("ScheduleFragment", "Cargando horarios desde SharedPreferences")
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(SCHEDULE_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<ScheduleItem>>() {}.type // Tipo para la deserialización
            val loadedSchedules: MutableList<ScheduleItem> = gson.fromJson(json, type)
            scheduledItemsList.clear()
            scheduledItemsList.addAll(loadedSchedules)
            Log.d("ScheduleFragment", "Cargados ${scheduledItemsList.size} horarios.")
        } else {
            Log.d("ScheduleFragment", "No se encontraron horarios guardados, cargando ejemplos.")
            // Si no hay nada guardado, podrías cargar los de ejemplo o empezar vacío
            // loadSampleSchedules() // Opcional: cargar ejemplos si no hay nada guardado
        }
        // Actualizar el adaptador con la lista cargada (o vacía, o de ejemplos)
        scheduleAdapter.updateSchedules(ArrayList(scheduledItemsList))
        updateEmptyViewVisibility()
    }
}