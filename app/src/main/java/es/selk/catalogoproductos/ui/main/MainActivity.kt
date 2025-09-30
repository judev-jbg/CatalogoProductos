package es.selk.catalogoproductos.ui.main

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.selk.catalogoproductos.MainApplication
import es.selk.catalogoproductos.R
import es.selk.catalogoproductos.data.local.database.AppDatabase
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.data.repository.ProductoRepository
import es.selk.catalogoproductos.data.repository.SyncRepository
import es.selk.catalogoproductos.databinding.ActivityMainBinding
import es.selk.catalogoproductos.ui.adapter.ProductoAdapter
import es.selk.catalogoproductos.ui.adapter.ProductoSkeletonAdapter
import es.selk.catalogoproductos.ui.detail.ProductDetailActivity
import es.selk.catalogoproductos.ui.viewmodel.ProductoViewModel
import es.selk.catalogoproductos.ui.viewmodel.StockFilter
import es.selk.catalogoproductos.ui.viewmodel.SyncViewModel
import es.selk.catalogoproductos.utils.NetworkUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var searchView: SearchView? = null
    private var searchViewExpanded = false
    private var isNetworkAvailable = true

    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    private val productoRepository by lazy {
        ProductoRepository(
            db.productoDao(),
        )
    }

    private val syncRepository by lazy {
        SyncRepository(
            db.productoDao(),
            db.ultimaActualizacionDao()
        )
    }

    private lateinit var productoViewModel: ProductoViewModel

    private val syncViewModel: SyncViewModel by viewModels {
        SyncViewModel.Factory(syncRepository, applicationContext)
    }

    private val productoAdapter = ProductoAdapter { producto ->
        val intent = Intent(this, ProductDetailActivity::class.java)
        intent.putExtra("producto", producto)

        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
        startActivity(intent, options.toBundle())
    }

    private val skeletonAdapter = ProductoSkeletonAdapter(10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupSkeletonRecyclerView()
        setupListeners()
        setupStockFilterChips()
        setupNetworkObserver()

        lifecycleScope.launch {
            MainApplication.isInitialSyncRunning.collect { isRunning ->
                if (isRunning) {
                    binding.syncOverlay.visibility = View.VISIBLE
                    syncViewModel.checkInitialSyncStatus()
                }
            }
        }

        observeViewModel()

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ProductoViewModel(productoRepository) as T
            }
        }

        productoViewModel = ViewModelProvider(this, factory)[ProductoViewModel::class.java]
    }

    private fun setupSkeletonRecyclerView() {
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        val layoutManager = GridLayoutManager(this, columnCount)

        binding.skeletonRecyclerView.layoutManager = layoutManager
        binding.skeletonRecyclerView.adapter = skeletonAdapter

        val spacing = resources.getDimensionPixelSize(R.dimen.card_margin)
        binding.skeletonRecyclerView.addItemDecoration(
            GridSpacingItemDecoration(columnCount, spacing, true)
        )
    }

    private fun setupRecyclerView() {
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        val layoutManager = GridLayoutManager(this, columnCount)

        binding.recyclerView.clearItemDecorations()

        val spacing = resources.getDimensionPixelSize(R.dimen.card_margin)
        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(columnCount, spacing, true))

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = productoAdapter

        binding.recyclerView.apply {
            // Deshabilitar animaciones predeterminadas para que aparezcan más rápido
            itemAnimator = null

            // Configurar cache para mejor rendimiento
            setHasFixedSize(true)
            setItemViewCacheSize(20)

            // Habilitar drawing cache
            isDrawingCacheEnabled = true
            drawingCacheQuality = android.view.View.DRAWING_CACHE_QUALITY_HIGH
        }

        Log.d("MainActivity", "RecyclerView configurado correctamente")
    }

    private fun setupNetworkObserver() {
        // Observar cambios en la conectividad de red
        NetworkUtil.NetworkLiveData(this).observe(this) { isConnected ->
            isNetworkAvailable = isConnected
            updateNetworkStatus(isConnected)
        }
    }

    private fun updateNetworkStatus(isConnected: Boolean) {
        Log.d("MainActivity", "Estado de red: ${if (isConnected) "Conectado" else "Desconectado"}")

        if (!isConnected) {
            // Si no hay conexión, determinar el mensaje apropiado
            lifecycleScope.launch {
                val offlineMessage = determineOfflineMessage()
                binding.tvOfflineStatus.text = offlineMessage
                binding.tvOfflineStatus.isVisible = true
                Log.d("MainActivity", "Mensaje offline establecido: $offlineMessage")
            }
        } else {
            // Si hay conexión, ocultar el mensaje
            binding.tvOfflineStatus.isVisible = false

            // Verificar si necesitamos ejecutar sincronización inicial automática
            checkAndRunInitialSync()
        }

        // Habilitar/deshabilitar botón de sincronización
        binding.fabSync.isEnabled = isConnected

        // Cambiar apariencia del botón cuando está deshabilitado
        if (isConnected) {
            binding.fabSync.alpha = 1.0f
        } else {
            binding.fabSync.alpha = 0.5f
        }
    }

    /**
     * Verifica si necesitamos ejecutar sincronización inicial automática
     * cuando se detecta conexión por primera vez
     */
    private fun checkAndRunInitialSync() {
        lifecycleScope.launch {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val isFirstRun = prefs.getBoolean("is_first_run", true)

            // Verificar si hay productos en la base de datos
            val productCount = try {
                productoViewModel.getProductCount()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al obtener conteo de productos", e)
                0
            }

            // Si es primera ejecución O no hay productos, ejecutar sincronización inicial
            if (isFirstRun || productCount == 0) {
                Log.d("MainActivity", "Detectada conexión en primera instalación. Iniciando sincronización automática.")

                // Marcar que ya no es primera ejecución
                prefs.edit().putBoolean("is_first_run", false).apply()

                // Mostrar mensaje de que se está sincronizando
                Toast.makeText(this@MainActivity, "Conexión detectada. Iniciando configuración inicial...", Toast.LENGTH_LONG).show()

                // Ejecutar sincronización inicial específica
                syncViewModel.syncInitialData()
            }
        }
    }

    private suspend fun determineOfflineMessage(): String {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        // Verificar si hay productos en la base de datos
        val productCount = try {
            productoViewModel.getProductCount()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al obtener conteo de productos", e)
            0
        }

        return if (isFirstRun || productCount == 0) {
            // Primera instalación o base de datos vacía
            "Sin conexión: Se requiere conexión a internet para la configuración inicial."
        } else {
            // Uso normal con productos existentes
            "Modo offline: Puedes usar la aplicación pero no será posible sincronizar productos."
        }
    }

    /**
     * Método auxiliar para refrescar el mensaje offline cuando es necesario
     */
    private fun refreshOfflineMessage() {
        if (!isNetworkAvailable) {
            lifecycleScope.launch {
                val offlineMessage = determineOfflineMessage()
                binding.tvOfflineStatus.text = offlineMessage
                Log.d("MainActivity", "Mensaje offline actualizado: $offlineMessage")
            }
        }
    }

    private fun setupStockFilterChips() {
        // Configurar listeners para los chips
        binding.chipAll.setOnClickListener {
            productoViewModel.setStockFilter(StockFilter.ALL)
        }

        binding.chipInStock.setOnClickListener {
            productoViewModel.setStockFilter(StockFilter.IN_STOCK)
        }

        binding.chipNoStock.setOnClickListener {
            productoViewModel.setStockFilter(StockFilter.NO_STOCK)
        }
    }

    private fun resetFiltersToDefault() {
        Log.d("MainActivity", "Reseteando filtros a valores predeterminados")
        binding.chipAll.isChecked = true
        binding.chipInStock.isChecked = false
        binding.chipNoStock.isChecked = false
        productoViewModel.resetStockFilter()
    }



    fun RecyclerView.clearItemDecorations() {
        while (itemDecorationCount > 0) {
            removeItemDecorationAt(0)
        }
    }

    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val adapter = parent.adapter ?: return
            val totalItems = adapter.itemCount
            if (totalItems == 0) return

            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return

            val column = position % spanCount

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount

                if (position < spanCount) {
                    outRect.top = spacing
                }
                outRect.bottom = spacing
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount

                if (position >= spanCount) {
                    outRect.top = spacing
                }
            }
        }
    }

    private fun setupListeners() {
        binding.fabSync.setOnClickListener {
            // Solo permitir sincronización si hay conexión
            if (isNetworkAvailable) {
                syncViewModel.syncData()
            } else {
                Toast.makeText(this, "No hay conexión a internet. No es posible sincronizar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observar resultados de búsqueda
                launch {
                    combine(
                        productoViewModel.searchResults,
                        productoViewModel.isLoading,
                        syncViewModel.isSyncing
                    ) { productos, isLoading, isSyncing ->
                        Triple(productos, isLoading, isSyncing)
                    }.collect { (productos, isLoading, isSyncing) ->
                        Log.d("MainActivity", "Estado combinado - productos: ${productos.size}, isLoading: $isLoading, isSyncing: $isSyncing")

                        // Actualizar adapter solo si no está cargando
                        if (!isLoading && !isSyncing) {
                            productoAdapter.submitList(productos) {
                                Log.d("MainActivity", "submitList completado")
                            }
                        }

                        // Actualizar visibilidad
                        updateViewVisibility(
                            productos = productos,
                            isLoading = isLoading,
                            isSyncing = isSyncing
                        )

                        // Scroll y refresh offline message
                        if (productos.isNotEmpty() && !isLoading && !isSyncing) {
                            binding.recyclerView.post {
                                binding.recyclerView.scrollToPosition(0)
                            }
                            refreshOfflineMessage()
                        }
                    }
                }

                // Observar estado de carga
                launch {
                    productoViewModel.isLoading.collectLatest { isLoading ->
                        val isSyncing = syncViewModel.isSyncing.value

                        // Mostrar skeleton cuando está cargando y NO está sincronizando
                        binding.skeletonRecyclerView.isVisible = isLoading && !isSyncing

                        // Ocultar RecyclerView principal cuando está cargando
                        if (isLoading) {
                            binding.recyclerView.isVisible = false
                        }

                        // ProgressBar solo para sincronización
                        binding.progressBar.isVisible = false
                    }
                }

                // Observar estado del filtro de stock y actualizar la UI
                launch {
                    productoViewModel.stockFilter.collectLatest { filter ->
                        // Actualizar UI de los chips
                        binding.chipAll.isChecked = filter == StockFilter.ALL
                        binding.chipInStock.isChecked = filter == StockFilter.IN_STOCK
                        binding.chipNoStock.isChecked = filter == StockFilter.NO_STOCK
                    }
                }

                // Observar estado de sincronización
                launch {
                    syncViewModel.isSyncing.collectLatest { isSyncing ->
                        Log.d("MainActivity", "UI: Estado de sincronización: $isSyncing")
                        // Show overlay during sync
                        binding.syncOverlay.isVisible = isSyncing

                        // Solo habilitar fabSync si hay conexión Y no se está sincronizando
                        binding.fabSync.isEnabled = isNetworkAvailable && !isSyncing

                        binding.syncOverlay.isVisible = isSyncing
                        binding.fabSync.isEnabled = isNetworkAvailable && !isSyncing

                        // If sync just completed, refresh the product list
                        if (!isSyncing && syncViewModel.justCompletedSync) {
                            syncViewModel.justCompletedSync = false
                            Log.d("MainActivity", "UI: Sincronización completa, refrescando productos")
                            Toast.makeText(this@MainActivity, "Sincronización completa", Toast.LENGTH_SHORT).show()
                            productoViewModel.refreshProducts()
                            refreshOfflineMessage()
                        }
                    }
                }

                // Observar fecha de última actualización
                launch {
                    syncViewModel.fechaUltimaActualizacion.collectLatest { fecha ->
                        binding.tvLastUpdate.text = "Última actualización: $fecha"
                    }
                }

                // Observar errores
                launch {
                    productoViewModel.error.collectLatest { error ->
                        if (error != null) {

                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Observar errores de sincronización
                launch {
                    syncViewModel.error.collectLatest { error ->
                        if (error != null) {

                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                            // Limpiar el error después de mostrarlo para evitar re-emisiones
                            syncViewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    /**
     * Función centralizada para manejar la visibilidad de las vistas
     * según el estado actual de la aplicación
     */
    private fun updateViewVisibility(
        productos: List<ProductoEntity>,
        isLoading: Boolean,
        isSyncing: Boolean
    ) {
        Log.d("MainActivity", "updateViewVisibility - productos: ${productos.size}, isLoading: $isLoading, isSyncing: $isSyncing")

        when {
            // Caso 1: Está sincronizando - mostrar overlay (ocultar todo lo demás)
            isSyncing -> {
                Log.d("MainActivity", "Estado: Sincronizando")
                binding.skeletonRecyclerView.isVisible = false
                binding.recyclerView.isVisible = false
                binding.tvEmpty.isVisible = false
                binding.progressBar.isVisible = false
            }

            // Caso 2: Está cargando/buscando - mostrar skeleton
            isLoading -> {
                Log.d("MainActivity", "Estado: Cargando")
                binding.skeletonRecyclerView.isVisible = true
                binding.recyclerView.isVisible = false
                binding.tvEmpty.isVisible = false
                binding.progressBar.isVisible = false
            }

            // Caso 3: Carga completa con productos - mostrar RecyclerView
            productos.isNotEmpty() -> {
                Log.d("MainActivity", "Estado: Mostrando ${productos.size} productos")
                binding.skeletonRecyclerView.isVisible = false
                binding.recyclerView.isVisible = true
                binding.tvEmpty.isVisible = false
                binding.progressBar.isVisible = false

                // Forzar invalidación del RecyclerView para asegurar que se dibuje
                binding.recyclerView.post {
                    binding.recyclerView.requestLayout()
                    binding.recyclerView.invalidate()
                }
            }

            // Caso 4: Carga completa sin productos - mostrar mensaje vacío
            else -> {
                Log.d("MainActivity", "Estado: Sin productos")
                binding.skeletonRecyclerView.isVisible = false
                binding.recyclerView.isVisible = false
                binding.tvEmpty.isVisible = true
                binding.progressBar.isVisible = false
            }
        }
    }


    override fun onBackPressed() {
        // Añadimos manejo específico para el botón atrás
        if (searchView != null && searchViewExpanded) {
            Log.d("MainActivity", "onBackPressed: SearchView está expandido, colapsando")
            searchView?.setQuery("", false)
            searchView?.isIconified = true

            resetFiltersToDefault()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView

        // Configurar para que use todo el ancho disponible cuando se expande
        searchView?.maxWidth = Integer.MAX_VALUE

        searchView?.queryHint = "Referencia, descripción o familia"

        // Configure search text appearance
        val searchEditText = searchView?.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.apply {
            // Forzar color de texto blanco siempre
            setTextColor(Color.WHITE)
            setHintTextColor(Color.WHITE)

            // Opcional: ajustar el tamaño del texto para mejor visibilidad
            textSize = 24f
        }

        // Cambiar color de todos los iconos a blanco
        val searchIcon = searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
        searchIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        val closeIcon = searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        // Botón para limpiar la consulta
        closeIcon?.setOnClickListener {
            Log.d("MainActivity", "Botón cerrar presionado")
            searchView?.setQuery("", false)

            // Si ya está vacío, colapsar y resetear filtros
            if (searchView?.query.isNullOrEmpty()) {
                Log.d("MainActivity", "Consulta vacía, colapsando y reseteando filtros")
                searchView?.isIconified = true
                resetFiltersToDefault()
            }
        }

        // Cambiar color de los iconos de submit y de voz (si existen)
        val submitIcon = searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_go_btn)
        submitIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        val voiceIcon = searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_voice_btn)
        voiceIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        // Monitorear directamente cambios en el estado de expansión
        searchView?.setOnSearchClickListener {
            Log.d("MainActivity", "SearchView expandido")
            searchViewExpanded = true

            // Mostrar el mensaje de ayuda para búsqueda
            //binding.tvSearchHint.visibility = View.VISIBLE
        }

        // Detector de colapso
        searchView?.setOnCloseListener {
            Log.d("MainActivity", "SearchView colapsado")
            searchViewExpanded = false

            // Ocultar el mensaje de ayuda para búsqueda
            //binding.tvSearchHint.visibility = View.GONE

            resetFiltersToDefault()
            false
        }

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                Log.d("MainActivity", "onMenuItemActionExpand llamado")
                searchViewExpanded = true

                // Mostrar el mensaje de ayuda para búsqueda
                //binding.tvSearchHint.visibility = View.VISIBLE

                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                Log.d("MainActivity", "onMenuItemActionCollapse llamado")
                searchViewExpanded = false

                // Ocultar el mensaje de ayuda para búsqueda
                //binding.tvSearchHint.visibility = View.GONE

                resetFiltersToDefault()
                return true
            }
        })

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    productoViewModel.setSearchQuery(query)
                    searchView?.clearFocus()

                    // Manejar visibilidad del mensaje basado en la longitud
                   // updateSearchHintVisibility(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Actualizar la consulta en el ViewModel
                productoViewModel.setSearchQuery(newText ?: "")

                // Manejar visibilidad del mensaje basado en la longitud
                //updateSearchHintVisibility(newText)

                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync -> {
                // Solo permitir sincronización si hay conexión
                if (isNetworkAvailable) {
                    syncViewModel.syncData()
                } else {
                    Toast.makeText(this, "No hay conexión a internet. No es posible sincronizar.", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}