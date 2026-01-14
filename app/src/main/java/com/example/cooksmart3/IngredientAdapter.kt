package com.example.cooksmart3

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class IngredientAdapter(
    private val ingredients: MutableList<String>,
    private val onItemRemoved: (Int) -> Unit
) : RecyclerView.Adapter<IngredientAdapter.IngredientViewHolder>() {

    class IngredientViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ingredientNameTextView: TextView = view.findViewById(R.id.ingredientNameTextView)
        val removeButton: ImageButton = view.findViewById(R.id.removeIngredientButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient, parent, false)
        return IngredientViewHolder(view)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        val ingredient = ingredients[position]
        holder.ingredientNameTextView.text = ingredient

        holder.removeButton.setOnClickListener {
            onItemRemoved(position)
        }
    }

    override fun getItemCount() = ingredients.size

    fun addIngredient(ingredient: String) {
        ingredients.add(ingredient)
        notifyItemInserted(ingredients.size - 1)
    }

    fun removeIngredient(position: Int) {
        if (position >= 0 && position < ingredients.size) {
            ingredients.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}