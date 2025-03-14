package com.example.proyecto_divisa.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exchangerate")
data class ExchangeRate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val cantidad: Double,
    val fecha: String
)