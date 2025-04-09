package es.selk.catalogoproductos.data.remote.api

import es.selk.catalogoproductos.data.remote.model.ProductoResponse
import es.selk.catalogoproductos.data.remote.model.VersionResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ProductoApiService {
    @GET("version")
    suspend fun checkVersion(): VersionResponse

    @GET("productos")
    suspend fun getAllProductos(): List<ProductoResponse>

    @GET("productos/changes")
    suspend fun getChanges(@Query("since") since: Long): List<ProductoResponse>
}