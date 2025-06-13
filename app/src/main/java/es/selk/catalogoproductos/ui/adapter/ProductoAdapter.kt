package es.selk.catalogoproductos.ui.adapter


import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import es.selk.catalogoproductos.R
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.databinding.ItemProductoBinding
import java.text.NumberFormat
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

class ProductoAdapter(
    private val onItemClick: (ProductoEntity) -> Unit

) : ListAdapter<ProductoEntity, ProductoAdapter.ProductoViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val binding = ItemProductoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductoViewHolder(binding, onItemClick)
    }

    override fun submitList(list: List<ProductoEntity>?) {
        val newList = list?.toList() ?: emptyList()
        super.submitList(newList)
        {
            // Este callback se ejecuta cuando la operación DiffUtil está completa
            val recyclerView = currentList.let { recyclerView }
            recyclerView?.scrollToPosition(0)
        }
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        Log.d("ProductoAdapter", "Binding producto: ${getItem(position).descripcion}")
        holder.bind(getItem(position))
        holder.itemView.alpha = 0.7f
        holder.itemView.animate()
            .alpha(1f)
            .setDuration(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }


    class ProductoViewHolder(
        private val binding: ItemProductoBinding,
        private val onItemClick: (ProductoEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val priceFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES")).apply { maximumFractionDigits = 4}

        fun bind(producto: ProductoEntity) {
            binding.apply {
                tvId.text = producto.referencia
                tvDescripcion.text = producto.descripcion
                if(producto.estado == "Anulado") {
                    tvCantidadBulto.visibility = android.view.View.GONE
                    tvUnidadVenta.visibility = android.view.View.GONE
                    tvStock.visibility = android.view.View.GONE
                    tvDescuento.visibility = android.view.View.GONE

                } else {
                    tvCantidadBulto.visibility = android.view.View.VISIBLE
                    tvUnidadVenta.visibility = android.view.View.VISIBLE
                    tvStock.visibility = android.view.View.VISIBLE
                    tvDescuento.visibility = android.view.View.VISIBLE
                    tvCantidadBulto.text = "Cantidad bulto: ${producto.cantidad_bulto.toInt()}"
                    tvUnidadVenta.text = "Unidad venta: ${producto.unidad_venta.toInt()}"
                    val stockText = "Stock: ${producto.stock_actual.toInt()}"
                    tvStock.text = stockText
                    tvStock.setTextColor(when {
                        producto.stock_actual > 1.0 -> Color.parseColor("#4CAF50") // Verde
                        else -> Color.parseColor("#CF6679")  // Rojo
                    })

                    if (producto.descuento == "") {
                        tvDescuento.visibility = android.view.View.GONE
                    } else {
                        tvDescuento.visibility = android.view.View.VISIBLE
                        tvDescuento.text = "Descuento: ${producto.descuento}"
                    }
                }

                tvFamilia.text = producto.familia
                tvPrecio.text = priceFormat.format(producto.precio_actual)

                tvEstado.text = producto.estado.uppercase()
                if (producto.estado == "Anulado") {
                    // Si es "Anulado", establecer texto en negrita
                    tvEstado.setTypeface(tvEstado.typeface, Typeface.BOLD)
                    tvEstado.setTextColor(ContextCompat.getColor(binding.root.context, R.color.color_estado_anulado))
                } else {
                    // Si es "Activo" u otro estado, usar estilo normal
                    tvEstado.setTypeface(tvEstado.typeface, Typeface.NORMAL)
                    // Asignar color según el estado
                    when (producto.estado) {
                        "Activo" -> tvEstado.setTextColor(ContextCompat.getColor(binding.root.context, R.color.color_estado_activo))
                        else -> tvEstado.setTextColor(ContextCompat.getColor(binding.root.context, R.color.on_primary))
                    }
                }
                tvLocalizacion.text = "Localizacion: ${producto.localizacion}"

                tvId.setOnClickListener { view ->
                    val context = view.context
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Referencia del producto", producto.referencia)
                    clipboard.setPrimaryClip(clip)

                    // Mostrar mensaje de confirmación
                    Toast.makeText(context, "Referencia '${producto.referencia}' copiada", Toast.LENGTH_SHORT).show()

                    // Importante: NO llamar a onItemClick aquí para evitar navegación
                    Log.d("ProductoAdapter", "Referencia copiada: ${producto.referencia}")

                    // Evitar que el evento se propague al contenedor padre
                    // (esto evita que se ejecute el click del root)
                }
                root.setOnClickListener { onItemClick(producto) }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ProductoEntity>() {
            override fun areItemsTheSame(oldItem: ProductoEntity, newItem: ProductoEntity): Boolean {
                return oldItem.id_producto == newItem.id_producto
            }

            override fun areContentsTheSame(oldItem: ProductoEntity, newItem: ProductoEntity): Boolean {
                // Check all relevant fields that should trigger a visual update
                return oldItem.id_producto == newItem.id_producto &&
                        oldItem.referencia == newItem.referencia &&
                        oldItem.descripcion == newItem.descripcion &&
                        oldItem.precio_actual == newItem.precio_actual &&
                        oldItem.stock_actual == newItem.stock_actual &&
                        oldItem.estado == newItem.estado
            }
        }
    }
}