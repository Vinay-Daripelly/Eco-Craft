package com.example.ecocraftplastic

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class PlasticDetectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plastic_detection)

        val btnIndividual = findViewById<LinearLayout>(R.id.btn_individual_mode)
        val btnOrganization = findViewById<LinearLayout>(R.id.btn_organization_mode)

        btnIndividual.setOnClickListener {
            val intent = Intent(this, IndividualModeActivity::class.java)
            startActivity(intent)
        }

        btnOrganization.setOnClickListener {
            val intent = Intent(this,   OrganizationModeActivity::class.java)
            startActivity(intent)
        }
    }
}
