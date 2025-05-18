package com.example.ecocraftplastic

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val signupBtn = findViewById<Button>(R.id.btnSignup)
        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passEt = findViewById<EditText>(R.id.etPassword)

        signupBtn.setOnClickListener {
            val email = emailEt.text.toString()
            val password = passEt.text.toString()

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Signup Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Signup Failed: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
    fun goToLogin(view: View) {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
