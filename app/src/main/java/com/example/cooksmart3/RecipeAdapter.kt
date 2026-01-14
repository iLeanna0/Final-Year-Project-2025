package com.example.cooksmart3

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(private val recipeList: List<Recipe>) :
    RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.recipeTitle)
        val descText: TextView = itemView.findViewById(R.id.recipeDescription)
        val imageView: ImageView = itemView.findViewById(R.id.recipeImage)
        val matchTextView: TextView = itemView.findViewById(R.id.matchPercentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipeList[position]
        holder.titleText.text = recipe.title

        //Create a better description
        val description = if (recipe.ingredients.isNotEmpty()) {
            "Ingredients: " + recipe.ingredients.take(3).joinToString(", ") +
                    if (recipe.ingredients.size > 3) "..." else ""
        } else if (recipe.directions.isNotEmpty()) {
            recipe.directions.firstOrNull() ?: "No description"
        } else {
            "No description available"
        }

        holder.descText.text = description
        holder.imageView.setImageResource(R.drawable.food) //temporary - remember to delete???

        holder.matchTextView.text = "${(recipe.matchPercentage * 100).toInt()}% match"
        holder.matchTextView.visibility = if (recipe.matchPercentage > 0) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, RecipeDetailActivity::class.java).apply {
                putExtra("RECIPE_TITLE", recipe.title)
                putStringArrayListExtra("RECIPE_INGREDIENTS", ArrayList(recipe.ingredients))
                putStringArrayListExtra("RECIPE_DIRECTIONS", ArrayList(recipe.directions))
                putExtra("RECIPE_LINK", recipe.link)
                putExtra("RECIPE_MATCH_PERCENTAGE", recipe.matchPercentage)
                putExtra("RECIPE_ID", recipe.id)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = recipeList.size
}