package es.selk.catalogoproductos.ui.main

import android.app.ActionBar
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import es.selk.catalogoproductos.data.repository.ProductoRepository
import es.selk.catalogoproductos.data.repository.SyncRepository
import es.selk.catalogoproductos.databinding.ActivityMainBinding
import es.selk.catalogoproductos.ui.adapter.ProductoAdapter
import es.selk.catalogoproductos.ui.detail.ProductDetailActivity
import es.selk.catalogoproductos.ui.viewmodel.ProductoViewModel
import es.selk.catalogoproductos.ui.viewmodel.StockFilter
import es.selk.catalogoproductos.ui.viewmodel.SyncViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var searchView: SearchView? = null
    private var searchViewExpanded = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupListeners()
        setupStockFilterChips()

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

    private fun setupRecyclerView() {
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        val layoutManager = GridLayoutManager(this, columnCount)
        layoutManager.scrollToPositionWithOffset(0, 0)

        // Limpiar decoraciones existentes y agregar una nueva
        binding.recyclerView.clearItemDecorations()

        val spacing = resources.getDimensionPixelSize(R.dimen.card_margin)
        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(columnCount, spacing, true))

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = productoAdapter

        binding.recyclerView.scrollToPosition(0)

        // Agregar este listener para forzar relayout cuando cambia la lista
        productoAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                binding.recyclerView.post { binding.recyclerView.invalidateItemDecorations() }
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                binding.recyclerView.post { binding.recyclerView.invalidateItemDecorations() }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.recyclerView.post { binding.recyclerView.invalidateItemDecorations() }
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                binding.recyclerView.post { binding.recyclerView.invalidateItemDecorations() }
            }
        })
    }

    // Agregar esta extensión
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
            syncViewModel.syncData()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observar resultados de búsqueda
                launch {
                    productoViewModel.searchResults.collectLatest { productos ->
                        productoAdapter.submitList(productos)
                        Log.d("MainActivity", "Productos cargados: ${productos.size}")
                        binding.tvEmpty.isVisible = productos.isEmpty() && !syncViewModel.isSyncing.value
                        binding.recyclerView.isVisible = productos.isNotEmpty()
                        if (productos.isNotEmpty()) {
                            binding.recyclerView.scrollToPosition(0)
                        }
                    }
                }

                // Observar estado de carga
                launch {
                    productoViewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.isVisible = isLoading && !syncViewModel.isSyncing.value
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
                        binding.fabSync.isEnabled = !isSyncing

                        // If sync just completed, refresh the product list
                        if (isSyncing) {
                            binding.tvEmpty.isVisible = false
                        } else {
                            // Si terminó la sincronización y hay que refrescar
                            if (syncViewModel.justCompletedSync) {
                                syncViewModel.justCompletedSync = false
                                Log.d("MainActivity", "UI: Sincronización completa, refrescando productos")
                                productoViewModel.refreshProducts()
                            }
                        }
                    }
                }

                // Observar fecha de última actualización
                launch {
                    syncViewModel.fechaUltimaActualizacion.collectLatest { fecha ->
                        binding.tvLastUpdate.text = "Última sincronización: $fecha"
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
                        }
                    }
                }
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

        }

        // Detector de colapso
        searchView?.setOnCloseListener {
            Log.d("MainActivity", "SearchView colapsado")
            searchViewExpanded = false
            resetFiltersToDefault()
            false
        }

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                Log.d("MainActivity", "onMenuItemActionExpand llamado")
                searchViewExpanded = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                Log.d("MainActivity", "onMenuItemActionCollapse llamado")
                searchViewExpanded = false
                resetFiltersToDefault()
                return true
            }
        })

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    productoViewModel.setSearchQuery(query)
                    searchView?.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Actualizar la consulta en el ViewModel
                productoViewModel.setSearchQuery(newText ?: "")

                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync -> {
                syncViewModel.syncData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}