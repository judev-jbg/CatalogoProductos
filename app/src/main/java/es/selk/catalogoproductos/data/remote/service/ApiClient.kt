package es.selk.catalogoproductos.data.remote.service

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import es.selk.catalogoproductos.data.remote.api.ProductoApiService
import es.selk.catalogoproductos.data.remote.model.ProductoResponse
import es.selk.catalogoproductos.data.remote.model.VersionResponse

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Esta será una implementación simulada que no realiza llamadas de red reales
    val productoApiService: ProductoApiService = object : ProductoApiService {
        override suspend fun checkVersion(): VersionResponse {
            // Simulamos retornar una versión que no requiere actualización
            return VersionResponse(
                version = "1.0.0",
                timestamp = System.currentTimeMillis(),
                changesCount = 0
            )
        }

        override suspend fun getAllProductos(): List<ProductoResponse> {
            // Simulamos una lista vacía por ahora
            return emptyList()
        }

        override suspend fun getChanges(since: Long): List<ProductoResponse> {
            // Simulamos que no hay cambios
            return emptyList()
        }
    }
}