package com.example.ishade

import java.util.Calendar

data class ScheduleItem(
    val id: Long = System.currentTimeMillis(), // ID único, usamos timestamp por simplicidad ahora
    val hour: Int,                             // Hora (0-23)
    val minute: Int,                           // Minuto (0-59)
    val positionPercent: Int,                  // Posición de la cortina (0, 25, 50, 75, 100)
    val daysOfWeek: Set<Int>,                  // Conjunto de enteros para los días (ej. Calendar.MONDAY, Calendar.TUESDAY)
    var isEnabled: Boolean = true              // Si el horario está activo o no, por defecto true
) {
    // Función para obtener una representación textual de los días (opcional, pero útil para mostrar)
    fun getDaysString(): String {
        if (daysOfWeek.isEmpty()) return "Nunca"
        if (daysOfWeek.size == 7) return "Todos los días"

        // Ordenar los días para una visualización consistente
        val sortedDays = daysOfWeek.sorted()
        val dayNames = mapOf(
            Calendar.SUNDAY to "Dom",
            Calendar.MONDAY to "Lun",
            Calendar.TUESDAY to "Mar",
            Calendar.WEDNESDAY to "Mié",
            Calendar.THURSDAY to "Jue",
            Calendar.FRIDAY to "Vie",
            Calendar.SATURDAY to "Sáb"
        )
        return sortedDays.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    // Función para obtener la hora formateada (opcional)
    fun getTimeString(): String {
        return String.format("%02d:%02d", hour, minute)
    }
}