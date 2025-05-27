package com.example.ishade // Asegúrate que el paquete sea el correcto

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ishade.databinding.ItemScheduleLayoutBinding // Generado por ViewBinding para item_schedule_layout.xml
import java.util.Locale

class ScheduleAdapter(
    private var schedules: List<ScheduleItem>,
    private val onScheduleToggle: (ScheduleItem, Boolean) -> Unit, // Lambda para cuando se cambia el switch
    private val onEditClick: (ScheduleItem) -> Unit,             // Lambda para clic en editar
    private val onDeleteClick: (ScheduleItem) -> Unit            // Lambda para clic en eliminar
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    // ViewHolder: Contiene las referencias a las vistas de item_schedule_layout.xml
    inner class ScheduleViewHolder(val binding: ItemScheduleLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(scheduleItem: ScheduleItem) {
            binding.textviewScheduleTime.text = scheduleItem.getTimeString()
            binding.textviewSchedulePosition.text = "${scheduleItem.positionPercent}% (${ScheduleItem.formatSelectedDays(scheduleItem.daysOfWeek, Locale.getDefault())})"
            binding.switchScheduleEnabled.isChecked = scheduleItem.isEnabled

            // Listener para el switch
            binding.switchScheduleEnabled.setOnCheckedChangeListener { _, isChecked ->
                // Evitar que el listener se dispare solo por el bind inicial
                // Solo actuar si el estado realmente ha cambiado por acción del usuario
                if (binding.switchScheduleEnabled.isPressed) { // O una comprobación más robusta si es necesario
                    onScheduleToggle(scheduleItem, isChecked)
                }
            }

            // Listener para el botón de editar
            binding.imageviewEditSchedule.setOnClickListener {
                onEditClick(scheduleItem)
            }

            // Listener para el botón de eliminar
            binding.imageviewDeleteSchedule.setOnClickListener {
                onDeleteClick(scheduleItem)
            }
        }
    }

    // Crea nuevas vistas (invocado por el layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemScheduleLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduleViewHolder(binding)
    }

    // Reemplaza el contenido de una vista (invocado por el layout manager)
    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val currentSchedule = schedules[position]
        holder.bind(currentSchedule)
    }

    // Devuelve el tamaño de tu conjunto de datos (invocado por el layout manager)
    override fun getItemCount() = schedules.size

    // Función para actualizar la lista de horarios en el adaptador
    fun updateSchedules(newSchedules: List<ScheduleItem>) {
        this.schedules = newSchedules
        notifyDataSetChanged() // Notifica al RecyclerView que los datos han cambiado
        // Para animaciones y mejor rendimiento, se usaría DiffUtil aquí, pero esto es más simple para empezar.
    }
}