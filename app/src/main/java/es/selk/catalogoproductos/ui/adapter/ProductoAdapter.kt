package es.selk.catalogoproductos.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
        holder.bind(getItem(position))
    }

    class ProductoViewHolder(
        private val binding: ItemProductoBinding,
        private val onItemClick: (ProductoEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val priceFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))

        fun bind(producto: ProductoEntity) {
            binding.apply {
                tvId.text = producto.id_producto
                tvDescripcion.text = producto.descripcion
                tvPrecio.text = priceFormat.format(producto.precio_actual)
                tvStock.text = "Stock: ${producto.stock_actual}"

                if (producto.descuento > 0) {
                    tvDescuento.visibility = android.view.View.VISIBLE
                    tvDescuento.text = "Descuento: ${producto.descuento}%"
                } else {
                    tvDescuento.visibility = android.view.View.GONE
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
                return oldItem == newItem
            }
        }
    }
}