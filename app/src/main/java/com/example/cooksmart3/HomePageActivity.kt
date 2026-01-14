package com.example.cooksmart3

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomePageActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "CookSmart"

        // Setup navigation buttons
        setupNavigationButtons()
    }

    private fun setupNavigationButtons() {
        // Recipes button
        findViewById<LinearLayout>(R.id.recipesButton).setOnClickListener {
            startActivity(Intent(this, MainView::class.java))
        }

        // Kitchen button
        findViewById<LinearLayout>(R.id.kitchenButton).setOnClickListener {
            startActivity(Intent(this, MyKitchenActivity::class.java))
        }

        // Profile button
        findViewById<LinearLayout>(R.id.profileButton).setOnClickListener {
            navigateToProfile()
        }

        // Favorites button
        findViewById<LinearLayout>(R.id.favoritesButton).setOnClickListener {
            startActivity(Intent(this, FavouritesActivity::class.java))
        }
    }

    private fun navigateToProfile() {
        val user = auth.currentUser
        if (user != null) {
            // Check if user has a profile
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    val intent = if (document.exists()) {
                        // If user has a profile, go to profile view
                        Intent(this, ProfileView::class.java)
                    } else {
                        // If not, user needs to create a profile
                        Intent(this, ProfileSetUp::class.java)
                    }
                    startActivity(intent)
                }
                .addOnFailureListener {
                    // On error, just try to go to profile setup
                    startActivity(Intent(this, ProfileSetUp::class.java))
                }
        } else {
            // User not logged in
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
        }
    }

    // Create menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // Handle menu selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                // Already on home page, do nothing
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
}