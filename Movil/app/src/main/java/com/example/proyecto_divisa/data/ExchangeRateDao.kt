package com.example.proyecto_divisa.room

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDAO {

    @Insert
    suspend fun insertar(exchangeRate: ExchangeRate)

    @Query("SELECT * FROM exchangerate")
    fun getAllRates(): Flow<List<ExchangeRate>> // Usamos Flow para observar cambios

    @Query("SELECT * FROM exchangerate WHERE id = :id")
    suspend fun obtenerPorId(id: Int): ExchangeRate?

    @Query("DELETE FROM exchangerate WHERE id = :id")
    suspend fun eliminarPorId(id: Int)

    @Query("SELECT * FROM exchangerate")
    fun getAllRatesCursor(): Cursor


    @Query("SELECT * FROM exchangerate WHERE nombre = :moneda AND fecha BETWEEN :fechaInicio AND :fechaFin ORDER BY fecha DESC")
    fun getExchangeRatesForCurrency(moneda: String, fechaInicio: String, fechaFin: String): Cursor

}