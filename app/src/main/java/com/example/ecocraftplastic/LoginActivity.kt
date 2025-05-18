package com.example.ecocraftplastic

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val loginBtn = findViewById<Button>(R.id.btnLogin)
        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passEt = findViewById<EditText>(R.id.etPassword)

        loginBtn.setOnClickListener {
            val email = emailEt.text.toString()
            val password = passEt.text.toString()

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                    } else {
                        Toast.makeText(this, "Login Failed: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
    fun goToSignup(view: View) {
        startActivity(Intent(this, SignupActivity::class.java))
        finish()
    }

}

