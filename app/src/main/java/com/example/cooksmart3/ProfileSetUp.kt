package com.example.cooksmart3

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class ProfileSetUp : AppCompatActivity() {

    private lateinit var nameEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var profileImageView: ImageView
    private lateinit var choosePhotoButton: Button
    private lateinit var takePictureButton: Button
    private lateinit var dietRadioGroup: RadioGroup

    private lateinit var checkNuts: CheckBox
    private lateinit var checkDairy: CheckBox
    private lateinit var checkGluten: CheckBox
    private lateinit var checkShellfish: CheckBox
    private lateinit var checkEggs: CheckBox
    private lateinit var checkFish: CheckBox
    private lateinit var checkSoy: CheckBox
    private lateinit var checkWheat: CheckBox

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private var selectedImageUri: Uri? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private const val TAKE_PHOTO_REQUEST = 2
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_set_up)

        nameEditText = findViewById(R.id.nameEditText)
        saveButton = findViewById(R.id.saveProfileButton)
        profileImageView = findViewById(R.id.profileImageView)
        choosePhotoButton = findViewById(R.id.choosePhotoButton)
        takePictureButton = findViewById(R.id.takePictureButton)
        dietRadioGroup = findViewById(R.id.dietRadioGroup)

        checkNuts = findViewById(R.id.allergyNuts)
        checkDairy = findViewById(R.id.allergyDairy)
        checkGluten = findViewById(R.id.allergyGluten)
        checkShellfish = findViewById(R.id.allergyShellfish)
        checkEggs = findViewById(R.id.allergyEggs)
        checkFish = findViewById(R.id.allergyFish)
        checkSoy = findViewById(R.id.allergySoy)
        checkWheat = findViewById(R.id.allergyWheat)

        //Action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Setup Your Profile"


        loadExistingProfile()

        choosePhotoButton.setOnClickListener {
            openGallery()
        }
        takePictureButton.setOnClickListener {
            openCamera()
        }

        saveButton.setOnClickListener {
            saveProfileToFirestore()
        }
    }

    private fun loadExistingProfile() {
        user?.uid?.let { uid ->
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        nameEditText.setText(document.getString("name") ?: "")

                        val diet = document.getString("diet") ?: ""
                        when (diet) {
                            "omnivore" -> dietRadioGroup.check(R.id.dietOmnivore)
                            "vegetarian" -> dietRadioGroup.check(R.id.dietVegetarian)
                            "vegan" -> dietRadioGroup.check(R.id.dietVegan)
                            "pescatarian" -> dietRadioGroup.check(R.id.dietPescatarian)
                        }

                        val allergies = document.get("allergies") as? List<String> ?: emptyList()
                        checkNuts.isChecked = allergies.contains("nuts")
                        checkDairy.isChecked = allergies.contains("dairy")
                        checkGluten.isChecked = allergies.contains("gluten")
                        checkShellfish.isChecked = allergies.contains("shellfish")
                        checkEggs.isChecked = allergies.contains("eggs")
                        checkFish.isChecked = allergies.contains("fish")
                        checkSoy.isChecked = allergies.contains("soy")
                        checkWheat.isChecked = allergies.contains("wheat")

                        //Load profile image if available
                        val profileImageUrl = document.getString("profileImageUrl")
                        if (!profileImageUrl.isNullOrEmpty()) {

                            //Placeholder icon
                            profileImageView.setImageResource(R.drawable.baseline_account_circle_24)
                        }
                    }
                }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(intent, TAKE_PHOTO_REQUEST)
        } catch (e: Exception) {
            Log.e("ProfileSetUp", "Error starting camera: ${e.message}", e)
            Toast.makeText(this, "Error starting camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    selectedImageUri = data?.data
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, selectedImageUri!!))
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                        }
                        val circularBitmap = getCircularBitmap(bitmap)
                        profileImageView.setImageBitmap(circularBitmap)
                    } catch (e: Exception) {
                        Log.e("ProfileSetUp", "Error loading image", e)
                        profileImageView.setImageURI(selectedImageUri)
                    }
                }
                TAKE_PHOTO_REQUEST -> {
                    try {
                        if (data != null && data.extras != null) {
                            val imageBitmap = data.extras?.get("data") as? Bitmap
                            if (imageBitmap != null) {
                                Log.d("ProfileSetUp", "Received camera image, size: ${imageBitmap.width}x${imageBitmap.height}")

                                val circularBitmap = getCircularBitmap(imageBitmap)
                                profileImageView.setImageBitmap(circularBitmap)

                                val bytes = ByteArrayOutputStream()
                                circularBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
                                val path = MediaStore.Images.Media.insertImage(
                                    contentResolver, circularBitmap, "Profile Picture", null
                                )
                                selectedImageUri = Uri.parse(path)
                                Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e("ProfileSetUp", "Camera data extras doesn't contain bitmap")
                                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("ProfileSetUp", "No data received from camera")
                            Toast.makeText(this, "No image data received from camera", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ProfileSetUp", "Error processing camera image: ${e.message}", e)
                        Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            when (requestCode) {
                TAKE_PHOTO_REQUEST -> {
                    Log.d("ProfileSetUp", "Camera capture cancelled")
                }
            }
        }
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

        //Create a square bitmap
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        //Draw a circle
        canvas.drawOval(rectF, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        // Get the center part of the image if not square
        val srcRect = if (width >= height) {
            val left = (width - height) / 2
            Rect(left, 0, left + height, height)
        } else {
            val top = (height - width) / 2
            Rect(0, top, width, top + width)
        }

        //Draw the bitmap through the circular mask
        canvas.drawBitmap(bitmap, srcRect, rect, paint)

        return output
    }

    private fun saveProfileToFirestore() {
        val name = nameEditText.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
            return
        }

        //Get selected diet
        val selectedDietId = dietRadioGroup.checkedRadioButtonId
        val diet = when (selectedDietId) {
            R.id.dietVegetarian -> "vegetarian"
            R.id.dietVegan -> "vegan"
            R.id.dietPescatarian -> "pescatarian"
            else -> "omnivore"
        }

        //Get allergies
        val allergies = mutableListOf<String>()
        if (checkNuts.isChecked) allergies.add("nuts")
        if (checkDairy.isChecked) allergies.add("dairy")
        if (checkGluten.isChecked) allergies.add("gluten")
        if (checkShellfish.isChecked) allergies.add("shellfish")
        if (checkEggs.isChecked) allergies.add("eggs")
        if (checkFish.isChecked) allergies.add("fish")
        if (checkSoy.isChecked) allergies.add("soy")
        if (checkWheat.isChecked) allergies.add("wheat")

        //Create profile data
        val profile = hashMapOf<String, Any>(
            "name" to name,
            "email" to (user?.email ?: ""),
            "diet" to diet,
            "allergies" to allergies
        )

        //Save profile data
        user?.uid?.let { uid ->
            //Save image
            if (selectedImageUri != null) {
                uploadProfileImage(uid, profile)
            } else {
                saveProfileData(uid, profile)
            }
        }
    }

    private fun uploadProfileImage(uid: String, profile: HashMap<String, Any>) {
        selectedImageUri?.let { uri ->

            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            progressBar.visibility = View.VISIBLE

            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }

                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true)

                //Convert to Base64
                val baos = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                val imageBytes = baos.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                progressBar.visibility = View.GONE

                //Add the base64 image string to profile data
                profile["profileImageBase64"] = base64Image

                //Save profile data
                saveProfileData(uid, profile)

            } catch (e: Exception) {
                Log.e("ImageUpload", "Exception during encoding", e)
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_LONG).show()
                saveProfileData(uid, profile)
            }
        }
    }

    private fun saveProfileData(uid: String, profile: HashMap<String, Any>) {
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val kitchenIngredients = document.get("kitchenIngredients") as? List<String>
                    if (!kitchenIngredients.isNullOrEmpty()) {
                        profile["kitchenIngredients"] = kitchenIngredients
                    }
                }

                db.collection("users").document(uid)
                    .set(profile)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, ProfileView::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error saving profile: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error retrieving existing data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}