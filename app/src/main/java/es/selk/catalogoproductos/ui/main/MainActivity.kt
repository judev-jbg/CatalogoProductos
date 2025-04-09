package es.selk.catalogoproductos.ui.main

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import es.selk.catalogoproductos.R
import es.selk.catalogoproductos.data.local.database.AppDatabase
import es.selk.catalogoproductos.data.repository.ProductoRepository
import es.selk.catalogoproductos.data.repository.SyncRepository
import es.selk.catalogoproductos.databinding.ActivityMainBinding
import es.selk.catalogoproductos.ui.adapter.ProductoAdapter
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

    private val productoViewModel: ProductoViewModel by viewModels {
        ProductoViewModel.Factory(productoRepository)
    }

    private val syncViewModel: SyncViewModel by viewModels {
        SyncViewModel.Factory(syncRepository)
    }

    private val productoAdapter = ProductoAdapter { producto ->
        // Navegación al detalle del producto (implementar en el futuro)
        Toast.makeText(this, "Producto: ${producto.descripcion}", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.adapter = productoAdapter
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
                    }
                }

                // Observar estado de carga
                launch {
                    productoViewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.isVisible = isLoading
                    }
                }

                // Observar estado de sincronización
                launch {
                    syncViewModel.isSyncing.collectLatest { isSyncing ->
                        binding.progressBar.isVisible = isSyncing
                        binding.fabSync.isEnabled = !isSyncing
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

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    productoViewModel.setSearchQuery(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrBlank() && newText.length >= 3) {
                    productoViewModel.setSearchQuery(newText)
                }
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