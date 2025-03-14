package com.example.proyecto_divisa

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

import com.example.proyecto_divisa.data.RetrofitClient
import com.example.proyecto_divisa.room.AppDatabase
import com.example.proyecto_divisa.room.ExchangeRate

import com.example.proyecto_divisa.ui.theme.Proyecto_DivisaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Opcional: Ejecutar solo cuando haya conexión a internet
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<MyHourlyTaskWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()


        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "uniqueWorkName",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
        setContent {
            Proyecto_DivisaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ExchangeRateScreen(modifier = Modifier.padding(innerPadding))
                }/*
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    ExchangeRateList(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                */
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Proyecto_DivisaTheme {
        Greeting("Android")
    }
}

@Composable
fun ExchangeRateList(modifier: Modifier = Modifier) {
    // Obtener instancia de la base de datos
    val db = AppDatabase.getInstance(LocalContext.current)

    // Observar los cambios en los datos
    val rates by db.exchangeRateDAO().getAllRates().collectAsState(emptyList())

    LazyColumn(modifier = modifier) {
        items(rates) { rate ->
            Text(
                text = "${rate.nombre}: ${"%.4f".format(rate.cantidad)} (Fecha: ${rate.fecha})", // Ahora incluye la fecha
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}


class MyHourlyTaskWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = "e313f23987b8bdb9633e0aed"
                val exchangeRateService = RetrofitClient.instance
                val exchangeRateResponse = exchangeRateService.getLatestRates(apiKey, "MXN")

                if (exchangeRateResponse.result == "success") {
                    val db = AppDatabase.getInstance(applicationContext)

                    // Obtener el timestamp de la API y convertirlo a una fecha
                    val rawTimestamp = exchangeRateResponse.timeLastUpdateUtc
                    val fechaDesdeApi = formatDate(rawTimestamp)

                    val response = exchangeRateResponse.conversion_rates
                    for ((clave, valor) in response) {
                        val exchangeDetail = ExchangeRate(
                            nombre = clave,
                            cantidad = valor,
                            fecha = fechaDesdeApi // Guardando solo la fecha
                        )
                        db.exchangeRateDAO().insertar(exchangeDetail)
                    }

                    Log.i("MyHourlyTaskWorker", "Tasas de cambio guardadas con fecha: $fechaDesdeApi")
                    Result.success()
                } else {
                    Log.e("MyHourlyTaskWorker", "Error al obtener tasas de cambio: ${exchangeRateResponse.result}")
                    Result.failure()
                }
            } catch (e: IOException) {
                Log.e("MyHourlyTaskWorker", "Error de red: ${e.message}", e)
                Result.failure()
            } catch (e: HttpException) {
                Log.e("MyHourlyTaskWorker", "Error de servidor: ${e.code()}", e)
                Result.failure()
            } catch (e: Exception) {
                Log.e("MyHourlyTaskWorker", "Error inesperado: ${e.message}", e)
                Result.failure()
            }
        }
    }

}

@RequiresApi(Build.VERSION_CODES.O)
fun formatDate(unixTimestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    return Instant.ofEpochSecond(unixTimestamp)
        .atZone(ZoneId.of("UTC")) // Convierte el timestamp a una fecha en UTC
        .toLocalDate() // Extrae solo la fecha
        .format(formatter) // Formatea la fecha a "yyyy-MM-dd"
}

@Composable
fun ExchangeRateScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val exchangeRates = remember { mutableStateListOf<ExchangeRateItem>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {  // 🔹 Ejecutar en un hilo secundario
            val uri = Uri.parse("content://com.example.proyecto_divisa.ContentProvider/exchangerate")
            val cursor = contentResolver.query(uri, null, null, null, null)

            cursor?.use {
                while (it.moveToNext()) {
                    val nombre = it.getString(it.getColumnIndexOrThrow("nombre"))
                    val cantidad = it.getDouble(it.getColumnIndexOrThrow("cantidad"))
                    val fecha = it.getString(it.getColumnIndexOrThrow("fecha"))

                    Log.d("Consulta", "Moneda: $nombre, Tasa: $cantidad, Fecha: $fecha")

                    // 🔹 Agregar datos en el hilo principal para evitar problemas con Compose
                    withContext(Dispatchers.Main) {
                        exchangeRates.add(ExchangeRateItem(nombre, cantidad, fecha))
                    }
                }
            }
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Tasas de Cambio", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn {
            items(exchangeRates) { rate ->
                ExchangeRateItemView(rate)
            }
        }
    }
}

@Composable
fun ExchangeRateItemView(rate: ExchangeRateItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Moneda: ${rate.nombre}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Tasa: ${"%.4f".format(rate.cantidad)}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Fecha: ${rate.fecha}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

data class ExchangeRateItem(
    val nombre: String,
    val cantidad: Double,
    val fecha: String
)

@Preview(showBackground = true)
@Composable
fun PreviewExchangeRateScreen() {
    Proyecto_DivisaTheme {
        ExchangeRateScreen()
    }
}
