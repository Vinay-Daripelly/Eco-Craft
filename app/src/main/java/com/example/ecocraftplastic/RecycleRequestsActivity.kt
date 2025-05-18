package com.example.ecocraftplastic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.example.ecocraftplastic.databinding.ActivityRecycleRequestsBinding
import com.example.ecocraftplastic.databinding.ItemRecycleRequestBinding

class RecycleRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecycleRequestsBinding
    private val db = FirebaseFirestore.getInstance()
    private val requests = mutableListOf<RecycleRequest>()
    private lateinit var adapter: RecycleRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RecycleRequestsActivity", "onCreate called")

        binding = ActivityRecycleRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        adapter = RecycleRequestAdapter(requests,
            onApprove = { request -> handleRequest(request, true) },
            onReject = { request -> handleRequest(request, false) })

        binding.recyclerRequests.layoutManager = LinearLayoutManager(this)
        binding.recyclerRequests.adapter = adapter

        fetchRequests()

    }

    private fun fetchRequests() {
        db.collection("recycle_requests")
            .whereEqualTo("status", "Submitted")
            .get()
            .addOnSuccessListener { snapshot ->
                requests.clear()
                for (doc in snapshot.documents) {
                    val request = doc.toObject<RecycleRequest>()
                    if (request != null) {
                        request.id = doc.id
                        requests.add(request)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun handleRequest(request: RecycleRequest, approve: Boolean) {
        val status = if (approve) "approved" else "rejected"

        val requestRef = db.collection("recycle_requests").document(request.id)
        Toast.makeText(this,"Status has been updated!",Toast.LENGTH_SHORT).show();
        requestRef.update("status", status)
        if (approve) {
            val userRef = db.collection("individualPlasticCounts").document(request.userId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val plasticCounts = snapshot.data?.toMutableMap() ?: mutableMapOf()
                val updatedCounts = plasticCounts.toMutableMap()

                // Track total count
                var totalCount = 0

                for ((type, count) in request.plasticCounts) {
                    val current = (plasticCounts[type] as? Long ?: 0L).toInt()
                    val newCount = (current - count).coerceAtLeast(0)
                    updatedCounts[type] = newCount
                }

                // Recalculate total (sum of all plastics only)
                val plasticTypes = listOf("PET", "HDPE", "PVC", "LDPE", "PP", "PS") // include all known types
                totalCount = plasticTypes.sumOf { (updatedCounts[it] as? Int ?: (updatedCounts[it] as? Long ?: 0L).toInt()) }

                updatedCounts["total"] = totalCount

                // Recalculate mostCommon
                val mostCommonType = plasticTypes.maxByOrNull { (updatedCounts[it] as? Int ?: (updatedCounts[it] as? Long ?: 0L).toInt()) } ?: "None"
                updatedCounts["mostCommon"] = mostCommonType

                transaction.set(userRef, updatedCounts, com.google.firebase.firestore.SetOptions.merge())
                null
            }
        }

    }
}

// Model class
data class RecycleRequest(
    var id: String = "",
    val userId: String = "",
    val address: String = "",
    val status: String = "",
    val plasticCounts: Map<String, Long> = emptyMap()
)

// Adapter
class RecycleRequestAdapter(
    private val requests: List<RecycleRequest>,
    private val onApprove: (RecycleRequest) -> Unit,
    private val onReject: (RecycleRequest) -> Unit
) : RecyclerView.Adapter<RecycleRequestAdapter.RequestViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemRecycleRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    inner class RequestViewHolder(private val binding: ItemRecycleRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(request: RecycleRequest) {
            binding.textUserId.text = "User ID: ${request.userId}"
            binding.textAddress.text = "Address: ${request.address}"
            binding.textStatus.text = "Status: ${request.status}"


            val plasticInfo = request.plasticCounts.entries.joinToString("\n") { (type, count) -> "$type: $count" }
            binding.textPlastics.text = plasticInfo

            binding.buttonApprove.setOnClickListener { onApprove(request) }
            binding.buttonReject.setOnClickListener { onReject(request) }
        }
    }
}
