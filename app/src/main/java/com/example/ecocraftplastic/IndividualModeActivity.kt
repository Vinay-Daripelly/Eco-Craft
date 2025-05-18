package com.example.ecocraftplastic

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class IndividualModeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var browseFilesButton: LinearLayout
    private lateinit var takePhotoButton: LinearLayout
    private lateinit var getUpcycling: Button
    private lateinit var requestRecycleButton: Button
    private lateinit var imagePreview: ImageView
    private lateinit var textResinType: TextView
    private lateinit var textCount: TextView
    private lateinit var textTotalCount: TextView
    private lateinit var textMostCommon: TextView

    private var tflite: Interpreter? = null
    private var detectedPlasticType: String = ""
    private var selectedBitmap: Bitmap? = null

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val resinTypes = arrayOf("PET", "HDPE", "PVC", "LDPE", "PP", "PS")

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_individual_mode)
        auth = FirebaseAuth.getInstance()

        browseFilesButton = findViewById(R.id.btn_browse_files)
        takePhotoButton = findViewById(R.id.btn_take_photo)
        imagePreview = findViewById(R.id.image_preview)
        textResinType = findViewById(R.id.text_resin_type)
        getUpcycling = findViewById(R.id.Upcycling)
        requestRecycleButton = findViewById(R.id.btn_request_recycle)
        textCount = findViewById(R.id.text_count)
        textTotalCount = findViewById(R.id.total_count)
        textMostCommon = findViewById(R.id.most_common_type)

        loadTFLiteModel()

        browseFilesButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }

        takePhotoButton.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }

        getUpcycling.setOnClickListener {
            if (detectedPlasticType.isNotEmpty()) {
                val intent = Intent(this, SuggestionActivity::class.java)
                intent.putExtra("PLASTIC_TYPE", detectedPlasticType)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please classify an image first", Toast.LENGTH_SHORT).show()
            }
        }

        val currentUser = auth.currentUser
        if (currentUser?.email == Constants.orgmail) {
            requestRecycleButton.isEnabled = false
            requestRecycleButton.alpha = 0f
        } else {
            requestRecycleButton.setOnClickListener {
                if (detectedPlasticType.isNotEmpty()) {
                    submitRecycleRequest()
                } else {
                    Toast.makeText(this, "Please classify an image first", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTFLiteModel() {
        try {
            val fileDescriptor = assets.openFd("model_unquant.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val model: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            tflite = Interpreter(model)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load TFLite model", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val photo = data?.extras?.get("data") as? Bitmap
                    photo?.let {
                        selectedBitmap = it
                        imagePreview.setImageBitmap(it)
                        classifyWithTFLite(it)
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    val imageUri = data?.data
                    imageUri?.let {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                        selectedBitmap = bitmap
                        imagePreview.setImageBitmap(bitmap)
                        classifyWithTFLite(bitmap)
                    }
                }
            }
        }
    }

    private fun classifyWithTFLite(bitmap: Bitmap) {
        try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }

            for (y in 0 until 224) {
                for (x in 0 until 224) {
                    val px = resizedBitmap.getPixel(x, y)
                    input[0][y][x][0] = ((px shr 16 and 0xFF) / 255.0f)
                    input[0][y][x][1] = ((px shr 8 and 0xFF) / 255.0f)
                    input[0][y][x][2] = ((px and 0xFF) / 255.0f)
                }
            }

            val output = Array(1) { FloatArray(resinTypes.size) }
            tflite?.run(input, output)

            val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
            detectedPlasticType = if (maxIndex != -1) resinTypes[maxIndex] else "Unknown"

            textResinType.text = "Resin Type: $detectedPlasticType"
            updatePlasticCount()

        } catch (e: Exception) {
            Toast.makeText(this, "Classification failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlasticCount() {
        val userId = auth.currentUser?.uid ?: return
        val countRef = firestore.collection("individualPlasticCounts").document(userId)

        val update = hashMapOf<String, Any>(
            detectedPlasticType to FieldValue.increment(1)
        )

        countRef.set(update, SetOptions.merge()).addOnSuccessListener {
            countRef.get().addOnSuccessListener { document ->
                val data = document.data ?: return@addOnSuccessListener
                val total = data.filterKeys { it != "total" && it != "mostCommon" }
                    .values.map { it.toString().toIntOrNull() ?: 0 }.sum()
                val mostCommon = data.filterKeys { it != "total" && it != "mostCommon" }
                    .maxByOrNull { (_, v) -> v.toString().toIntOrNull() ?: 0 }?.key ?: "N/A"

                countRef.update(mapOf(
                    "total" to total,
                    "mostCommon" to mostCommon
                ))

                val currentCount = data[detectedPlasticType]?.toString()?.toLongOrNull() ?: 0
                textCount.text = "Detected Count: $currentCount"
                textTotalCount.text = "Total Items: $total"
                textMostCommon.text = "Most Common: $mostCommon"
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to update count", Toast.LENGTH_SHORT).show()
        }
    }
private fun submitRecycleRequest() {
    val userId = auth.currentUser?.uid ?: return

    val docRef = firestore.collection("individualPlasticCounts").document(userId)

    docRef.get().addOnSuccessListener { document ->
        if (document.exists()) {
            val plasticData = hashMapOf<String, Any>()

            var hasData = false
            for ((key, value) in document.data!!) {
                if (key != "mostCommon" && key != "total") {
                    val count = value as? Long ?: continue
                    if (count > 0) {
                        plasticData[key] = count
                        hasData = true
                    }
                }
            }

            if (!hasData) {
                Toast.makeText(this, "No valid plastic data found.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val input = EditText(this)
            input.hint = "Enter your address"

            AlertDialog.Builder(this)
                .setTitle("Enter Pickup Address")
                .setView(input)
                .setPositiveButton("Submit") { _, _ ->
                    val address = input.text.toString().trim()

                    if (address.isEmpty()) {
                        Toast.makeText(this, "Address cannot be empty", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val requestData = hashMapOf(
                        "userId" to userId,
                        "address" to address,
                        "status" to "Submitted",
                        "timestamp" to System.currentTimeMillis(),
                        "plasticCounts" to plasticData
                    )

                    firestore.collection("recycle_requests")
                        .add(requestData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Recycle request submitted!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to submit request", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()

        } else {
            Toast.makeText(this, "No plastic data found.", Toast.LENGTH_SHORT).show()
        }
    }.addOnFailureListener {
        Toast.makeText(this, "Failed to fetch plastic data", Toast.LENGTH_SHORT).show()
    }
}

}


