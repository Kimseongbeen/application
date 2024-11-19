package com.conference.sendMessage

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var rgWebhookType: RadioGroup
    private lateinit var tilNgrokId: TextInputLayout
    private lateinit var tilWebhookPath: TextInputLayout
    private lateinit var tilFullWebhookUrl: TextInputLayout
    private lateinit var etNgrokId: EditText
    private lateinit var etWebhookPath: EditText
    private lateinit var etFullWebhookUrl: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        rgWebhookType = findViewById(R.id.rg_webhook_type)
        tilNgrokId = findViewById(R.id.til_ngrok_id)
        tilWebhookPath = findViewById(R.id.til_webhook_path)
        tilFullWebhookUrl = findViewById(R.id.til_full_webhook_url)
        etNgrokId = findViewById(R.id.et_ngrok_id)
        etWebhookPath = findViewById(R.id.et_webhook_path)
        etFullWebhookUrl = findViewById(R.id.et_full_webhook_url)
        btnSave = findViewById(R.id.btn_save)
    }

    private fun loadSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val useNgrok = sharedPreferences.getBoolean("use_ngrok", true)
        rgWebhookType.check(if (useNgrok) R.id.rb_ngrok else R.id.rb_full_url)
        etNgrokId.setText(sharedPreferences.getString("ngrok_id", ""))
        etWebhookPath.setText(sharedPreferences.getString("webhook_path", "webhook-test/gSjlVYopZ2NKO7ce/webhook/webhook"))
        etFullWebhookUrl.setText(sharedPreferences.getString("full_webhook_url", ""))

        updateUIState(useNgrok)
    }

    private fun setupListeners() {
        rgWebhookType.setOnCheckedChangeListener { _, checkedId ->
            updateUIState(checkedId == R.id.rb_ngrok)
        }

        btnSave.setOnClickListener {
            saveSettings()
            finish()
        }
    }

    private fun updateUIState(useNgrok: Boolean) {
        tilNgrokId.visibility = if (useNgrok) View.VISIBLE else View.GONE
        tilWebhookPath.visibility = if (useNgrok) View.VISIBLE else View.GONE
        tilFullWebhookUrl.visibility = if (useNgrok) View.GONE else View.VISIBLE
    }

    private fun saveSettings() {
        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        val isNgrok = rgWebhookType.checkedRadioButtonId == R.id.rb_ngrok
        editor.putBoolean("use_ngrok", isNgrok)
        editor.putString("ngrok_id", etNgrokId.text.toString())
        editor.putString("webhook_path", etWebhookPath.text.toString())
        editor.putString("full_webhook_url", etFullWebhookUrl.text.toString())
        editor.apply()
    }
}