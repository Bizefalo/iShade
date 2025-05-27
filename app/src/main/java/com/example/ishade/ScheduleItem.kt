package com.example.ishade

import android.util.Log // Necesario para los logs en las funciones de utilidad
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ScheduleItem(
    val id: Long = System.currentTimeMillis(), // ID único, usamos timestamp por simplicidad ahora
    val hour: Int,                             // Hora (0-23)
    val minute: Int,                           // Minuto (0-59)
    val positionPercent: Int,                  // Posición de la cortina (0, 25, 50, 75, 100)
    val daysOfWeek: Set<Int>,                  // Conjunto de enteros para los días (ej. Calendar.MONDAY, Calendar.TUESDAY)
    var isEnabled: Boolean = true              // Si el horario está activo o no, por defecto true
) {
    // Función para obtener la hora formateada (la dejamos aquí ya que es específica de la instancia)
    fun getTimeString(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    // Ya no necesitamos getDaysString() aquí si la movimos al companion object
    // fun getDaysString(): String { ... } // Puedes eliminarla o dejarla si la usas en otro lado

    companion object {
        private const val UTIL_TAG = "ScheduleItemUtil" // Tag para los logs de estas utilidades

        /**
         * Calcula el próximo tiempo de activación en milisegundos.
         * @param hour Hora del día (0-23).
         * @param minute Minuto de la hora (0-59).
         * @param selectedDaysOfWeek Un Set de enteros representando los días de la semana seleccionados
         * (usando constantes de Calendar como Calendar.MONDAY, Calendar.TUESDAY, etc.).
         * @param baseCalendar El Calendar a partir del cual se calcula "ahora". Útil para que el Receiver
         * especifique el momento exacto en que se disparó la alarma anterior.
         * Por defecto, usa la hora actual.
         * @return El tiempo en milisegundos para la próxima activación, o null si no se puede calcular.
         */
        fun calculateNextTriggerTimeInMillis(
            hour: Int,
            minute: Int,
            selectedDaysOfWeek: Set<Int>,
            baseCalendar: Calendar = Calendar.getInstance() // Permite pasar una referencia de "ahora"
        ): Long? {
            if (selectedDaysOfWeek.isEmpty()) {
                Log.w(UTIL_TAG, "calculateNextTriggerTimeInMillis: No hay días seleccionados.")
                return null
            }

            val now = baseCalendar.clone() as Calendar // Usamos un clon para no modificar el original

            // Iteramos para encontrar el próximo día válido, comenzando desde la fecha de baseCalendar
            // hasta 7 días en el futuro para asegurar que cubrimos toda la semana.
            for (dayOffset in 0..7) {
                val potentialTriggerDay = baseCalendar.clone() as Calendar // Clonamos para cada iteración de día
                potentialTriggerDay.add(Calendar.DAY_OF_YEAR, dayOffset)
                potentialTriggerDay.set(Calendar.HOUR_OF_DAY, hour)
                potentialTriggerDay.set(Calendar.MINUTE, minute)
                potentialTriggerDay.set(Calendar.SECOND, 0)
                potentialTriggerDay.set(Calendar.MILLISECOND, 0)

                val dayOfWeekInCalendar = potentialTriggerDay.get(Calendar.DAY_OF_WEEK)

                if (selectedDaysOfWeek.contains(dayOfWeekInCalendar)) {
                    // Si el día de la semana está en los seleccionados Y la fecha/hora es posterior a "ahora"
                    if (potentialTriggerDay.after(now)) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
                        sdf.timeZone = potentialTriggerDay.timeZone // Asegura consistencia de zona horaria en el log
                        Log.d(UTIL_TAG, "Próxima activación calculada para: ${sdf.format(potentialTriggerDay.time)}")
                        return potentialTriggerDay.timeInMillis
                    }
                }
            }
            // Si no se encuentra una fecha futura (lo cual es raro si hay días seleccionados), loguear un error.
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
            sdf.timeZone = now.timeZone
            Log.e(UTIL_TAG, "No se pudo calcular la próxima activación futura. Ahora: ${sdf.format(now.time)}, Hora: $hour, Min: $minute, Días: $selectedDaysOfWeek")
            return null
        }

        /**
         * Formatea el conjunto de días de la semana en una cadena legible.
         * @param daysOfWeek El conjunto de días (constantes de Calendar).
         * @param locale El Locale a usar para los nombres de los días (para localización).
         * @return Una cadena representando los días seleccionados.
         */
        fun formatSelectedDays(daysOfWeek: Set<Int>, locale: Locale = Locale.getDefault()): String {
            if (daysOfWeek.isEmpty()) return "Nunca" // Considera usar recursos de strings para localización
            if (daysOfWeek.size == 7) return "Todos los días"

            val calendar = Calendar.getInstance(locale)
            val dayNamesMap = mutableMapOf<Int, String>()

            // Llenar el mapa con los nombres cortos de los días según el locale
            listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
                .forEach { dayConstant ->
                    calendar.set(Calendar.DAY_OF_WEEK, dayConstant)
                    dayNamesMap[dayConstant] = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale) ?: ""
                }

            // Ordenar los días como vienen en el Set (el orden de Set no está garantizado, pero
            // para visualización podemos ordenarlos por su valor numérico de Calendar)
            val sortedDays = daysOfWeek.toList().sorted()

            return sortedDays.mapNotNull { dayNamesMap[it] }.joinToString(", ")
        }
    }
}