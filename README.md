# Código de ESP32
```
#include <WiFi.h>
#include <ArduinoMqttClient.h>

// WiFi
const char* ssid = "VTR-0486191";
const char* password = "ns$o2{gn2AL\"#8t=9z";

WiFiClient wifiClient;
MqttClient mqttClient(wifiClient);

// MQTT
const char broker[] = "192.168.0.8";
int port = 1883;

// Tópicos
const char topic_luz[] = "sensor/luz";
const char topic_control[] = "cortina/control";

// Sensor
const int pinFotoresistor = 34;

// Tiempo de envío de datos del sensor (y revisión de modo automático)
const long intervalo = 1000; // 1 segundo
unsigned long tiempoAnterior = 0;

// Variables para el modo automático
bool modoAutomaticoActivo = false;
// 'posicionCortinaConocidaPorcentaje' representa la última posición a la que se ordenó moverse.
// Inicializar a un valor que no sea un porcentaje válido (0-100) para forzar la primera acción.
// O, si tienes una forma de saber la posición al inicio (ej. siempre empieza cerrada), úsala.
int posicionCortinaConocidaPorcentaje = 0; // Asumamos que empieza cerrada (0%)

void setup() {
  Serial.begin(115200);
  while (!Serial) {
    delay(10);
  }
  Serial.println("\n[SETUP] Iniciando ESP32...");

  WiFi.begin(ssid, password);
  Serial.print("[SETUP] Conectando a WiFi ");
  Serial.print(ssid);

  int wifi_retries = 0;
  while (WiFi.status() != WL_CONNECTED && wifi_retries < 30) {
    delay(500);
    Serial.print(".");
    wifi_retries++;
  }

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\n[SETUP] ¡Fallo al conectar a WiFi! Reiniciando en 10 segundos...");
    delay(10000);
    ESP.restart();
  }

  Serial.println("\n[SETUP] Conectado a WiFi.");
  Serial.print("[SETUP] IP local: ");
  Serial.println(WiFi.localIP());

  Serial.print("[SETUP] Conectando a broker MQTT: ");
  Serial.print(broker);
  Serial.print(":");
  Serial.println(port);

  if (!mqttClient.connect(broker, port)) {
    Serial.print("[SETUP] ¡Fallo al conectar al broker! Código de error = ");
    Serial.println(mqttClient.connectError());
    Serial.println("[SETUP] Reiniciando en 10 segundos...");
    delay(10000);
    ESP.restart();
  }

  Serial.println("[SETUP] Conectado a broker MQTT.");
  mqttClient.onMessage(messageReceived);

  Serial.print("[SETUP] Suscribiéndose al topic: ");
  Serial.println(topic_control);
  if (!mqttClient.subscribe(topic_control)) {
    Serial.println("[SETUP] Error al suscribirse al topic de control.");
  } else {
    Serial.println("[SETUP] Suscripción exitosa a ");
    Serial.println(topic_control);
  }
  Serial.println("[SETUP] Configuración completada.");
}

void messageReceived(int messageSize) {
  Serial.println("\n[MQTT] ----- Mensaje Recibido -----");
  Serial.print("[MQTT] Topic: ");
  Serial.print(mqttClient.messageTopic());
  // ... (resto de la función messageReceived como la tenías, asegurándote que actualiza modoAutomaticoActivo) ...
  String mensaje = "";
  while (mqttClient.available()) {
    char c = (char)mqttClient.read();
    mensaje += c;
  }
  Serial.print("[MQTT] Payload: ");
  Serial.println(mensaje);

  if (mensaje == "SUBIR" || mensaje == "BAJAR" || mensaje == "DETENER") {
    Serial.print("[MODO] Comando manual '");
    Serial.print(mensaje);
    Serial.println("' recibido. Desactivando modo automático.");
    modoAutomaticoActivo = false; 
    if (mensaje == "SUBIR") Serial.println("[ACCION] Subiendo cortina...");
    if (mensaje == "BAJAR") Serial.println("[ACCION] Bajando cortina...");
    if (mensaje == "DETENER") Serial.println("[ACCION] Deteniendo...");
  } else if (mensaje == "AUTO") {
    Serial.println("[MODO] MODO AUTOMÁTICO ACTIVADO desde app.");
    modoAutomaticoActivo = true;
    // Al activar modo auto, forzamos una evaluación inicial sin importar la última posición auto.
    // La función controlarCortinaAutomaticamente se encargará de decidir si hay que moverse.
  } else if (mensaje == "AUTO_OFF") {
    Serial.println("[MODO] MODO AUTOMÁTICO DESACTIVADO desde app.");
    modoAutomaticoActivo = false;
  } else if (mensaje == "MANUAL") { 
    Serial.println("[MODO] Modo manual explícito seleccionado (automático desactivado).");
    modoAutomaticoActivo = false;
  } else {
    Serial.println("[MQTT] Comando desconocido recibido.");
  }
  Serial.println("[MQTT] ----------------------------");
}

void controlarCortinaAutomaticamente(int valorLuz) {
  if (!modoAutomaticoActivo) {
    return; 
  }

  int nuevaPosicionDeseadaPorcentaje = posicionCortinaConocidaPorcentaje; // Asumir que no hay cambio inicialmente
  const int H_OFFSET = 30; // Umbral de histéresis, ajusta este valor según sea necesario

  // Determinar la "zona" de luz actual sin histéresis primero
  int zonaLuzPorcentaje = 0;
  if (valorLuz > 2000) zonaLuzPorcentaje = 100;
  else if (valorLuz >= 1300) zonaLuzPorcentaje = 75;
  else if (valorLuz >= 650) zonaLuzPorcentaje = 50;
  else if (valorLuz >= 201) zonaLuzPorcentaje = 25;
  else zonaLuzPorcentaje = 0; // valorLuz <= 200

  // Aplicar histéresis para decidir si cambiamos de la posición actual
  if (zonaLuzPorcentaje > posicionCortinaConocidaPorcentaje) {
    // Quiere abrir más. ¿Cruzó el umbral inferior de la nueva zona + histéresis?
    if (zonaLuzPorcentaje == 25 && valorLuz > (200 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 25;
    else if (zonaLuzPorcentaje == 50 && valorLuz > (650 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 50;
    else if (zonaLuzPorcentaje == 75 && valorLuz > (1300 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 75;
    else if (zonaLuzPorcentaje == 100 && valorLuz > (2000 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 100;
  } else if (zonaLuzPorcentaje < posicionCortinaConocidaPorcentaje) {
    // Quiere cerrar más. ¿Cruzó el umbral superior de la nueva zona - histéresis?
    if (zonaLuzPorcentaje == 75 && valorLuz < (2000 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 75;
    else if (zonaLuzPorcentaje == 50 && valorLuz < (1300 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 50;
    else if (zonaLuzPorcentaje == 25 && valorLuz < (650 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 25;
    else if (zonaLuzPorcentaje == 0 && valorLuz < (201 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 0;
  }
  // Si zonaLuzPorcentaje == posicionCortinaConocidaPorcentaje, no hacemos nada, ya estamos en la zona correcta.

  // Solo actuar si la posición realmente deseada es diferente de la conocida actual
  if (nuevaPosicionDeseadaPorcentaje != posicionCortinaConocidaPorcentaje) {
    Serial.print("[MODO AUTO] Luz: ");
    Serial.print(valorLuz);
    Serial.print(" -> Mover cortina de ");
    Serial.print(posicionCortinaConocidaPorcentaje); // La posición ANTES del movimiento
    Serial.print("% a ");
    Serial.print(nuevaPosicionDeseadaPorcentaje);
    Serial.println("%.");

    // AQUÍ IRÍA LA LÓGICA PARA COMANDAR AL ARDUINO UNO
    // Ejemplo: String comandoParaArduino = "MOVER_A_PORCENTAJE:" + String(nuevaPosicionDeseadaPorcentaje);
    // Serial.println(comandoParaArduino); // Simulación

    posicionCortinaConocidaPorcentaje = nuevaPosicionDeseadaPorcentaje; // Actualizar la posición conocida DESPUÉS de ordenar el movimiento
  }
}

void loop() {
  if (!mqttClient.connected()) {
      Serial.println("[LOOP] Conexión MQTT perdida. Intentando reconectar...");
      if (!mqttClient.connect(broker, port)) {
          Serial.print("[LOOP] ¡Fallo al reconectar al broker! Código: ");
          Serial.println(mqttClient.connectError());
          delay(5000);
          return;
      }
      Serial.println("[LOOP] Reconectado al broker MQTT.");
      if(!mqttClient.subscribe(topic_control)) {
        Serial.println("[LOOP] Error al re-suscribirse al topic de control.");
      } else {
        Serial.println("[LOOP] Re-suscripción exitosa a ");
        Serial.println(topic_control);
      }
  }
  
  mqttClient.poll();

  unsigned long tiempoActual = millis();
  if (tiempoActual - tiempoAnterior >= intervalo) {
    // Serial.println("\n[LOOP] Intervalo cumplido."); // Podemos comentar esto si hay mucho log
    tiempoAnterior = tiempoActual;

    int valorLuz = analogRead(pinFotoresistor);
    // Serial.print("[SENSOR] Valor de luz: "); // Comentado para reducir logs, pero puedes activarlo
    // Serial.println(valorLuz);

    // Publicar valor de luz
    mqttClient.beginMessage(topic_luz);
    mqttClient.print(valorLuz);
    mqttClient.endMessage(); // No es necesario imprimir éxito cada vez si funciona bien

    // Llama a la lógica del modo automático si está activo
    if (modoAutomaticoActivo) {
        controlarCortinaAutomaticamente(valorLuz);
    }
  }
}
```

