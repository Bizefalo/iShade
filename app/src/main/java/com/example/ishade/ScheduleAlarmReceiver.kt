package com.example.ishade // Asegúrate que el paquete sea el correcto

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
// import android.widget.Toast // Considera si los Toasts son necesarios aquí o si los logs son suficientes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleAlarmReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.e(TAG, "Contexto o Intent nulos, no se puede procesar la alarma.")
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        Log.d(TAG, "¡Alarma recibida! Hora actual del sistema: ${sdf.format(Calendar.getInstance().time)}")

        // Extraer datos del Intent
        val scheduleId = intent.getLongExtra("SCHEDULE_ID", -1L)
        val positionPercent = intent.getIntExtra("POSITION_PERCENT", -1)
        val timeString = intent.getStringExtra("TIME_STRING") ?: "Hora Desconocida"
        val daysOfWeekArray = intent.getIntArrayExtra("DAYS_OF_WEEK")

        Log.d(TAG, "Detalles de la alarma disparada - ID: $scheduleId, Posición: $positionPercent%, Hora Original: $timeString")
        if (daysOfWeekArray != null) {
            Log.d(TAG, "Días de la semana recibidos (array): ${daysOfWeekArray.joinToString()}")
        } else {
            Log.w(TAG, "El array daysOfWeekArray es nulo. No se podrá reprogramar.")
        }

        if (scheduleId != -1L && positionPercent != -1) {
            // PASO 1: Ejecutar la acción programada
            // --------------------------------------------------
            val mqttCommand = "SETPOS_$positionPercent" // Comando MQTT de ejemplo
            Log.i(TAG, "ACCIÓN PROGRAMADA: Mover cortina a $positionPercent%. (Comando MQTT simulado: $mqttCommand). ID del Horario: $scheduleId")

            // TODO: Aquí es donde implementarías el envío real del comando MQTT.
            // Si la operación de red es muy corta, podría hacerse directamente.
            // Para operaciones más largas o si la app puede no estar activa,
            // considera usar WorkManager o un JobIntentService.
            // Ejemplo de Toast (si es útil, pero los logs son más fiables para depuración de fondo):
            // Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Ejecutando Horario: $timeString - Mover a $positionPercent%", Toast.LENGTH_LONG).show() }


            // PASO 2: Reprogramar la alarma si es necesario
            // --------------------------------------------------
            if (daysOfWeekArray != null && daysOfWeekArray.isNotEmpty()) {
                val daysOfWeekSet = daysOfWeekArray.toSet() // Convertir el IntArray a Set<Int>
                Log.d(TAG, "Intentando reprogramar la alarma para los días: ${ScheduleItem.formatSelectedDays(daysOfWeekSet)}")
                rescheduleAlarm(context, scheduleId, positionPercent, timeString, daysOfWeekSet)
            } else {
                Log.d(TAG, "No hay días de repetición especificados (daysOfWeekArray es nulo o vacío). No se reprogramará la alarma con ID: $scheduleId.")
                // Opcional: Si esta alarma era de "una sola vez" (daysOfWeek vacío),
                // podrías querer eliminarla de SharedPreferences aquí para que no vuelva a aparecer
                // en la lista de la UI como un horario activo si ya se ejecutó.
            }

        } else {
            Log.e(TAG, "ID de horario ($scheduleId) o porcentaje de posición ($positionPercent) no válidos recibidos en la alarma.")
        }
    }

    private fun rescheduleAlarm(
        context: Context,
        scheduleId: Long,
        positionPercent: Int,
        originalTimeString: String, // Ejemplo: "10:30"
        daysOfWeek: Set<Int>        // El Set de constantes Calendar.DAY_OF_WEEK
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())

        // Parsear la hora y minuto del string original de la hora
        val timeParts = originalTimeString.split(":")
        if (timeParts.size != 2) {
            Log.e(TAG, "Formato de originalTimeString ('$originalTimeString') incorrecto. No se puede reprogramar el ID: $scheduleId.")
            return
        }
        val hour = timeParts[0].toIntOrNull()
        val minute = timeParts[1].toIntOrNull()

        if (hour == null || minute == null) {
            Log.e(TAG, "No se pudieron parsear hora/minuto de originalTimeString ('$originalTimeString'). No se puede reprogramar el ID: $scheduleId.")
            return
        }

        // Calcular el próximo tiempo de disparo usando la función de utilidad.
        // Es crucial pasar Calendar.getInstance() como baseCalendar. Esto asegura que
        // la función busque la *siguiente* ocurrencia estrictamente *después* del momento actual
        // (es decir, después de que esta alarma actual se haya disparado).
        val nextTriggerTimeMillis = ScheduleItem.calculateNextTriggerTimeInMillis(
            hour = hour,
            minute = minute,
            selectedDaysOfWeek = daysOfWeek,
            baseCalendar = Calendar.getInstance() // "Ahora" es el momento en que esta alarma se está procesando
        )

        if (nextTriggerTimeMillis != null) {
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = "com.example.ishade.SCHEDULE_ALARM.${scheduleId}" // Usar la misma acción
                putExtra("SCHEDULE_ID", scheduleId)
                putExtra("POSITION_PERCENT", positionPercent)
                putExtra("TIME_STRING", originalTimeString) // Mantener la hora original para referencia
                putExtra("DAYS_OF_WEEK", daysOfWeek.toIntArray()) // Pasar los días de nuevo para la siguiente reprogramación
            }

            val requestCode = scheduleId.toInt() // Usar el mismo requestCode
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Actualizar si existe
            )

            try {
                // Aunque el permiso se verificó al programar, una comprobación aquí no está de más,
                // aunque en un BroadcastReceiver no puedes pedir permisos interactivamente.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    Log.e(TAG, "PERMISO DENEGADO AL REPROGRAMAR: No se pueden programar alarmas exactas para el ID Horario: $scheduleId. La alarma NO será reprogramada.")
                    // Considera registrar este fallo persistentemente o notificar al usuario de otra forma.
                    return // No reprogramar si no hay permiso.
                }

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTimeMillis,
                    pendingIntent
                )
                val nextTriggerCalendar = Calendar.getInstance().apply { timeInMillis = nextTriggerTimeMillis }
                Log.i(TAG, "Alarma REPROGRAMADA para ID $scheduleId. Próxima activación: ${sdf.format(nextTriggerCalendar.time)} (Epoch: $nextTriggerTimeMillis)")

            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException al REPROGRAMAR la alarma para ID $scheduleId.", e)
            } catch (e: Exception) {
                Log.e(TAG, "Excepción general al REPROGRAMAR la alarma para ID $scheduleId.", e)
            }

        } else {
            Log.e(TAG, "No se pudo calcular la próxima activación para reprogramar la alarma ID: $scheduleId. Días de la semana: ${daysOfWeek.joinToString()}. Esto puede ocurrir si la hora ya pasó para todos los días seleccionados en el futuro cercano o hay un error de lógica.")
        }
    }
}