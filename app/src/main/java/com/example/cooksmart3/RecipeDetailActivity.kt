package com.example.cooksmart3

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class RecipeDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var recipeIngredients = arrayListOf<String>()
    private var kitchenIngredients = listOf<String>()
    private var missingIngredients = listOf<String>()
    private var matchPercentage = 0f

    private lateinit var favoriteButton: FloatingActionButton
    private var recipeId: String = ""
    private var isFavorite: Boolean = false
    private var recipeTitle: String = ""

    private lateinit var missingIngredientsContainer: CardView
    private lateinit var missingIngredientsList: TextView
    private lateinit var mapLoadingOverlay: FrameLayout
    private lateinit var detailMatchPercentage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)
        val bundle = intent.extras
        Log.d("RecipeIntentDebug", "Bundle: $bundle")

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        val titleTextView = findViewById<TextView>(R.id.detailRecipeTitle)
        val ingredientsTextView = findViewById<TextView>(R.id.detailRecipeIngredients)
        val directionsTextView = findViewById<TextView>(R.id.detailRecipeDirections)
        val linkTextView = findViewById<TextView>(R.id.detailRecipeLink)
        val imageView = findViewById<ImageView>(R.id.detailRecipeImage)
        detailMatchPercentage = findViewById(R.id.detailMatchPercentage)
        missingIngredientsContainer = findViewById(R.id.missingIngredientsContainer)
        missingIngredientsList = findViewById(R.id.missingIngredientsList)
        mapLoadingOverlay = findViewById(R.id.mapLoadingOverlay)
        favoriteButton = findViewById(R.id.favoriteButton)


        recipeTitle = intent.getStringExtra("RECIPE_TITLE") ?: "Recipe"
        recipeIngredients = intent.getStringArrayListExtra("RECIPE_INGREDIENTS") ?: arrayListOf()
        val directions = intent.getStringArrayListExtra("RECIPE_DIRECTIONS") ?: arrayListOf()
        val link = intent.getStringExtra("RECIPE_LINK") ?: ""
        recipeId = intent.getStringExtra("RECIPE_ID") ?: ""

        titleTextView.text = recipeTitle


        val formattedIngredients = recipeIngredients.joinToString("\n") { "• $it" }
        ingredientsTextView.text = formattedIngredients


        val formattedDirections = directions.mapIndexed { index, step ->
            "${index + 1}. $step"
        }.joinToString("\n\n")
        directionsTextView.text = formattedDirections


        if (link.isNotEmpty()) {
            linkTextView.text = "Source: $link"
        } else {
            linkTextView.text = ""
        }

        // Set image - temporary
        imageView.setImageResource(R.drawable.food)


        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.storesMap) as SupportMapFragment
        mapFragment.getMapAsync(this)


        loadKitchenIngredients()

        checkIfFavorite()
        setupFavoriteButton()
    }
    private fun checkIfFavorite() {
        val user = auth.currentUser
        if (user != null && recipeId.isNotEmpty()) {
            db.collection("users").document(user.uid)
                .collection("favorites").document(recipeId)
                .get()
                .addOnSuccessListener { document ->
                    isFavorite = document.exists()
                    updateFavoriteButtonIcon()
                }
        }
    }
    private fun setupFavoriteButton() {
        favoriteButton.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Please log in to save favorites", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (recipeId.isEmpty()) {

                findRecipeIdByTitle(recipeTitle) { foundId ->
                    if (foundId.isNotEmpty()) {
                        recipeId = foundId
                        toggleFavoriteStatus()
                    } else {
                        Toast.makeText(this, "Couldn't identify recipe to favorite", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                toggleFavoriteStatus()
            }
        }
    }
    private fun findRecipeIdByTitle(title: String, callback: (String) -> Unit) {
        db.collection("recipes")
            .whereEqualTo("title", title)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    callback(result.documents[0].id)
                } else {
                    callback("")
                }
            }
            .addOnFailureListener {
                callback("")
            }
    }

    private fun toggleFavoriteStatus() {
        val user = auth.currentUser ?: return
        val userRef = db.collection("users").document(user.uid)
        val favoriteRef = userRef.collection("favorites").document(recipeId)

        if (isFavorite) {
            //Remove from favorites
            favoriteRef.delete()
                .addOnSuccessListener {
                    isFavorite = false
                    updateFavoriteButtonIcon()
                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to remove: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            //Add to favorites
            val favoriteData = hashMapOf(
                "title" to recipeTitle,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            favoriteRef.set(favoriteData)
                .addOnSuccessListener {
                    isFavorite = true
                    updateFavoriteButtonIcon()
                    Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to add: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    private fun updateFavoriteButtonIcon() {
        if (isFavorite) {
            favoriteButton.setImageResource(R.drawable.ic_favorite)
        } else {
            favoriteButton.setImageResource(R.drawable.ic_favorite_border)
        }
    }


    private fun loadKitchenIngredients() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            detailMatchPercentage.visibility = View.GONE
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                kitchenIngredients = userDoc.get("kitchenIngredients") as? List<String> ?: emptyList()

                // Find missing ingredients
                findMissingIngredients()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RecipeDetailActivity,
                        "Error loading kitchen ingredients: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun findMissingIngredients() {
        val missing = mutableListOf<String>()
        var matchCount = 0

        for (recipeIngredient in recipeIngredients) {
            var found = false

            for (kitchenIngredient in kitchenIngredients) {
                if (recipeIngredient.contains(kitchenIngredient, ignoreCase = true)) {
                    matchCount++
                    found = true
                    break
                }
            }

            if (!found) {
                missing.add(recipeIngredient)
            }
        }

        missingIngredients = missing
        matchPercentage = if (recipeIngredients.isNotEmpty()) {
            (recipeIngredients.size - missing.size).toFloat() / recipeIngredients.size
        } else {
            0f
        }

        withContext(Dispatchers.Main) {
            updateUI()
        }
    }

    private fun updateUI() {
        //Update match percentage text
        detailMatchPercentage.text = "${(matchPercentage * 100).toInt()}% match with your kitchen"

        if (missingIngredients.isNotEmpty() && matchPercentage < 1.0f) {
            //Show missing ingredients section
            missingIngredientsContainer.visibility = View.VISIBLE


            val formattedMissing = missingIngredients.joinToString("\n") { "• $it" }
            missingIngredientsList.text = formattedMissing

            if (::mMap.isInitialized) {
                findNearbyStores()
            }
        } else {
            missingIngredientsContainer.visibility = View.GONE
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (missingIngredients.isNotEmpty() && matchPercentage < 1.0f) {
            findNearbyStores()
        }
    }

    private fun findNearbyStores() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        //Show loading
        mapLoadingOverlay.visibility = View.VISIBLE

        //Get current location
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)


                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 13f))


                mMap.addMarker(
                    MarkerOptions()
                        .position(currentLatLng)
                        .title("Your Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )


                searchNearbyStores(currentLatLng)
            } ?: run {
                Toast.makeText(
                    this,
                    "Unable to get your location. Please try again later.",
                    Toast.LENGTH_SHORT
                ).show()
                mapLoadingOverlay.visibility = View.GONE
            }
        }
    }

    private fun searchNearbyStores(location: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiKey = "AIzaSyD6vT5oaGfdd5a9I71TMFb2JYCTuMYqXjs"
                val radius = 5000 //5km radius
                val type = "supermarket"

                val url = URL("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${location.latitude},${location.longitude}&radius=$radius&type=$type&key=$apiKey")

                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)

                if (jsonResponse.getString("status") == "OK") {
                    val results = jsonResponse.getJSONArray("results")
                    val markers = mutableListOf<MarkerOptions>()

                    for (i in 0 until results.length()) {
                        val place = results.getJSONObject(i)
                        val name = place.getString("name")
                        val vicinity = place.getString("vicinity")
                        val geometry = place.getJSONObject("geometry")
                        val locationObj = geometry.getJSONObject("location")
                        val lat = locationObj.getDouble("lat")
                        val lng = locationObj.getDouble("lng")

                        markers.add(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .title(name)
                                .snippet(vicinity)
                        )
                    }

                    withContext(Dispatchers.Main) {
                        // Add all markers to the map
                        markers.forEach { mMap.addMarker(it) }
                        mapLoadingOverlay.visibility = View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@RecipeDetailActivity,
                            "Could not find nearby stores",
                            Toast.LENGTH_SHORT
                        ).show()
                        mapLoadingOverlay.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RecipeDetailActivity,
                        "Error finding stores: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    mapLoadingOverlay.visibility = View.GONE
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                findNearbyStores()
            } else {
                //Permission denied
                Toast.makeText(
                    this,
                    "Location permission is required to show nearby stores",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}