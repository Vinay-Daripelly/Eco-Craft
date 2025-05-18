package com.example.ecocraftplastic

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SuggestionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suggestion)

        val plasticType = intent.getStringExtra("PLASTIC_TYPE") ?: ""
        val quantity = intent.getStringExtra("QUANTITY") ?: "medium"

        val suggestionContainer = findViewById<LinearLayout>(R.id.suggestion_container)
        val gemini = SuggestionService()

        gemini.getSuggestions(plasticType, quantity) { result ->
            runOnUiThread {
                if (result != null) {
                    suggestionContainer.removeAllViews()

                    val sections = result.split("\n\n")  // Separate sections
                    for (section in sections) {
                        val sectionText = section.trim()

                        // Extract title (first line) and body
                        val lines = sectionText.split("\n", limit = 2)
                        val heading = lines.firstOrNull()?.trim() ?: ""
                        val body = lines.getOrNull(1)?.trim() ?: ""

                        val combinedText = "$heading\n$body"
                        val spannable = SpannableString(combinedText)

                        // Make heading bold
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            heading.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        val textView = TextView(this).apply {
                            text = spannable
                            textSize = 16f
                            setTextColor(resources.getColor(android.R.color.black))
                            setPadding(20, 20, 20, 20)
                            setBackgroundResource(R.drawable.suggestion_box)
                        }

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 0, 0, 30)

                        suggestionContainer.addView(textView, params)
                    }
                } else {
                    Toast.makeText(this, "Failed to fetch suggestions", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
