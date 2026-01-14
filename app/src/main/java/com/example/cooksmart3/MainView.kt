package com.example.cooksmart3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainView : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var recipeList: MutableList<Recipe>
    private lateinit var adapter: RecipeAdapter
    private lateinit var loadingContainer: FrameLayout
    private lateinit var searchView: SearchView
    private var allRecipes: MutableList<Recipe> = mutableListOf()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_view)

        //Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Recipes"

        //Start loading
        loadingContainer = findViewById(R.id.loadingContainer)

        //Setup RecyclerView
        recyclerView = findViewById(R.id.recipeRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        recipeList = mutableListOf()
        adapter = RecipeAdapter(recipeList)
        recyclerView.adapter = adapter

        //Setup SearchView
        searchView = findViewById(R.id.searchView)
        setupSearchView()

        //Show loading
        showLoading(true)

        Log.d("RecipeDebug", "Starting to load recipes...")

        loadRecipes()
        checkUserProfile()
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { filterRecipes(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { filterRecipes(it) }
                return true
            }
        })
    }

    private fun filterRecipes(query: String) {
        val filteredList = if (query.isEmpty()) {
            //If query is empty, show all recipes
            allRecipes.toMutableList()
        } else {
            //Filter recipes by title/ingredients
            allRecipes.filter { recipe ->
                recipe.title.contains(query, ignoreCase = true) ||
                        recipe.ingredients.any { it.contains(query, ignoreCase = true) }
            }.toMutableList()
        }

        //Update the adapter
        recipeList.clear()
        recipeList.addAll(filteredList)
        adapter.notifyDataSetChanged()

        //If no recipes match the search
        if (recipeList.isEmpty() && query.isNotEmpty()) {
            Toast.makeText(this, "No recipes found for '$query'", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            loadingContainer.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            loadingContainer.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun loadRecipes() {
        //Get user's dietary preferences
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val userDiet = userDoc.getString("diet") ?: "omnivore"
                val userAllergies = userDoc.get("allergies") as? List<String> ?: emptyList()
                val kitchenIngredients = userDoc.get("kitchenIngredients") as? List<String> ?: emptyList()

                Log.d("RecipeDebug", "User diet: $userDiet, Allergies: $userAllergies")

                //Fetch and filter recipes
                db.collection("recipes")
                    .get()
                    .addOnSuccessListener { result ->
                        Log.d("RecipeDebug", "Number of docs: ${result.size()}")
                        allRecipes.clear()
                        recipeList.clear()

                        if (result.isEmpty) {
                            Toast.makeText(this, "No recipes found.", Toast.LENGTH_SHORT).show()
                            //Add a recipe as a failsafe
                            val testRecipe = Recipe("Test Recipe", listOf("Step 1: Do something", "Step 2: Done!!"))
                            allRecipes.add(testRecipe)
                            recipeList.add(testRecipe)
                        } else {
                            for (document in result) {
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
                                        id = document.id
                                    )

                                    //Check if recipe matches user's dietary preferences
                                    if (isRecipeCompatibleWithDiet(recipe, userDiet, userAllergies)) {
                                        recipe.matchPercentage = calculateRecipeMatch(recipe, kitchenIngredients)
                                        allRecipes.add(recipe)
                                        Log.d("RecipeDebug", "Added compatible recipe: $title")
                                    } else {
                                        Log.d("RecipeDebug", "Filtered out incompatible recipe: $title")
                                    }

                                } catch (e: Exception) {
                                    Log.e("RecipeDebug", "Error parsing recipe", e)
                                }
                            }

                            //Sort by percentage (highest to lowest)
                            allRecipes.sortByDescending { it.matchPercentage }

                            //Initialise the display list with all the recipes
                            recipeList.addAll(allRecipes)
                        }

                        adapter.notifyDataSetChanged()

                        //Hide loading
                        showLoading(false)

                        //Error handling: Show message if there are no compatible recipes
                        if (recipeList.isEmpty()) {
                            Toast.makeText(
                                this,
                                "No recipes match your dietary preferences",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        //Hide loading
                        showLoading(false)

                        Toast.makeText(this, "Error loading recipes: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("RecipeDebug", "Failed to load recipes", e)
                    }
            }
            .addOnFailureListener { e ->
                //Hide loading
                showLoading(false)

                Toast.makeText(this, "Error loading user profile: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("RecipeDebug", "Failed to load user profile", e)

                //Load all recipes without filtering if user profile can't be loaded
                loadAllRecipes()
            }
    }

    //Function to check if recipe matches dietary preferences
    internal fun isRecipeCompatibleWithDiet(recipe: Recipe, diet: String, allergies: List<String>): Boolean {
        val allergyGroups = mapOf(
            "nuts" to listOf("nut", "nuts", "almond", "walnut", "cashew", "pistachio", "pecan", "hazelnut",
                "macadamia", "peanut", "peanuts", "groundnut", "pine nut"),
            "dairy" to listOf("milk", "cheese", "butter", "cream", "yogurt", "whey", "casein", "lactose", "dairy"),
            "gluten" to listOf("gluten", "wheat", "barley", "rye", "malt", "pasta", "bread"),
            "fish" to listOf("fish", "salmon", "tuna", "cod", "tilapia", "bass", "trout", "sardine", "anchovy", "halibut"),
            "shellfish" to listOf("shellfish", "shrimp", "crab", "lobster", "crawfish", "prawn", "clam", "mussel", "oyster", "scallop"),
            "eggs" to listOf("egg", "eggs", "mayonnaise", "albumen", "meringue", "eggnog"),
            "soy" to listOf("soy", "soya", "tofu", "edamame", "miso", "tempeh", "soybean"),
            "wheat" to listOf("wheat", "bread", "flour", "pasta", "cereal", "bran", "couscous", "semolina")
        )

        //If user has specific allergies listed
        for (allergy in allergies) {
            val allergyLower = allergy.lowercase().trim()

            //Check if this is a known allergy group
            if (allergyGroups.containsKey(allergyLower)) {
                //Check for all related ingredients
                val allergyTerms = allergyGroups[allergyLower]!!
                for (term in allergyTerms) {
                    if (recipe.ingredients.any { it.contains(term, ignoreCase = true) } ||
                        recipe.NER.any { it.contains(term, ignoreCase = true) }) {
                        return false
                    }
                }
            } else {
                if (recipe.ingredients.any { it.contains(allergyLower, ignoreCase = true) } ||
                    recipe.NER.any { it.contains(allergyLower, ignoreCase = true) }) {
                    return false
                }
            }
        }

        //Check diet restrictions
        when (diet) {
            "vegan" -> {
                val nonVeganIngredients = listOf("meat", "chicken", "beef", "pork", "fish", "seafood",
                    "lamb", "turkey", "duck", "egg", "eggs", "milk", "cheese", "butter", "cream",
                    "yogurt", "honey", "mayonnaise")

                if (containsAny(recipe, nonVeganIngredients)) {
                    return false
                }
            }
            "vegetarian" -> {
                val nonVegetarianIngredients = listOf("meat", "chicken", "beef", "pork", "fish", "seafood",
                    "lamb", "turkey", "duck", "bacon", "prosciutto", "ham", "salami")

                if (containsAny(recipe, nonVegetarianIngredients)) {
                    return false
                }
            }
            "pescatarian" -> {
                val nonPescatarianIngredients = listOf("meat", "chicken", "beef", "pork",
                    "lamb", "turkey", "duck", "bacon", "prosciutto", "ham", "salami")

                if (containsAny(recipe, nonPescatarianIngredients)) {
                    return false
                }
            }
        }

        return true
    }

    //Check if recipe contains any of the listed ingredients
    private fun containsAny(recipe: Recipe, ingredients: List<String>): Boolean {
        for (ingredient in ingredients) {
            if (recipe.ingredients.any { it.contains(ingredient, ignoreCase = true) } ||
                recipe.NER.any { it.contains(ingredient, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    //Fallback to load all recipes without filtering
    private fun loadAllRecipes() {
        //Show loading again
        showLoading(true)
        db.collection("recipes")
            .get()
            .addOnSuccessListener { result ->
                allRecipes.clear()
                recipeList.clear()

                for (document in result) {
                    try {
                        val title = document.getString("title") ?: "Untitled"
                        val directions = parseStringListField(document["directions"])
                        val ingredients = parseStringListField(document["ingredients"])
                        val ner = parseStringListField(document["NER"])
                        val link = document.getString("link") ?: ""
                        val site = document.getString("site") ?: ""

                        val recipe = Recipe(title, directions, ingredients, ner, link, site)
                        allRecipes.add(recipe)
                        recipeList.add(recipe)
                    } catch (e: Exception) {
                        Log.e("RecipeDebug", "Error parsing recipe", e)
                    }
                }

                adapter.notifyDataSetChanged()
                //Hide loading
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)

                Toast.makeText(this, "Error loading recipes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

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

    private fun checkUserProfile() {
        val user = auth.currentUser
        user?.uid?.let { uid ->
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        // User doesn't have a profile yet, prompt them to create one
                        Toast.makeText(this, "Please set up your profile", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, ProfileSetUp::class.java)
                        startActivity(intent)
                    }
                }
        }
    }

    //Create menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    //Handle menu selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_home -> {
                startActivity(Intent(this, HomePageActivity::class.java))
                true
            }
            R.id.action_recipes -> {
                true
            }
            R.id.action_profile -> {
                navigateToProfile()
                true
            }
            R.id.action_kitchen -> {
                startActivity(Intent(this, MyKitchenActivity::class.java))
                true
            }
            R.id.action_favorites -> {
                startActivity(Intent(this, FavouritesActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateToProfile() {
        val user = auth.currentUser
        if (user != null) {
            //Check if user has a profile
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    val intent = if (document.exists()) {
                        //If user has a profile, go to profile view
                        Intent(this, ProfileView::class.java)
                    } else {
                        //If not, user needs to create a profile
                        Intent(this, ProfileSetUp::class.java)
                    }
                    startActivity(intent)
                }
                .addOnFailureListener {
                    //On error, just try to go to profile setup
                    startActivity(Intent(this, ProfileSetUp::class.java))
                }
        } else {
            //User not logged in
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
        }
    }
    internal fun calculateRecipeMatch(recipe: Recipe, kitchenIngredients: List<String>): Float {
        if (kitchenIngredients.isEmpty()) return 0f

        //Count how many recipe ingredients are in the user's kitchen
        var matchCount = 0
        for (ingredient in recipe.ingredients) {
            for (kitchenIngredient in kitchenIngredients) {
                if (ingredient.contains(kitchenIngredient, ignoreCase = true)) {
                    matchCount++
                    break
                }
            }
        }

        //Calculate the match percentage (how many ingredients the user already has)
        return if (recipe.ingredients.isNotEmpty()) {
            matchCount.toFloat() / recipe.ingredients.size
        } else {
            0f
        }
    }
}