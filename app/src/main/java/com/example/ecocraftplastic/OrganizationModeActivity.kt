package com.example.ecocraftplastic
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class OrganizationModeActivity : AppCompatActivity() {

    private lateinit var addNewItemText: TextView
    private lateinit var detectedPlasticText: TextView
    private lateinit var totalItemsText: TextView
    private lateinit var mostCommonText: TextView
    private lateinit var barChart: BarChart
    private lateinit var pieChart: PieChart

    private var tflite: Interpreter? = null
    private var firestoreListener: ListenerRegistration? = null

    private val resinTypes = arrayOf("PET", "HDPE", "PVC", "LDPE", "PP", "PS")
    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { handleImageUri(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val bitmap = it.data?.extras?.get("data") as? Bitmap
        bitmap?.let { classifyWithTFLite(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organization_mode)

        addNewItemText = findViewById(R.id.addNewItem)
        detectedPlasticText = findViewById(R.id.detectedPlasticType)
        totalItemsText = findViewById(R.id.totalItems)
        mostCommonText = findViewById(R.id.mostCommon)
        pieChart = findViewById(R.id.pieChart)
        barChart = findViewById(R.id.barChart)
        val recycleNotifications = findViewById<ImageView>(R.id.recycle_notifications)

        loadModel()
        setupCharts()

        addNewItemText.setOnClickListener { showImageSourceDialog() }
        recycleNotifications.setOnClickListener {
            val intent = Intent(this, RecycleRequestsActivity::class.java)
            startActivity(intent)
        }

        startRealtimeUpdates()
    }

    private fun loadModel() {
        try {
            val modelFile = assets.openFd("model_unquant.tflite")
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val mappedByteBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                modelFile.startOffset,
                modelFile.declaredLength
            )
            tflite = Interpreter(mappedByteBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Model load failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCharts() {
        // PieChart styling
        pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(android.graphics.Color.WHITE)
            setEntryLabelColor(android.graphics.Color.BLACK)
            setEntryLabelTextSize(12f)
            legend.orientation = Legend.LegendOrientation.VERTICAL
            legend.isWordWrapEnabled = true
        }

        // BarChart styling
        barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            axisLeft.setDrawGridLines(false)
            axisRight.isEnabled = false
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.valueFormatter = IndexAxisValueFormatter(resinTypes)
            legend.isEnabled = false
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Choose from Gallery", "Capture with Camera")
        AlertDialog.Builder(this)
            .setTitle("Add New Plastic Item")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imagePickerLauncher.launch("image/*")
                    1 -> cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                }
            }
            .show()
    }

    private fun handleImageUri(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        bitmap?.let { classifyWithTFLite(it) }
    }

    private fun classifyWithTFLite(bitmap: Bitmap) {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }

        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resizedBitmap.getPixel(x, y)
                input[0][y][x][0] = (pixel shr 16 and 0xFF) / 255.0f
                input[0][y][x][1] = (pixel shr 8 and 0xFF) / 255.0f
                input[0][y][x][2] = (pixel and 0xFF) / 255.0f
            }
        }

        val output = Array(1) { FloatArray(6) }
        tflite?.run(input, output)

        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        val result = if (maxIndex in resinTypes.indices) resinTypes[maxIndex] else "Unknown"

        detectedPlasticText.text = "Detected Type: $result"
        updateFirestoreCount(result)
    }

    private fun updateFirestoreCount(resinType: String) {
        userId?.let { uid ->
            val resinRef = db.collection("organizationStats").document(uid)

            resinRef.get().addOnSuccessListener { doc ->
                val newCount = (doc[resinType] as? Long ?: 0L) + 1
                resinRef.update(resinType, newCount)
                    .addOnFailureListener {
                        resinRef.set(mapOf(resinType to 1L), SetOptions.merge())
                    }
            }
        }
    }

    private fun startRealtimeUpdates() {
        userId?.let { uid ->
            firestoreListener = db.collection("organizationStats").document(uid)
                .addSnapshotListener { doc, _ ->
                    doc?.let {
                        val resinCounts = resinTypes.map { type -> (doc[type] as? Long ?: 0L).toFloat() }

                        updateTotalAndCommon(resinCounts)
                        updatePieChart(resinCounts)
                        updateBarChart(resinCounts)
                    }
                }
        }
    }

    private fun updateTotalAndCommon(resinCounts: List<Float>) {
        val totalItems = resinCounts.sum().toInt()
        totalItemsText.text = totalItems.toString()

        val maxIndex = resinCounts.indices.maxByOrNull { resinCounts[it] } ?: -1
        val mostCommonType = if (maxIndex in resinTypes.indices) resinTypes[maxIndex] else "None"
        mostCommonText.text = mostCommonType
    }

    private fun updatePieChart(resinCounts: List<Float>) {
        val total = resinCounts.sum()
        if (total > 0) {
            val entries = resinCounts.mapIndexedNotNull { index, value ->
                if (value > 0f) PieEntry(value, resinTypes[index]) else null
            }
            val dataSet = PieDataSet(entries, "").apply {
                colors = ColorTemplate.COLORFUL_COLORS.toList()
            }
            pieChart.data = PieData(dataSet)
            pieChart.invalidate()
        } else {
            pieChart.clear()
        }
    }

    private fun updateBarChart(resinCounts: List<Float>) {
        val entries = resinCounts.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value)
        }
        val dataSet = BarDataSet(entries, "Plastic Types").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
        }
        barChart.data = BarData(dataSet)
        barChart.invalidate()
    }

    override fun onDestroy() {
        tflite?.close()
        firestoreListener?.remove()
        super.onDestroy()
    }
}
