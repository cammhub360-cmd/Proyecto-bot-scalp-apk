# 📈 Guía Maestra: Binance Multi-Pair Scalping Bot & Android Dashboard

Esta guía detalla la arquitectura, el funcionamiento y el despliegue del **Proyecto Base para un Bot de Scalping Multipar de Binance con App Controladora Nativa Android** (diseñada para Android Studio y Google Play Studio).

El ecosistema está compuesto por dos elementos principales que trabajan de forma desacoplada y asíncrona:
1. **Frontend Nativo Android:** Escrito en **Kotlin con Jetpack Compose** y Material Design 3. Cuenta con un dashboard de control de riesgo que monitorea el balance, las órdenes activas, los trailing stops y visualiza la consola de la terminal del bot en tiempo real.
2. **Backend Engine de Trading:** Escrito en **Python**, implementado con **FastAPI** para la API de control y **CCXT** para la interacción directa y filtrado de alta velocidad en el exchange de Binance.

---

## 🗂️ Estructura Completa del Proyecto

A continuación se resume la distribución de archivos de este espacio de trabajo:
* `/app/src/main/` : Directorio con el código fuente del aplicativo Android nativo actualizable en tiempo real.
* `/bot_android_app/backend-bot/` : Carpeta que contiene el núcleo del robot programado en Python con CCXT.
  * [`bot.py`](file:///bot_android_app/backend-bot/bot.py) : El algoritmo central de scalping con endpoints FastAPI y el simulador de stop-loss dinámico.
  * [`requirements.txt`](file:///bot_android_app/backend-bot/requirements.txt) : Lista de dependencias del framework trading.
* [`/bot_android_app/README_ES.md`](file:///bot_android_app/README_ES.md) : Este manual operacional.

> 💡 **Consejo de Descarga:** Puedes descargar todo el zip de este entorno listo para exportar a Android Studio/Google Play Studio presionando el botón de **"Download"** en la configuración de entorno (**Environment settings**) de la esquina de AI Studio.

---

## 🚀 1. Estructura y Funcionamiento de la Aplicación Android

La aplicación móvil nativa ha sido dotada de un diseño interactivo y fluido que sigue los lineamientos modernos de Material Design 3, especial para traders avanzados:

- **Panel (Dashboard):** Presenta un interruptor central asíncrono que arranca o detiene el bot de inmediato mediante llamadas `POST /start` y `POST /stop`. Lista los activos que se encuentran bajo monitoreo y renderiza barras de progreso que indican la distancia visual de cada trade en relación a su Take Profit.
- **Ajustes de Riesgo (Risk Settings):** Ofrece un panel táctil para configurar el monto exacto de inversión por operación ($2.0 USD por defecto), el ratio de Take Profit y el % del Trailing Stop que persigue los máximos de mercado. Permite ingresar de forma segura las **API Key** y **API Secret** de Binance de Spot.
- **Terminal Consola (Logs):** Incorpora un lector constante de logs que colorea de forma semántica las salidas para agilizar análisis visuales rápidos (Verde para toma de ganancias y compras, amarillo para movimientos de stop-loss, rojo para alertas).
- **Control de Endpoints:** Permite cambiar la URL de conexión en caliente (por ejemplo, usar la IP de localhost `http://10.0.2.2:8080/` para pruebas con emuladores, o la IP de red local de tu máquina para pruebas con celulares físicos por Wi-Fi).

---

## 🤖 2. Backend del Bot con CCXT y Filtro de Pares USDT/USDC

El motor del bot de scalping automatizado ([`bot.py`](file:///bot_android_app/backend-bot/bot.py)) se encarga de analizar permanentemente los mercados de spot de Binance:

- **Filtrado Exclusivo USDT/USDC:** Al arrancar, el bot llama recursivamente al listado de mercados spot de Binance, purgando tokens de apalancamiento (`UP`, `DOWN`, `BULL`, `BEAR`), pares con apodos y stablecoins cruzadas de poca liquidez. Se concentra estrictamente en los pares líquidos contra USDT y USDC (e.g. `BTC/USDT`, `ETH/USDT`, `SOL/USDT`, `BNB/USDT`, `XRP/USDT`, etc.).
- **Modo Simulado Integrado (Mock Mode):** Debido a que los sandboxes de desarrollo suelen poseer políticas de seguridad que bloquean la comunicación hacia el host de producción de Binance (`api.binance.com`), el bot implementa un **Modo de Simulación Financiera avanzado**. Este genera trayectorias probabilísticas para el precio simulando la fluctuación real del mercado cripto para demostrar visualmente la entrada de órdenes y el comportamiento dinámico de los trailing stops sin requerir llaves reales ni saldo.

---

## 🛡️ 3. Gestión de Riesgos: Take Profit (1%) y Trailing Stop-Loss (0.5%)

La disciplina emocional es nula en este sistema; cada orden incorpora de inmediato dos mecanismos lógicos de liquidación y salida:

1. **Take Profit Fijo al 1%:**
   - En cuanto una orden se ejecuta, el sistema calcula la salida ideal: $Precio_{Compra} \times 1.01$.
   - Al tocar dicho nivel de cotización, el bot envía una orden de venta de mercado asíncrona, bloqueando de inmediato los fondos con ganancias netas.

2. **Trailing Stop-Loss Dinámico de 0.5% (Trailing Stop):**
   - El stop-loss convencional es estático y bloquea pérdidas fijas. Nuestro **Trailing Stop** es dinámico y acompaña el movimiento favorable del activo.
   - Si se compra una posición a $Precio_{Entrada} = 70,000$, el stop de salida inicial se fija en $69,650$ (0.5% abajo).
   - Si el precio sube a $72,000$, el bot recalcula de forma asíncrona al vuelo el precio de stop a $71,640$ (0.5% abajo del precio pico más alto).
   - Si el activo comienza a caer brusca o paulatinamente a partir de allí y toca los $71,640$, el robot vende la posición para proteger el capital. Esto convierte una potencial pérdida en una ganancia asegurada del **+2.34%** neto de forma totalmente autónoma.

---

## 💰 4. Operaciones con Mínimos Óptimos ($2.0 USD)

Uno de los mayores dolores de cabeza de los bots de scalping básicos es la detención por errores de red al violar los tamaños mínimos de orden impuestos por Binance (que usualmente exige montos de al menos $5 o $10 USD por orden).

Este bot de scalping solventa esto de manera inteligente:
1. **Mapeo de Límites de Mercado:** Durante la fase de inicio de mercados (`load_markets`), consulta los campos `'limits'` provistos por CCXT para cada moneda:
   - `market['limits']['cost']['min']` : El costo total mínimo en divisa base.
   - `market['limits']['amount']['min']` : El tamaño mínimo de la moneda.
2. **Ajuste Automático Adaptive:** Si el usuario fija una orden mínima de **$2.0 USD** y el par específico requiere un valor un poco mayor (por ejemplo, $5.0 USD para cierto token de baja capitalización), el bot reajusta automáticamente al nivel mínimo superior requerido para evitar ser bloqueado por los servidores de control de Binance.
3. **Conversión a Precisión de Red:** Aplica la función estándar `amount_to_precision` provista por Binance para formatear correctamente los decimales y evitar rechazos por exceso de decimales en el servidor.

---

## 🛠️ Cómo Ejecutar el Entorno de Desarrollo y Despliegue

### Paso A: Levantar el Backend de Cripto-Scalping (Python)
Para ejecutar el bot desde tu terminal en la nube o en tu PC local:
```bash
# Entrar al directorio
cd bot_android_app/backend-bot

# Instalar dependencias requeridas
pip install -r requirements.txt

# Iniciar servidor asíncrono FastAPI
python bot.py
```
*Verás logs inmediatos de simulación y un servidor levantado en el puerto 8080.*

### Paso B: Importar la aplicación nativa en Google Play / Android Studio
1. Descarga el snapshot entero presionando **"Download"**.
2. Descomprime y abre la ruta `/bot_android_app/android-app/` (o el directorio raíz de Gradle de este espacio de trabajo) en tu **Android Studio**.
3. El proyecto sincronizará de forma automática las dependencias detalladas en el catálogo centralizado de Gradle.
4. Conecta tu celular por depuración USB o inicia un emulador. La app apuntará por defecto a `http://10.0.2.2:8080/` para enlazarse de inmediato con tu backend de pruebas.
