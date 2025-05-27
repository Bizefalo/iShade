package com.example.ishade

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ishade.databinding.DialogAddScheduleBinding
import java.util.Calendar

class AddScheduleDialogFragment : DialogFragment() {

    private var _binding: DialogAddScheduleBinding? = null
    private val binding get() = _binding!!

    // Listener para comunicar el resultado de vuelta al ScheduleFragment
    interface AddScheduleDialogListener {
        fun onScheduleAdded(scheduleItem: ScheduleItem)
    }
    var listener: AddScheduleDialogListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddScheduleBinding.inflate(inflater, container, false)
        //dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent) // Opcional: para esquinas redondeadas si tu tema lo soporta
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPositionSpinner()

        // Configurar TimePicker a 24 horas si se prefiere (opcional)
        // binding.timePickerSchedule.setIs24HourView(true)


        binding.buttonSaveSchedule.setOnClickListener {
            saveSchedule()
        }

        binding.buttonCancelSchedule.setOnClickListener {
            dismiss() // Cierra el diálogo
        }
    }

    private fun setupPositionSpinner() {
        // Opciones para el Spinner de posición
        val positionOptions = listOf(
            "0% (Cerrada)",
            "25% Abierta",
            "50% Abierta",
            "75% Abierta",
            "100% Abierta"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, positionOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPositionPercent.adapter = adapter
        binding.spinnerPositionPercent.setSelection(2) // Poner 50% como default, por ejemplo
    }

    private fun saveSchedule() {
        val hour = binding.timePickerSchedule.hour // En API 23+ .hour, en anteriores .currentHour
        val minute = binding.timePickerSchedule.minute // En API 23+ .minute, en anteriores .currentMinute

        val selectedPositionString = binding.spinnerPositionPercent.selectedItem.toString()
        val positionPercent = when (selectedPositionString) {
            "0% (Cerrada)" -> 0
            "25% Abierta" -> 25
            "50% Abierta" -> 50
            "75% Abierta" -> 75
            "100% Abierta" -> 100
            else -> 50 // Un default por si acaso
        }

        val daysOfWeek = mutableSetOf<Int>()
        if (binding.checkboxRepeatEveryDay.isChecked) {
            daysOfWeek.add(Calendar.SUNDAY)
            daysOfWeek.add(Calendar.MONDAY)
            daysOfWeek.add(Calendar.TUESDAY)
            daysOfWeek.add(Calendar.WEDNESDAY)
            daysOfWeek.add(Calendar.THURSDAY)
            daysOfWeek.add(Calendar.FRIDAY)
            daysOfWeek.add(Calendar.SATURDAY)
        } else {
            // TODO: Implementar lógica para seleccionar días individuales si el checkbox no está marcado
            // Por ahora, si no es "Todos los días", lo dejamos como "Nunca" (set vacío) o podrías
            // por defecto añadir el día actual o no añadir ninguno.
            // Para la simplicidad inicial, si no es "todos los días", el Set quedará vacío.
            // O podrías mostrar un Toast indicando que se debe seleccionar días (si tuvieras la UI para ello)
            Toast.makeText(requireContext(), "Repetición no configurada (solo 'Todos los días' por ahora)", Toast.LENGTH_LONG).show()
            // Podríamos decidir no guardar si no se ha especificado la repetición, o guardarlo como "nunca"
        }

        // Crear el objeto ScheduleItem
        // El ID se genera automáticamente en la data class por ahora
        val newSchedule = ScheduleItem(
            hour = hour,
            minute = minute,
            positionPercent = positionPercent,
            daysOfWeek = daysOfWeek,
            isEnabled = true // Por defecto, los nuevos horarios están habilitados
        )

        // Notificar al listener (ScheduleFragment)
        listener?.onScheduleAdded(newSchedule)
        Toast.makeText(requireContext(), "Horario guardado (simulado)", Toast.LENGTH_SHORT).show()
        dismiss() // Cerrar el diálogo
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Limpiar la referencia al binding
    }

    // Opcional: Para hacer el diálogo más ancho si es necesario
    // override fun onStart() {
    //     super.onStart()
    //     dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    // }

    companion object {
        const val TAG = "AddScheduleDialog" // Tag para mostrar el diálogo

        // Método factoría para crear instancias del diálogo (opcional pero buena práctica)
        fun newInstance(): AddScheduleDialogFragment {
            return AddScheduleDialogFragment()
        }
    }
}