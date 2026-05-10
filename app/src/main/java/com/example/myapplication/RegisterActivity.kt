package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class RegisterActivity : AppCompatActivity(){
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        auth = Firebase.auth

        val edtEmail = findViewById<EditText>(R.id.edtEmailReg)
        val edtPassword = findViewById<EditText>(R.id.edtPasswordReg)
        val edtConfirm = findViewById<EditText>(R.id.edtConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val txtBackToLogin = findViewById<TextView>(R.id.txtBackToLogin)

        btnRegister.setOnClickListener{
            val email = edtEmail.text.toString().trim()
            val pass = edtPassword.text.toString().trim()
            val confirm = edtConfirm.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass != confirm) {
                Toast.makeText(this, "Mật khẩu không khớp!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass.length < 6) {
                Toast.makeText(this, "Mật khẩu phải từ 6 ký tự trở lên", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                        // Chuyển thẳng vào MainActivity
                        startActivity(Intent(this, MainActivity::class.java))
                        finishAffinity() // Đóng tất cả các màn hình trước đó (Login)
                    } else {
                        Toast.makeText(this, "Lỗi: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            txtBackToLogin.setOnClickListener { finish() }
        }

    }
}









































