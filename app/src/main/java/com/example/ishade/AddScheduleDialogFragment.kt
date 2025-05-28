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
        val hour = binding.timePickerSchedule.hour
        val minute = binding.timePickerSchedule.minute

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
            daysOfWeek.addAll(listOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            ))
        }
        // Si el checkbox no está marcado, daysOfWeek permanece vacío.
        // Esto será interpretado por ScheduleFragment como "Solo para el día de hoy".
        // No es necesario un Toast aquí, ScheduleFragment dará feedback más preciso.

        val newSchedule = ScheduleItem(
            hour = hour,
            minute = minute,
            positionPercent = positionPercent,
            daysOfWeek = daysOfWeek,
            isEnabled = true
        )

        listener?.onScheduleAdded(newSchedule)
        // Este Toast es opcional, ya que ScheduleFragment también mostrará uno.
        // Podrías comentarlo o quitarlo si prefieres que solo ScheduleFragment notifique.
        // Toast.makeText(requireContext(), "Horario añadido", Toast.LENGTH_SHORT).show()
        dismiss()
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