# Código de ESP32
```
#include <WiFi.h>
#include <ArduinoMqttClient.h>

// WiFi
const char* ssid = "ssid";
const char* password = "contraseña"; // Reemplaza con tu contraseña real

WiFiClient wifiClient;
MqttClient mqttClient(wifiClient);

// MQTT
const char broker[] = "192.168.0.8"; // IP de tu PC donde corre Mosquitto
int port = 1883;

// Tópicos
const char topic_luz[] = "sensor/luz";
const char topic_control[] = "cortina/control";

// Sensor
const int pinFotoresistor = 34;

// UART para Arduino (Serial2: TX2=GPIO17, RX2=GPIO16 por defecto)
// Solo usaremos TX2 para enviar al RX del Arduino.
// El Arduino deberá estar configurado para recibir a esta velocidad de baudios.
const long ARDUINO_SERIAL_BAUD = 9600;

// Tiempo de envío de datos del sensor
const long intervalo = 1000; // 1 segundo
unsigned long tiempoAnterior = 0;

// Variables para el modo automático
bool modoAutomaticoActivo = false;
int posicionCortinaConocidaPorcentaje = 0; // Asumimos que empieza cerrada (0%)

void setup() {
  Serial.begin(115200); // Para depuración con el Monitor Serie
  while (!Serial) {
    delay(10);
  }
  Serial.println("\n[SETUP] Iniciando ESP32...");

  // Inicializar Serial2 para comunicación con Arduino
  // Usaremos los pines por defecto de Serial2 (TX2: GPIO17, RX2: GPIO16)
  // Solo necesitamos TX2 para enviar, pero inicializamos ambos por completitud.
  Serial2.begin(ARDUINO_SERIAL_BAUD);
  Serial.print("[SETUP] Serial2 (para Arduino) inicializado a ");
  Serial.print(ARDUINO_SERIAL_BAUD);
  Serial.println(" baudios.");

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
  
  String mensaje = "";
  while (mqttClient.available()) {
    char c = (char)mqttClient.read();
    mensaje += c;
  }
  Serial.print("[MQTT] Payload: ");
  Serial.println(mensaje);

  // Comandos para Arduino (con \n para delimitador)
  String cmdSubir = "U\n";
  String cmdBajar = "D\n";
  String cmdDetener = "S\n";

  if (mensaje == "SUBIR") {
    Serial.print("[MODO] Comando manual 'SUBIR' recibido. Desactivando modo automático.");
    modoAutomaticoActivo = false;
    Serial.println("[ACCION] Subiendo cortina...");
    Serial2.print(cmdSubir);
    Serial.println("[ESP32->ARDUINO] Enviado: U");
  } else if (mensaje == "BAJAR") {
    Serial.print("[MODO] Comando manual 'BAJAR' recibido. Desactivando modo automático.");
    modoAutomaticoActivo = false;
    Serial.println("[ACCION] Bajando cortina...");
    Serial2.print(cmdBajar);
    Serial.println("[ESP32->ARDUINO] Enviado: D");
  } else if (mensaje == "DETENER") {
    Serial.print("[MODO] Comando manual 'DETENER' recibido. Desactivando modo automático.");
    modoAutomaticoActivo = false; 
    Serial.println("[ACCION] Deteniendo...");
    Serial2.print(cmdDetener);
    Serial.println("[ESP32->ARDUINO] Enviado: S");
  } else if (mensaje == "AUTO") {
    Serial.println("[MODO] MODO AUTOMÁTICO ACTIVADO desde app.");
    modoAutomaticoActivo = true;
  } else if (mensaje == "AUTO_OFF") {
    Serial.println("[MODO] MODO AUTOMÁTICO DESACTIVADO desde app.");
    modoAutomaticoActivo = false;
  } else if (mensaje == "MANUAL") { 
    Serial.println("[MODO] Modo manual explícito seleccionado (automático desactivado).");
    modoAutomaticoActivo = false;
  } else if (mensaje.startsWith("SETPOS_")) {
    Serial.println("[MODO HORARIO] Comando de posición SETPOS_X recibido.");
    modoAutomaticoActivo = false; 

    String porcentajeStr = mensaje.substring(7); 
    int porcentajeDeseado = porcentajeStr.toInt();

    Serial.print("[MODO HORARIO] Mover cortina a la posición: ");
    Serial.print(porcentajeDeseado);
    Serial.println("%.");

    String comandoParaArduino = "P" + String(porcentajeDeseado) + "\n";
    Serial2.print(comandoParaArduino);

    String logCmd = comandoParaArduino; 
    logCmd.trim();                      
    Serial.print("[ESP32->ARDUINO] Enviado: "); Serial.println(logCmd);
    
    posicionCortinaConocidaPorcentaje = porcentajeDeseado;
  } else {
    Serial.println("[MQTT] Comando desconocido recibido.");
  }
  Serial.println("[MQTT] ----------------------------");
}

void controlarCortinaAutomaticamente(int valorLuz) {
  if (!modoAutomaticoActivo) {
    return; 
  }

  int nuevaPosicionDeseadaPorcentaje = posicionCortinaConocidaPorcentaje;
  const int H_OFFSET = 30; 

  int zonaLuzPorcentaje = 0;
  if (valorLuz > 2000) zonaLuzPorcentaje = 100;
  else if (valorLuz >= 1300) zonaLuzPorcentaje = 75;
  else if (valorLuz >= 650) zonaLuzPorcentaje = 50;
  else if (valorLuz >= 201) zonaLuzPorcentaje = 25;
  else zonaLuzPorcentaje = 0;

  if (zonaLuzPorcentaje > posicionCortinaConocidaPorcentaje) {
    if (zonaLuzPorcentaje == 25 && valorLuz > (200 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 25;
    else if (zonaLuzPorcentaje == 50 && valorLuz > (650 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 50;
    else if (zonaLuzPorcentaje == 75 && valorLuz > (1300 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 75;
    else if (zonaLuzPorcentaje == 100 && valorLuz > (2000 + H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 100;
  } else if (zonaLuzPorcentaje < posicionCortinaConocidaPorcentaje) {
    if (zonaLuzPorcentaje == 75 && valorLuz < (2000 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 75;
    else if (zonaLuzPorcentaje == 50 && valorLuz < (1300 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 50;
    else if (zonaLuzPorcentaje == 25 && valorLuz < (650 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 25;
    else if (zonaLuzPorcentaje == 0 && valorLuz < (201 - H_OFFSET)) nuevaPosicionDeseadaPorcentaje = 0;
  }

  if (nuevaPosicionDeseadaPorcentaje != posicionCortinaConocidaPorcentaje) {
    Serial.print("[MODO AUTO] Luz: ");
    Serial.print(valorLuz);
    Serial.print(" -> Mover cortina de ");
    Serial.print(posicionCortinaConocidaPorcentaje);
    Serial.print("% a ");
    Serial.print(nuevaPosicionDeseadaPorcentaje);
    Serial.println("%.");

    String comandoParaArduino = "P" + String(nuevaPosicionDeseadaPorcentaje) + "\n";
    Serial2.print(comandoParaArduino);

    String logCmdAuto = comandoParaArduino; 
    logCmdAuto.trim();                      
    Serial.print("[ESP32->ARDUINO] Modo Auto - Enviado: "); Serial.println(logCmdAuto);

    posicionCortinaConocidaPorcentaje = nuevaPosicionDeseadaPorcentaje;
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
    tiempoAnterior = tiempoActual;

    int valorLuz = analogRead(pinFotoresistor);

    mqttClient.beginMessage(topic_luz);
    mqttClient.print(valorLuz);
    mqttClient.endMessage();

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
