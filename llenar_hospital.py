import os
import pandas as pd
import random
from faker import Faker
from sqlalchemy import create_engine
from datetime import timedelta, date, datetime
from urllib.parse import quote_plus

# --- 1. CONFIGURACIÓN INICIAL ---
fake = Faker('es_CO')

# Las credenciales se leen del entorno o del .env cargado por el .bat.
usuario_bd = os.environ.get('ANALITICA_DB_USERNAME')
clave_bd = os.environ.get('ANALITICA_DB_PASSWORD')
if not usuario_bd or not clave_bd:
    raise SystemExit(
        'Configura ANALITICA_DB_USERNAME y ANALITICA_DB_PASSWORD '
        'en el entorno o en el archivo .env antes de ejecutar este script.'
    )

host_bd = os.environ.get('ANALITICA_DB_HOST', 'localhost')
puerto_bd = os.environ.get('ANALITICA_DB_PORT', '3306')
nombre_bd = os.environ.get('ANALITICA_DB_NAME', 'urgencias_hospital')

url_bd = (
    f'mysql+pymysql://{quote_plus(usuario_bd)}:{quote_plus(clave_bd)}'
    f'@{host_bd}:{puerto_bd}/{nombre_bd}'
)
engine = create_engine(url_bd)

print("Generando datos sintéticos con aleatoriedad ponderada...")

# --- 2. GENERAR CONTEXTO DE ZONA ---
localidades = ['Norte', 'Sur', 'Centro', 'Occidente', 'Oriente']
datos_zonas = []
for loc in localidades:
    datos_zonas.append({
        'localidad': loc,
        'poblacion': random.randint(100000, 800000),
        'porcentaje_mayores_60': round(random.uniform(8.0, 22.0), 2),
        'densidad_poblacional': round(random.uniform(3000.0, 20000.0), 2)
    })
df_zonas = pd.DataFrame(datos_zonas)

# --- 3. GENERAR CONTEXTO DIARIO (Último año) ---
fecha_inicio = date.today() - timedelta(days=365)
fechas = [fecha_inicio + timedelta(days=i) for i in range(365)]

datos_diarios = []
for f in fechas:
    # Simular estacionalidad básica en lluvia y temperatura
    lluvia = round(random.expovariate(1/10), 2) if random.random() < 0.4 else 0.0
    datos_diarios.append({
        'fecha': f,
        'es_festivo': random.random() < 0.05,
        'lluvia_mm': lluvia,
        'temperatura': round(random.gauss(18.0, 4.0), 2), # Distribución normal (media 18, desv 4)
        'pm25': round(random.gauss(40.0, 15.0), 2),
        'evento_masivo': random.random() < 0.03
    })
df_diarios = pd.DataFrame(datos_diarios)

# --- 4. GENERAR CAPACIDAD DEL TURNO ---
turnos_nombres = ['Mañana', 'Tarde', 'Noche']
datos_turnos = []
for f in fechas:
    for t in turnos_nombres:
        # En la noche suele haber menos personal
        base_personal = random.randint(10, 15) if t != 'Noche' else random.randint(5, 10)
        datos_turnos.append({
            'fecha': f,
            'turno': t,
            'personal_disponible': base_personal,
            'puestos_triage': random.randint(2, 4) if t != 'Noche' else random.randint(1, 2),
            'camas_disponibles': max(0, int(random.gauss(15, 8))) # Evitar números negativos
        })
df_turnos = pd.DataFrame(datos_turnos)

# --- 5. GENERAR 50,000 LLEGADAS ---
print("Fabricando 50,000 registros de llegadas (esto puede tomar unos segundos)...")
datos_llegadas = []

# Pesos para la hora de llegada (menos en la madrugada, pico en la tarde)
horas = list(range(24))
pesos_horas = [2, 1, 1, 1, 1, 2, 4, 6, 8, 9, 10, 10, 9, 8, 8, 9, 10, 11, 12, 10, 8, 6, 5, 3]

# Pesos para prioridades (Triage 3 y 4 son los más comunes)
prioridades_lista = ['Triage 1 (Rojo)', 'Triage 2 (Naranja)', 'Triage 3 (Amarillo)', 'Triage 4 (Verde)', 'Triage 5 (Azul)']
pesos_prioridad = [2, 10, 40, 35, 13]

for _ in range(50000):
    dia_random = random.choice(fechas)
    hora_random = random.choices(horas, weights=pesos_horas, k=1)[0]
    minuto_random = random.randint(0, 59)
    fecha_hora_random = datetime.combine(dia_random, datetime.min.time()) + timedelta(hours=hora_random, minutes=minuto_random)
    
    if 6 <= hora_random < 14:
        turno = 'Mañana'
    elif 14 <= hora_random < 22:
        turno = 'Tarde'
    else:
        turno = 'Noche'
        
    prioridad = random.choices(prioridades_lista, weights=pesos_prioridad, k=1)[0]
    
    # Asignar tiempos lógicos basados en la prioridad
    if prioridad == 'Triage 1 (Rojo)':
        espera = random.randint(0, 5)
        atencion = random.randint(45, 300)
        estado = 'Atendido' if random.random() < 0.8 else 'Derivado'
    elif prioridad == 'Triage 2 (Naranja)':
        espera = random.randint(0, 20)
        atencion = random.randint(30, 180)
        estado = 'Atendido' if random.random() < 0.9 else 'Derivado'
    elif prioridad == 'Triage 3 (Amarillo)':
        espera = random.randint(15, 120)
        atencion = random.randint(20, 90)
        estado = 'Atendido'
    elif prioridad == 'Triage 4 (Verde)':
        espera = random.randint(30, 240)
        atencion = random.randint(10, 40)
        estado = random.choices(['Atendido', 'Abandono'], weights=[85, 15], k=1)[0]
    else: # Triage 5 (Azul)
        espera = random.randint(60, 360)
        atencion = random.randint(5, 20)
        estado = random.choices(['Atendido', 'Abandono'], weights=[70, 30], k=1)[0]

    # Si abandonan, el tiempo de atención es 0
    if estado == 'Abandono':
        atencion = 0

    datos_llegadas.append({
        'fecha_hora': fecha_hora_random,
        'fecha': dia_random,
        'turno': turno,
        'localidad': random.choice(localidades),
        'prioridad': prioridad,
        'estado': estado,
        'tiempo_espera_min': espera,
        'tiempo_atencion_min': atencion
    })

df_llegadas = pd.DataFrame(datos_llegadas)

# --- 6. INSERCIÓN EN LA BASE DE DATOS ---
print("Conectado a MySQL. Iniciando la ingesta de datos...")

df_zonas.to_sql('contexto_zona', con=engine, if_exists='append', index=False)
df_diarios.to_sql('contexto_diario', con=engine, if_exists='append', index=False)
df_turnos.to_sql('capacidad_turno', con=engine, if_exists='append', index=False)
df_llegadas.to_sql('llegadas', con=engine, if_exists='append', index=False, chunksize=10000)

print("¡50,000 Llegadas con aleatoriedad estadística insertadas con éxito!")