# Script de Python

```
import psycopg2
import paho.mqtt.client as mqtt
from datetime import datetime


# Configuración de PostgreSQL
conn = psycopg2.connect(
    host="localhost",
    database="cortina",
    user="postgres",
    password="admin"
)
cur = conn.cursor()

# Función para insertar mediciones
def insertar_medicion(valor):
    cur.execute("INSERT INTO medicion_luz (valor_luz) VALUES (%s);", (valor,))
    conn.commit()
    print(f"[BD] Insertado valor de luz: {valor}")

# Callback cuando se conecta al broker
def on_connect(client, userdata, flags, rc):
    print("[MQTT] Conectado con código:", rc)
    client.subscribe("sensor/luz")

# Callback cuando llega un mensaje
def on_message(client, userdata, msg):
    payload = msg.payload.decode()
    print(f"[MQTT] Mensaje recibido en {msg.topic}: {payload}")
    
    # Intentamos extraer el número del mensaje
    try:
        # Si el ESP32 envía "hello 4", extraemos el "4"
        valor = int(payload)
        insertar_medicion(valor)
    except Exception as e:
        print(f"[ERROR] No se pudo insertar el valor: {e}")

# Configurar cliente MQTT
client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

# IP del broker (la misma que el ESP32 usa como broker)
client.connect("192.168.0.8", 1883)  # Reemplaza con tu IP real si es distinta

# Bucle infinito para escuchar mensajes
print("[MQTT] Esperando mensajes...")
client.loop_forever()
```
# Habilitar Mosquitto manualmente

```
cd C:\Program Files\mosquitto
mosquitto -c mosquitto.conf -v
```

# Conectar inalámbricamente celular a Android Studio (CMD)

## Requisitos
- Celular en modo desarrollador
- En opciones de desarrollador habilitar Depuración USB
- Dirigirse a Depuración inalámbrica
- Obtener código de sincronización ( 1 )

```
cd C:\Users\nombreUsuario\AppData\Local\Android\Sdk\platform-tools
adb pair direccionIP:Puerto ( 1 )
adb connect direccionIP:Puerto ( Dirección IP y Puerto del celular)
```
