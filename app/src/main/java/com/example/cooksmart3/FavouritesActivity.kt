package com.example.cooksmart3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class FavouritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: FrameLayout
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var adapter: RecipeAdapter
    private val recipeList = mutableListOf<Recipe>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourites_activity)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Favorite Recipes"

        // Initialize views
        recyclerView = findViewById(R.id.favoritesRecyclerView)
        loadingContainer = findViewById(R.id.loadingContainer)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)

        // Setup recycler view
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecipeAdapter(recipeList)
        recyclerView.adapter = adapter

        // Setup "Browse Recipes" button
        findViewById<Button>(R.id.browseRecipesButton).setOnClickListener {
            startActivity(Intent(this, MainView::class.java))
            finish()
        }

        // Load favorite recipes
        loadFavoriteRecipes()
    }

    private fun loadFavoriteRecipes() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState()
            return
        }

        showLoading(true)

        // Get user's favorite recipe IDs
        db.collection("users").document(currentUser.uid)
            .collection("favorites")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { favoritesDocs ->
                if (favoritesDocs.isEmpty) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                // Create a list of recipe IDs to fetch
                val recipeIds = favoritesDocs.documents.map { it.id }

                // If there are no favorites, show empty state
                if (recipeIds.isEmpty()) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                // Load recipe details for each favorite
                loadRecipeDetails(recipeIds)
            }
            .addOnFailureListener { e ->
                Log.e("FavoritesDebug", "Error loading favorites", e)
                showEmptyState()
            }
    }

    private fun loadRecipeDetails(recipeIds: List<String>) {
        // Clear existing recipes
        recipeList.clear()

        // Counter to track when all recipes are loaded
        var loadedCount = 0

        for (recipeId in recipeIds) {
            db.collection("recipes").document(recipeId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        try {
                            val title = document.getString("title") ?: "Untitled"
                            val directions = parseStringListField(document["directions"])
                            val ingredients = parseStringListField(document["ingredients"])
                            val ner = parseStringListField(document["NER"])
                            val link = document.getString("link") ?: ""
                            val site = document.getString("site") ?: ""

                            val recipe = Recipe(
                                title = title,
                                directions = directions,
                                ingredients = ingredients,
                                NER = ner,
                                link = link,
                                site = site,
                                id = document.id,
                                isFavorite = true
                            )

                            recipeList.add(recipe)
                        } catch (e: Exception) {
                            Log.e("FavoritesDebug", "Error parsing recipe", e)
                        }
                    }

                    // Increment counter and check if all recipes are loaded
                    loadedCount++
                    if (loadedCount >= recipeIds.size) {
                        finishLoading()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FavoritesDebug", "Error loading recipe $recipeId", e)

                    // Increment counter and check if all recipes are loaded
                    loadedCount++
                    if (loadedCount >= recipeIds.size) {
                        finishLoading()
                    }
                }
        }
    }

    private fun finishLoading() {
        adapter.notifyDataSetChanged()

        if (recipeList.isEmpty()) {
            showEmptyState()
        } else {
            showLoading(false)
            emptyStateContainer.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            loadingContainer.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyStateContainer.visibility = View.GONE
        } else {
            loadingContainer.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showEmptyState() {
        loadingContainer.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyStateContainer.visibility = View.VISIBLE
    }

    // Parse string list fields from various formats (copied from MainView)
    private fun parseStringListField(field: Any?): List<String> {
        return when (field) {
            is List<*> -> field.filterIsInstance<String>()
            is String -> {
                if (field.startsWith("[") && field.endsWith("]")) {
                    field.removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }
                } else {
                    listOf(field)
                }
            }
            else -> emptyList()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}