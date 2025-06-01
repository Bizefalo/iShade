package com.example.ishade

import android.app.Dialog
import android.os.Build // Necesario para la comprobación de versión de getParcelable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast // Lo mantenemos por ahora, aunque algunos Toasts se podrían eliminar
import androidx.fragment.app.DialogFragment
import com.example.ishade.databinding.DialogAddScheduleBinding
import java.util.Calendar

class AddScheduleDialogFragment : DialogFragment() {

    private var _binding: DialogAddScheduleBinding? = null
    private val binding get() = _binding!!

    // Listener para comunicar el resultado de vuelta al ScheduleFragment
    interface AddScheduleDialogListener {
        // Reutilizaremos onScheduleAdded. ScheduleFragment distinguirá si es nuevo o editado por el ID.
        fun onScheduleAdded(scheduleItem: ScheduleItem)
    }
    var listener: AddScheduleDialogListener? = null

    private var editingScheduleItem: ScheduleItem? = null // Para guardar el item que se está editando

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Recuperar el ScheduleItem si se pasó para edición
            editingScheduleItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_SCHEDULE_ITEM, ScheduleItem::class.java)
            } else {
                @Suppress("DEPRECATION") // Necesario para versiones < TIRAMISU
                it.getParcelable(ARG_SCHEDULE_ITEM)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPositionSpinner()
        // binding.timePickerSchedule.setIs24HourView(true) // Opcional

        // Si estamos editando, pre-rellenar los campos
        editingScheduleItem?.let { item ->
            binding.timePickerSchedule.hour = item.hour
            binding.timePickerSchedule.minute = item.minute

            // Mapear el positionPercent al índice del spinner
            val positionToSelectText = when (item.positionPercent) {
                0 -> "0% (Cerrada)"
                25 -> "25% Abierta"
                50 -> "50% Abierta"
                75 -> "75% Abierta"
                100 -> "100% Abierta"
                else -> null // Dejar que se use el default del spinner si no coincide
            }
            if (positionToSelectText != null) {
                val adapter = binding.spinnerPositionPercent.adapter as? ArrayAdapter<String>
                adapter?.getPosition(positionToSelectText)?.let { positionIndex ->
                    if (positionIndex >= 0) {
                        binding.spinnerPositionPercent.setSelection(positionIndex)
                    }
                }
            }

            // El checkbox "Todos los días" se marca si daysOfWeek no está vacío (contiene los 7 días)
            // Asumimos que si daysOfWeek no está vacío, es porque son los 7 días.
            // Si en el futuro permites días específicos, esta lógica necesitaría ser más detallada.
            binding.checkboxRepeatEveryDay.isChecked = item.daysOfWeek.isNotEmpty() // O item.daysOfWeek.size == 7

            // Cambiar título del diálogo o texto del botón si estamos editando (opcional)
            // Por ejemplo, podrías acceder al Dialog y cambiar su título:
            // dialog?.setTitle("Editar Horario")
            binding.buttonSaveSchedule.text = "Actualizar Horario" // Cambiar texto del botón
        }


        binding.buttonSaveSchedule.setOnClickListener {
            saveSchedule()
        }

        binding.buttonCancelSchedule.setOnClickListener {
            dismiss()
        }
    }

    private fun setupPositionSpinner() {
        val positionOptions = listOf(
            "0% (Cerrada)", "25% Abierta", "50% Abierta", "75% Abierta", "100% Abierta"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, positionOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPositionPercent.adapter = adapter
        // No establecer selección por defecto aquí si vamos a pre-rellenar en edición
        if (editingScheduleItem == null) { // Solo poner default si es nuevo
            binding.spinnerPositionPercent.setSelection(2) // 50% como default para nuevos
        }
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
            else -> 50
        }

        val daysOfWeek = mutableSetOf<Int>()
        if (binding.checkboxRepeatEveryDay.isChecked) {
            daysOfWeek.addAll(listOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            ))
        }
        // Si no está marcado, daysOfWeek queda vacío (para "Solo hoy")

        // Usar el ID existente si estamos editando, o generar uno nuevo si es un item nuevo.
        val itemId = editingScheduleItem?.id ?: System.currentTimeMillis()
        // Mantener el estado 'isEnabled' original si estamos editando, o true si es nuevo.
        // A menos que añadas un control en el diálogo para cambiar 'isEnabled'.
        val itemIsEnabled = editingScheduleItem?.isEnabled ?: true

        val scheduleToSave = ScheduleItem(
            id = itemId, // MUY IMPORTANTE: usar el ID original si se edita
            hour = hour,
            minute = minute,
            positionPercent = positionPercent,
            daysOfWeek = daysOfWeek,
            isEnabled = itemIsEnabled // Mantener el estado de habilitación original o default
        )

        listener?.onScheduleAdded(scheduleToSave) // Reutilizamos onScheduleAdded
        val toastMessage = if (editingScheduleItem != null) "Horario actualizado" else "Horario añadido"
        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddScheduleDialog"
        private const val ARG_SCHEDULE_ITEM = "arg_schedule_item_to_edit" // Key para el Bundle

        // Método factoría modificado para aceptar un ScheduleItem opcional para editar
        fun newInstance(scheduleItemToEdit: ScheduleItem? = null): AddScheduleDialogFragment {
            val fragment = AddScheduleDialogFragment()
            val args = Bundle()
            // Solo añadir el argumento si scheduleItemToEdit no es nulo
            scheduleItemToEdit?.let {
                args.putParcelable(ARG_SCHEDULE_ITEM, it)
            }
            fragment.arguments = args
            return fragment
        }
    }
}