package com.example.cooksmart3

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color

class ProfileView : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser

    private lateinit var profileImageView: ImageView
    private lateinit var nameText: TextView
    private lateinit var dietText: TextView
    private lateinit var allergyLayout: LinearLayout
    private lateinit var editProfileButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_view)

        //UI
        profileImageView = findViewById(R.id.profileImage)
        nameText = findViewById(R.id.usernameTextView)
        dietText = findViewById(R.id.dietTextView)
        allergyLayout = findViewById(R.id.allergyListLayout)
        editProfileButton = findViewById(R.id.editProfileButton)

        //Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Profile"

        //Load user profile
        loadUserProfile()


        findViewById<Button>(R.id.backToMainButton).setOnClickListener {
            startActivity(Intent(this, MainView::class.java))
        }

        editProfileButton.setOnClickListener {
            startActivity(Intent(this, ProfileSetUp::class.java))
        }
    }

    private fun loadUserProfile() {
        user?.uid?.let { uid ->
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    val name = document.getString("name") ?: "User"
                    val diet = document.getString("diet") ?: "Not specified"
                    val allergies = document.get("allergies") as? List<*> ?: emptyList<Any>()
                    val profileImageUrl = document.getString("profileImageUrl")

                    //Update UI with profile data
                    nameText.text = name

                    //Format diet text for display
                    val formattedDiet = when (diet) {
                        "omnivore" -> "Omnivore (Meat Eater)"
                        "vegetarian" -> "Vegetarian"
                        "vegan" -> "Vegan"
                        "pescatarian" -> "Pescatarian"
                        else -> "Not specified"
                    }
                    dietText.text = formattedDiet

                    //Display allergies as chips
                    allergyLayout.removeAllViews()
                    if (allergies.isEmpty()) {
                        val noAllergiesText = TextView(this).apply {
                            text = "No allergies specified"
                            setPadding(16, 8, 16, 8)
                        }
                        allergyLayout.addView(noAllergiesText)
                    } else {
                        val flowLayout = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        for (allergy in allergies) {
                            val chip = TextView(this).apply {
                                text = allergy.toString()
                                setPadding(16, 8, 16, 8)
                                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                setBackgroundResource(R.drawable.allergy_chip_background)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 0, 8, 8)
                                }
                            }
                            flowLayout.addView(chip)
                        }
                        allergyLayout.addView(flowLayout)
                    }

                    //Load profile image
                    val profileImageBase64 = document.getString("profileImageBase64")
                    if (!profileImageBase64.isNullOrEmpty()) {
                        try {
                            //Convert base64 string back to bitmap
                            val imageBytes = Base64.decode(profileImageBase64, Base64.DEFAULT)
                            val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            val circularBitmap = getCircularBitmap(imageBitmap)
                            profileImageView.setImageBitmap(circularBitmap)
                        } catch (e: Exception) {
                            Log.e("ProfileView", "Error loading image", e)
                            profileImageView.setImageResource(R.drawable.baseline_account_circle_24)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    //Create menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val size = width.coerceAtMost(height)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }


        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        canvas.drawOval(rectF, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        val srcRect = if (width >= height) {
            val left = (width - height) / 2
            Rect(left, 0, left + height, height)
        } else {
            val top = (height - width) / 2
            Rect(0, top, width, top + width)
        }

        canvas.drawBitmap(bitmap, srcRect, rect, paint)

        return output
    }

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