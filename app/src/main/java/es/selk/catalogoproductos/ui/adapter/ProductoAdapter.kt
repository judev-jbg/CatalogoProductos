package es.selk.catalogoproductos.ui.adapter

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
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

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        Log.d("ProductoAdapter", "Binding producto: ${getItem(position).descripcion}")
        holder.bind(getItem(position))
        holder.itemView.alpha = 0f
        holder.itemView.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }


    class ProductoViewHolder(
        private val binding: ItemProductoBinding,
        private val onItemClick: (ProductoEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val priceFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))

        fun bind(producto: ProductoEntity) {
            binding.apply {
                tvId.text = producto.referencia
                tvDescripcion.text = producto.descripcion
                tvCantidadBulto.text = "Cantidad bulto: ${producto.cantidad_bulto}"
                tvUnidadVenta.text = "Unidad venta: ${producto.unidad_venta}"
                tvFamilia.text = producto.familia
                tvPrecio.text = priceFormat.format(producto.precio_actual)
                val stockText = "Stock: ${producto.stock_actual}"
                tvStock.text = stockText
                tvStock.setTextColor(when {
                    producto.stock_actual > 10.0 -> Color.parseColor("#FFA000") // Verde
                    producto.stock_actual > 0.0 -> Color.parseColor("#FFA000")  // Ámbar
                    else -> Color.parseColor("#CF6679")  // Rojo
                })

                if (producto.descuento == "") {
                    tvDescuento.visibility = android.view.View.GONE
                } else {
                    tvDescuento.visibility = android.view.View.VISIBLE
                    tvDescuento.text = "Descuento: ${producto.descuento}"
                }

                tvEstado.text = producto.estado
                tvEstado.setTextColor(when {
                    producto.estado == "Activo" -> ContextCompat.getColor(binding.root.context, R.color.color_estado_activo)
                    producto.estado == "Anulado" -> ContextCompat.getColor(binding.root.context, R.color.color_estado_anulado)
                    else -> ContextCompat.getColor(binding.root.context, R.color.on_primary)
                })


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
                return oldItem == newItem
            }
        }
    }
}