package com.example.cooksmart3

import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    //UI Views
    private lateinit var InputImageBtn: MaterialButton
    private lateinit var RecognizeTextBtn: MaterialButton
    private lateinit var ImageIv: ImageView
    private lateinit var recognizedTextEt: EditText

    private companion object{
        private const val CAMERA_REQUEST_CODE = 100
        private const val STORAGE_REQUEST_CODE = 101
    }

    private var imageUri: Uri? = null

    private lateinit var cameraPermissions:Array <String>
    private lateinit var storagePermissions:Array <String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init UI Views
        InputImageBtn = findViewById(R.id.InputImageBtn)
        RecognizeTextBtn = findViewById(R.id.RecognizeTextBtn)
        ImageIv = findViewById(R.id.ImageIv)
        recognizedTextEt = findViewById(R.id.recognizedTextEt)

        InputImageBtn.setOnClickListener{
            showInputImageDialog()
        }
    }
    private fun showInputImageDialog() {

    }
}


