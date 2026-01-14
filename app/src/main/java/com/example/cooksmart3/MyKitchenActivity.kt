package com.example.cooksmart3

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyKitchenActivity : AppCompatActivity() {

    private lateinit var ingredientEditText: EditText
    private lateinit var addIngredientButton: Button
    private lateinit var findRecipesButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val ingredients = mutableListOf<String>()
    private lateinit var adapter: IngredientAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_kitchen)

        //Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Kitchen"

        //UI components
        ingredientEditText = findViewById(R.id.ingredientEditText)
        addIngredientButton = findViewById(R.id.addIngredientButton)
        findRecipesButton = findViewById(R.id.findRecipesButton)
        recyclerView = findViewById(R.id.ingredientsRecyclerView)
        progressBar = findViewById(R.id.progressBar)

        //RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = IngredientAdapter(ingredients) { position ->
            adapter.removeIngredient(position)
        }
        recyclerView.adapter = adapter

        //Load existing ingredients
        loadIngredients()

        addIngredientButton.setOnClickListener {
            addIngredient()
        }

        findRecipesButton.setOnClickListener {
            saveIngredients()
            Toast.makeText(this, "Ingredients saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadIngredients() {
        progressBar.visibility = View.VISIBLE
        val currentUser = auth.currentUser

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    progressBar.visibility = View.GONE
                    if (document.exists()) {
                        val kitchenIngredients = document.get("kitchenIngredients") as? List<String>
                        if (!kitchenIngredients.isNullOrEmpty()) {
                            ingredients.clear()
                            ingredients.addAll(kitchenIngredients)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error loading ingredients: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Please log in to view your kitchen", Toast.LENGTH_SHORT).show()
            //Redirect to login
        }
    }

    private fun addIngredient() {
        val ingredientName = ingredientEditText.text.toString().trim()
        if (ingredientName.isNotEmpty()) {
            //Check if ingredient already exists
            if (ingredients.contains(ingredientName)) {
                Toast.makeText(this, "This ingredient is already in your kitchen", Toast.LENGTH_SHORT).show()
            } else {
                adapter.addIngredient(ingredientName)
                ingredientEditText.text.clear()
            }
        } else {
            Toast.makeText(this, "Please enter an ingredient name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveIngredients() {
        progressBar.visibility = View.VISIBLE
        val currentUser = auth.currentUser

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .update("kitchenIngredients", ingredients)
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Kitchen ingredients saved!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error saving ingredients: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Please log in to save your kitchen", Toast.LENGTH_SHORT).show()
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
                startActivity(Intent(this, MainView::class.java))
                true
            }
            R.id.action_profile -> {
                navigateToProfile()
                true
            }
            R.id.action_kitchen -> {
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
}