package es.selk.catalogoproductos.ui.detail

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.databinding.ActivityProductDetailBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private val priceFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Obtener el producto de los extras
        val producto = intent.getParcelableExtra<ProductoEntity>("producto")
        producto?.let {
            mostrarDetalleProducto(it)
        }
    }

    private fun mostrarDetalleProducto(producto: ProductoEntity) {
        binding.apply {
            tvId.text = producto.referencia
            tvDescripcion.text = producto.descripcion
            tvCantidadBulto.text = producto.cantidad_bulto.toString()
            tvUnidadVenta.text = producto.unidad_venta.toString()
            tvCategoria.text = producto.familia
            tvStock.text = producto.stock_actual.toString()
            tvPrecio.text = priceFormat.format(producto.precio_actual)
            tvDescuento.text = producto.descuento
            tvUltimaActualizacion.text = dateFormat.format(Date(producto.ultima_actualizacion))

            // Título de la toolbar
            toolbar.title = "Detalle: ${producto.referencia}"
        }
    }
}