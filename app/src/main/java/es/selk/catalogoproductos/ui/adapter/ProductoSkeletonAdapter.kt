package es.selk.catalogoproductos.ui.adapter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
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
        private val animators = mutableListOf<ValueAnimator>()

        fun bind() {
            val skeletonViews = listOf(
                R.id.skeleton_referencia,
                R.id.skeleton_descripcion,
                R.id.skeleton_descripcion2,
                R.id.skeleton_familia,
                R.id.skeleton_precio
            )

            // Cancelar animaciones anteriores
            animators.forEach { it.cancel() }
            animators.clear()

            skeletonViews.forEach { viewId ->
                itemView.findViewById<View>(viewId)?.let { view ->
                    view.setBackgroundResource(R.drawable.skeleton_shimmer)
                    startShimmerAnimation(view)
                }
            }
        }

        private fun startShimmerAnimation(view: View) {
            val fadeAnimator = ObjectAnimator.ofFloat(view, "alpha", 0.3f, 1f, 0.3f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
            fadeAnimator.start()
            animators.add(fadeAnimator)
        }
    }
}