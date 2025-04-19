package es.selk.catalogoproductos.ui.main

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.selk.catalogoproductos.R
import es.selk.catalogoproductos.data.local.database.AppDatabase
import es.selk.catalogoproductos.data.repository.ProductoRepository
import es.selk.catalogoproductos.data.repository.SyncRepository
import es.selk.catalogoproductos.databinding.ActivityMainBinding
import es.selk.catalogoproductos.ui.adapter.ProductoAdapter
import es.selk.catalogoproductos.ui.detail.ProductDetailActivity
import es.selk.catalogoproductos.ui.viewmodel.ProductoViewModel
import es.selk.catalogoproductos.ui.viewmodel.SyncViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    private val productoRepository by lazy {
        ProductoRepository(
            db.productoDao(),
            db.historialPrecioDao(),
            db.historialStockDao()
        )
    }

    private val syncRepository by lazy {
        SyncRepository(
            db.productoDao(),
            db.historialPrecioDao(),
            db.historialStockDao(),
            db.ultimaActualizacionDao()
        )
    }

    private lateinit var productoViewModel: ProductoViewModel

    private val syncViewModel: SyncViewModel by viewModels {
        SyncViewModel.Factory(syncRepository)
    }

    private val productoAdapter = ProductoAdapter { producto ->
        val intent = Intent(this, ProductDetailActivity::class.java)
        intent.putExtra("producto", producto)

        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ProductoViewModel(productoRepository) as T
            }
        }

        productoViewModel = ViewModelProvider(this, factory)[ProductoViewModel::class.java]

    }

    private fun setupRecyclerView() {
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        val layoutManager = GridLayoutManager(this, columnCount)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = productoAdapter

        val spacing = resources.getDimensionPixelSize(R.dimen.card_margin)
        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(columnCount, spacing, true))

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
            val position = parent.getChildAdapterPosition(view)
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
                        binding.tvEmpty.isVisible = productos.isEmpty()
                        Log.d("MainActivity", "Productos cargados: ${productos.size}")
                    }
                }

                // Observar estado de carga
                launch {
                    productoViewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.isVisible = isLoading && !syncViewModel.isSyncing.value
                    }
                }

                // Observar estado de sincronización
                launch {
                    syncViewModel.isSyncing.collectLatest { isSyncing ->
                        // Show overlay during sync
                        binding.syncOverlay.isVisible = isSyncing
                        binding.fabSync.isEnabled = !isSyncing

                        // If sync just completed, refresh the product list
                        if (!isSyncing && syncViewModel.justCompletedSync) {
                            syncViewModel.justCompletedSync = false
                            productoViewModel.refreshProducts()
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
                        }
                    }
                }
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.queryHint = "Referencia o descripción del producto"

        // Configure search text appearance
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_primary))
        }

        // Search icon color
        val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
        searchIcon?.setColorFilter(ContextCompat.getColor(this, R.color.on_primary), PorterDuff.Mode.SRC_IN)

        // Get close button reference
        val closeIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon?.setColorFilter(ContextCompat.getColor(this, R.color.on_primary), PorterDuff.Mode.SRC_IN)

        // Track if search is empty to change close button behavior
        var isSearchEmpty = true

        // Custom OnCloseListener to handle search view closing
        searchView.setOnCloseListener {
            productoViewModel.setSearchQuery("")
            false // Let the system handle the default closing behavior
        }

        // Handle empty search when action view is collapsed
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchView.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchView.findFocus(), InputMethodManager.SHOW_IMPLICIT)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                productoViewModel.setSearchQuery("") // Reset search to show all products
                return true
            }
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    productoViewModel.setSearchQuery(query)
                    searchView.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Update close button behavior based on text content
                isSearchEmpty = newText.isNullOrBlank()

                // Dynamically change the close button behavior
                closeIcon?.setOnClickListener {
                    if (isSearchEmpty) {
                        // If empty, collapse the search view
                        searchItem.collapseActionView()
                    } else {
                        // If not empty, just clear the text
                        searchView.setQuery("", false)
                    }
                }

                // Always update search query
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