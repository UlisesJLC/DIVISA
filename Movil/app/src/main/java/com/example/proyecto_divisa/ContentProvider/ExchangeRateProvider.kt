package com.example.proyecto_divisa.ContentProvider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.content.Intent
import com.example.proyecto_divisa.room.AppDatabase

class ExchangeRateProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.proyecto_divisa.ContentProvider"
        const val TABLE_NAME = "exchangerate"

        // Códigos para las URIs
        const val CODE_ALL_EXCHANGE_RATES = 1
        const val CODE_SPECIFIC_EXCHANGE_RATE = 2

        val URI_EXCHANGE_RATE: Uri = Uri.parse("content://$AUTHORITY/$TABLE_NAME")

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, TABLE_NAME, CODE_ALL_EXCHANGE_RATES)
            addURI(AUTHORITY, "$TABLE_NAME/*/*/*", CODE_SPECIFIC_EXCHANGE_RATE)
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db = AppDatabase.getInstance(context!!).exchangeRateDAO()

        return when (uriMatcher.match(uri)) {
            CODE_ALL_EXCHANGE_RATES -> {
                Log.d("ExchangeRateProvider", "Consulta de todas las tasas de cambio")
                db.getAllRatesCursor()
            }

            CODE_SPECIFIC_EXCHANGE_RATE -> {
                val moneda = uri.pathSegments[1]
                val fechaInicio = uri.pathSegments[2]
                val fechaFin = uri.pathSegments[3]

                Log.d("ExchangeRateProvider", "Consulta de tasas para $moneda entre $fechaInicio y $fechaFin")
                db.getExchangeRatesForCurrency(moneda, fechaInicio, fechaFin)
            }

            else -> throw IllegalArgumentException("URI no soportada: $uri")
        }
    }

    override fun getType(uri: Uri): String? {
        context?.grantUriPermission("com.example.clientedivisas", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return when (uriMatcher.match(uri)) {
            CODE_ALL_EXCHANGE_RATES -> "vnd.android.cursor.dir/$AUTHORITY.$TABLE_NAME"
            CODE_SPECIFIC_EXCHANGE_RATE -> "vnd.android.cursor.item/$AUTHORITY.$TABLE_NAME"
            else -> throw IllegalArgumentException("URI no soportada: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("No se admite la inserción a través de ContentProvider")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("No se admite la eliminación a través de ContentProvider")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("No se admite la actualización a través de ContentProvider")
    }
}
