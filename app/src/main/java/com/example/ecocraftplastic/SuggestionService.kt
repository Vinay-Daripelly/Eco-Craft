package com.example.ecocraftplastic

import Constants
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import android.util.Log

class SuggestionService {

    private val client = OkHttpClient()
    private val api = Constants.apiKey
    private val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$api"

    fun getSuggestions(plasticType: String, quantity: String, callback: (String?) -> Unit) {
        val prompt = """
    You are an expert in plastic waste upcycling. Provide suggestions in a clear, structured format with no special symbols like *, -, or markdown formatting.

    Plastic Type: $plasticType
    Quantity: $quantity

    Response Format:
    Title: Upcycling Suggestions for $plasticType
    Summary: (Brief overview of how $plasticType can be upcycled in $quantity quantities)
    
    Suggestions:
    1. Project Name:   [Name]
       Materials Required:  [List]
       Description:   [Short description]
       Skill Level:   [Beginner/Intermediate/Advanced]

    2. Project Name: ...
       ...

    Environmental Benefit: (Short paragraph explaining how these upcycling efforts reduce plastic waste and help the environment)

    The entire response should be plain text with no bullet points, asterisks, or formatting characters.
""".trimIndent()


        // Correct JSON structure
        val part = JSONObject().put("text", prompt)
        val partsArray = JSONArray().put(part)

        val content = JSONObject().put("parts", partsArray)
        val contentsArray = JSONArray().put(content)

        val json = JSONObject().put("contents", contentsArray)

        val mediaType = "application/json".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GeminiResponse", "Request failed: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val rawJson = response.body?.string()
                Log.e("GeminiResponse", rawJson ?: "No body received")

                try {
                    if (!response.isSuccessful || rawJson == null) {
                        callback(null)
                        return
                    }

                    val jsonResponse = JSONObject(rawJson)
                    val result = jsonResponse
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    callback(result)
                } catch (e: Exception) {
                    Log.e("GeminiResponse", "JSON parsing error: ${e.message}")
                    callback(null)
                }
            }
        })
    }
}
