package com.example.clientedivisas

import android.app.DatePickerDialog
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.example.clientedivisas.ui.theme.ClienteDivisasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClienteDivisasTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding), contentResolver = contentResolver)
                }
            }
        }
    }
}

// 🔹 Componente del Select (DropdownMenu) de Monedas
@Composable
fun CurrencySelector(contentResolver: ContentResolver, selectedCurrency: String, onCurrencySelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var availableCurrencies by remember { mutableStateOf(listOf<String>()) }

    // 🔹 Consultar el ContentProvider y obtener todas las monedas disponibles
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val uri = Uri.parse("content://com.example.proyecto_divisa.ContentProvider/exchangerate")
            val cursor = contentResolver.query(uri, arrayOf("nombre"), null, null, "nombre ASC")

            val currencies = mutableSetOf<String>() // Usamos un `Set` para evitar duplicados

            cursor?.use {
                while (it.moveToNext()) {
                    val nombre = it.getString(it.getColumnIndexOrThrow("nombre"))
                    currencies.add(nombre) // Agregamos la moneda a la lista
                }
            }

            // 🔹 Actualizamos la lista de monedas en el hilo principal
            withContext(Dispatchers.Main) {
                availableCurrencies = currencies.toList()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Selecciona una moneda", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))

        Box {
            Button(onClick = { expanded = true }) {
                Text(selectedCurrency.ifEmpty { "Seleccionar moneda" })
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availableCurrencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(text = currency) },
                        onClick = {
                            onCurrencySelected(currency)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// 🔹 Selector de Fechas
@Composable
fun DatePickerButton(label: String, selectedDate: String, onDateSelected: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = Calendar.getInstance()

    Button(onClick = {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }) {
        Text(text = if (selectedDate.isEmpty()) "Seleccionar $label" else selectedDate)
    }
}

@Composable
fun ExchangeRateChart(exchangeRates: List<Pair<String, Double>>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Histórico del Tipo de Cambio",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(8.dp)
        )

        AndroidView(factory = { context ->
            LineChart(context).apply {
                if (exchangeRates.isEmpty()) {
                    setNoDataText("No hay datos disponibles")
                    return@apply
                }

                val entries = exchangeRates.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.second.toFloat()) // Índice como X, tasa como Y
                }

                val dataSet = LineDataSet(entries, "Tipo de Cambio").apply {
                    color = android.graphics.Color.parseColor("#FFA500") // 🔹 Naranja para mejor visibilidad
                    valueTextColor = android.graphics.Color.BLACK
                    lineWidth = 3f
                    circleRadius = 5f
                    setDrawCircleHole(false)
                    setDrawValues(true) // 🔹 Mostrar valores en los puntos
                    setDrawFilled(true)
                    fillColor = android.graphics.Color.parseColor("#FFECB3") // 🔹 Amarillo claro para el área debajo de la línea
                    mode = LineDataSet.Mode.CUBIC_BEZIER // 🔹 Línea suavizada
                }

                data = LineData(dataSet)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textSize = 12f
                    granularity = 1f
                    setDrawGridLines(true) // 🔹 Agregar líneas de referencia en el fondo
                    setDrawAxisLine(true)
                    labelRotationAngle = -45f
                    valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return exchangeRates.getOrNull(value.toInt())?.first ?: ""
                        }
                    }
                }

                axisLeft.apply {
                    textSize = 12f
                    setDrawGridLines(true)
                    granularity = 0.1f
                    axisMinimum = (exchangeRates.minOfOrNull { it.second }?.minus(0.5f) ?: 0f).toFloat()
                    axisMaximum = (exchangeRates.maxOfOrNull { it.second }?.plus(0.5f) ?: 1f).toFloat()
                }

                axisRight.isEnabled = false

                description = Description().apply {
                    text = "Tendencia del tipo de cambio"
                    textSize = 12f
                }

                legend.apply {
                    textSize = 14f
                    formSize = 12f
                    isEnabled = true
                }

                setBackgroundColor(android.graphics.Color.WHITE)
                setNoDataText("No hay datos disponibles")

                invalidate() // 🔹 Forzar redibujado
            }
        }, update = { chart ->
            val entries = exchangeRates.mapIndexed { index, value ->
                Entry(index.toFloat(), value.second.toFloat())
            }

            val dataSet = LineDataSet(entries, "Tipo de Cambio").apply {
                color = android.graphics.Color.parseColor("#FFA500") // 🔹 Naranja brillante
                valueTextColor = android.graphics.Color.BLACK
                lineWidth = 3f
                circleRadius = 5f
                setDrawCircleHole(false)
                setDrawValues(true) // 🔹 Mostrar valores
                setDrawFilled(true)
                fillColor = android.graphics.Color.parseColor("#FFECB3") // 🔹 Relleno amarillo claro
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            chart.data = LineData(dataSet)
            chart.invalidate() // 🔹 Redibujar la gráfica con los nuevos datos
        },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(8.dp)
        )
    }
}




fun getExchangeRates(
    contentResolver: ContentResolver,
    currency: String,
    startDate: String,
    endDate: String
): List<Pair<String, Double>> {
    if (currency.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
        return emptyList() // Evita consultas si no hay datos seleccionados
    }

    val uri = Uri.parse("content://com.example.proyecto_divisa.ContentProvider/exchangerate/$currency/$startDate/$endDate")
    val cursor: Cursor? = contentResolver.query(uri, arrayOf("fecha", "cantidad"), null, null, "fecha ASC")

    val exchangeRates = mutableListOf<Pair<String, Double>>()

    cursor?.use {
        val dateIndex = it.getColumnIndexOrThrow("fecha")
        val rateIndex = it.getColumnIndexOrThrow("cantidad")

        while (it.moveToNext()) {
            val date = it.getString(dateIndex)
            val rate = it.getDouble(rateIndex)
            exchangeRates.add(Pair(date, rate))
        }
    }

    Log.d("Consulta", "Resultados obtenidos: $exchangeRates") // 🔹 Log para verificar datos
    return exchangeRates
}


@Composable
fun MainScreen(modifier: Modifier = Modifier, contentResolver: ContentResolver) {
    var selectedCurrency by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var exchangeRates = remember { mutableStateListOf<Pair<String, Double>>() } // 🔹 Mejor forma de manejar listas dinámicas

    Column(modifier = modifier.padding(16.dp)) {
        CurrencySelector(contentResolver, selectedCurrency) { selectedCurrency = it }

        Spacer(modifier = Modifier.height(16.dp))

        DatePickerButton(label = "Inicio", selectedDate = startDate) { startDate = it }

        Spacer(modifier = Modifier.height(16.dp))

        DatePickerButton(label = "Fin", selectedDate = endDate) { endDate = it }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val result = getExchangeRates(contentResolver, selectedCurrency, startDate, endDate)

            // 🔹 En lugar de reasignar, limpiamos y agregamos para que Compose detecte el cambio
            exchangeRates.clear()
            exchangeRates.addAll(result)
        }) {
            Text("Cargar Resultados")
        }

        Spacer(modifier = Modifier.height(16.dp))

        ExchangeRateChart(exchangeRates) // 🔹 Se pasa el estado actualizado
    }
}

@Composable
fun StyledButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable (() -> Unit)? = null // 🔹 Icono opcional en el botón
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary, // 🔹 Color principal
            contentColor = MaterialTheme.colorScheme.onPrimary // 🔹 Color del texto
        ),
        shape = MaterialTheme.shapes.medium, // 🔹 Bordes redondeados
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(50.dp) // 🔹 Altura más grande para mejor visibilidad
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
