package com.example.ishade // Asegúrate que el paquete sea el correcto

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast // Importar Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.ishade.databinding.ItemScheduleLayoutBinding // Generado por ViewBinding para item_schedule_layout.xml
import java.util.Locale

class ScheduleAdapter(
    private var schedules: List<ScheduleItem>,
    private val onScheduleToggle: (ScheduleItem, Boolean) -> Unit, // Lambda para cuando se cambia el switch
    private val onEditClick: (ScheduleItem) -> Unit,             // Lambda para clic en editar
    private val onDeleteClick: (ScheduleItem) -> Unit            // Lambda para clic en eliminar
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    // NUEVO: Flag para controlar si las interacciones en los items están habilitadas
    private var masterInteractionsEnabled: Boolean = true

    /**
     * Nueva función para ser llamada desde ScheduleFragment para habilitar/deshabilitar
     * las interacciones en todos los items del RecyclerView.
     */
    fun setInteractionsEnabled(isEnabled: Boolean) {
        if (masterInteractionsEnabled == isEnabled) return // Sin cambios
        masterInteractionsEnabled = isEnabled
        notifyDataSetChanged() // Notifica para que los items se redibujen con el nuevo estado de habilitación.
        // Para optimización, podrías solo notificar los items visibles o usar payload.
    }

    inner class ScheduleViewHolder(val binding: ItemScheduleLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(scheduleItem: ScheduleItem) {
            binding.textviewScheduleTime.text = scheduleItem.getTimeString()
            binding.textviewSchedulePosition.text = "${scheduleItem.positionPercent}% (${ScheduleItem.formatSelectedDays(scheduleItem.daysOfWeek, Locale.getDefault())})"
            binding.switchScheduleEnabled.isChecked = scheduleItem.isEnabled

            // Habilitar/deshabilitar controles del item basados en el master switch
            binding.switchScheduleEnabled.isEnabled = masterInteractionsEnabled
            binding.imageviewEditSchedule.isEnabled = masterInteractionsEnabled
            binding.imageviewDeleteSchedule.isEnabled = masterInteractionsEnabled // O podrías querer que borrar esté siempre activo

            // Listener para el switch individual
            binding.switchScheduleEnabled.setOnCheckedChangeListener { compoundButton, isChecked ->
                // Solo actuar si el cambio es del usuario (isPressed)
                if (compoundButton.isPressed) {
                    if (masterInteractionsEnabled) {
                        // Si las interacciones maestras están habilitadas, proceder normalmente
                        onScheduleToggle(scheduleItem, isChecked)
                    } else {
                        // Si las interacciones maestras están deshabilitadas, revertir el toggle y mostrar mensaje
                        compoundButton.isChecked = !isChecked // Revertir el cambio visual
                        Toast.makeText(compoundButton.context, "Active el interruptor general de horarios para modificar.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Listener para el botón de editar
            binding.imageviewEditSchedule.setOnClickListener {
                if (masterInteractionsEnabled) {
                    onEditClick(scheduleItem)
                } else {
                    Toast.makeText(it.context, "Active el interruptor general de horarios para editar.", Toast.LENGTH_SHORT).show()
                }
            }

            // Listener para el botón de eliminar
            binding.imageviewDeleteSchedule.setOnClickListener {
                // Podrías decidir si borrar está siempre permitido o también depende del master switch.
                // Por ahora, lo haremos dependiente también.
                if (masterInteractionsEnabled) {
                    onDeleteClick(scheduleItem)
                } else {
                    // Si decides que borrar siempre está permitido:
                    // onDeleteClick(scheduleItem)
                    // Si no:
                    Toast.makeText(it.context, "Active el interruptor general de horarios para eliminar.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemScheduleLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val currentSchedule = schedules[position]
        holder.bind(currentSchedule)
    }

    override fun getItemCount() = schedules.size

    fun updateSchedules(newSchedules: List<ScheduleItem>) {
        this.schedules = newSchedules
        notifyDataSetChanged()
    }
}