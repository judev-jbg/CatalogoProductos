package es.selk.catalogoproductos.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import es.selk.catalogoproductos.R

class ProductoSkeletonAdapter(private val itemCount: Int = 10) :
    RecyclerView.Adapter<ProductoSkeletonAdapter.SkeletonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_skeleton, parent, false)
        return SkeletonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = itemCount

    class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            // Iniciar animación shimmer en los elementos
            itemView.findViewById<View>(R.id.skeleton_referencia)
                ?.setBackgroundResource(R.drawable.skeleton_shimmer)
            itemView.findViewById<View>(R.id.skeleton_descripcion)
                ?.setBackgroundResource(R.drawable.skeleton_shimmer)
            itemView.findViewById<View>(R.id.skeleton_descripcion2)
                ?.setBackgroundResource(R.drawable.skeleton_shimmer)
            itemView.findViewById<View>(R.id.skeleton_familia)
                ?.setBackgroundResource(R.drawable.skeleton_shimmer)
            itemView.findViewById<View>(R.id.skeleton_precio)
                ?.setBackgroundResource(R.drawable.skeleton_shimmer)
        }
    }
}