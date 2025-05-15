package es.selk.catalogoproductos.ui.detail

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import es.selk.catalogoproductos.R
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.databinding.ActivityProductDetailBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private val priceFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES")).apply { maximumFractionDigits = 4}
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

            if(producto.estado == "Anulado"){
                cardStock.visibility = View.GONE
                tvCantidadBultoLabel.visibility = View.GONE
                tvCantidadBulto.visibility = View.GONE
                tvUnidadVentaLabel.visibility = View.GONE
                tvUnidadVenta.visibility = View.GONE
                tvStockLabel.visibility = View.GONE
                tvStock.visibility = View.GONE
                tvDescuentoLabel.visibility = View.GONE
                tvDescuento.visibility = View.GONE
                tvPrecioLabel.text = "Último precio:"
                tvEstado.setTypeface(tvEstado.typeface, Typeface.BOLD)
                tvEstado.setTextColor(ContextCompat.getColor(binding.root.context, R.color.color_estado_anulado))
            }else{
                cardStock.visibility = View.VISIBLE
                tvCantidadBultoLabel.visibility = View.VISIBLE
                tvCantidadBulto.visibility = View.VISIBLE
                tvCantidadBulto.text = producto.cantidad_bulto.toInt().toString()
                tvUnidadVentaLabel.visibility = View.VISIBLE
                tvUnidadVenta.visibility = View.VISIBLE
                tvUnidadVenta.text = producto.unidad_venta.toInt().toString()
                tvStockLabel.visibility = View.VISIBLE
                tvStock.visibility = View.VISIBLE
                tvStock.text = producto.stock_actual.toInt().toString()
                tvDescuentoLabel.visibility = View.VISIBLE
                tvDescuento.visibility = View.VISIBLE
                tvDescuento.text = producto.descuento
                tvPrecioLabel.text = "Precio:"
                tvEstado.setTypeface(tvEstado.typeface, Typeface.NORMAL)
                when (producto.estado) {
                    "Activo" -> tvEstado.setTextColor(ContextCompat.getColor(binding.root.context, R.color.color_estado_activo))
                    else -> tvEstado.setTextColor(ContextCompat.getColor(binding.root.context, R.color.on_primary))
                }
            }

            tvCategoria.text = producto.familia
            tvPrecio.text = priceFormat.format(producto.precio_actual)
            tvUltimaActualizacion.text = dateFormat.format(Date(producto.ultima_actualizacion))
            tvEstado.text = producto.estado.uppercase()

            // Título de la toolbar
            toolbar.title = "Detalle: ${producto.referencia}"

        }
    }
}