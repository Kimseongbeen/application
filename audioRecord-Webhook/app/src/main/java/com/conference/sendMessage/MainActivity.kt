package com.conference.sendMessage

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private lateinit var outputFile: String
    private var isRecording = false
    private var isPaused = false
    private var selectedFile: File? = null

    private val client = OkHttpClient()
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var tvAppTitle: TextView
    private lateinit var tvRecordingStatus: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnPauseResume: Button
    private lateinit var btnSelectFile: Button
    private lateinit var tvSelectedFile: TextView
    private lateinit var btnUploadFile: Button
    private lateinit var tvTranscription: TextView
    private lateinit var tvWebhookUrl: TextView
    private lateinit var btnSettings: Button

    private val recordAudioPermissionCode = 200

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        initializeViews()
        setupListeners()
        updateWebhookUrlDisplay()

        outputFile = "${externalCacheDir?.absolutePath}/audio.mp3"
    }

    private fun initializeViews() {
        tvAppTitle = findViewById(R.id.tv_app_title)
        tvRecordingStatus = findViewById(R.id.tv_recording_status)
        btnRecord = findViewById(R.id.btn_record)
        btnPauseResume = findViewById(R.id.btn_pause_resume)
        btnSelectFile = findViewById(R.id.btn_select_file)
        tvSelectedFile = findViewById(R.id.tv_selected_file)
        btnUploadFile = findViewById(R.id.btn_upload_file)
        tvTranscription = findViewById(R.id.tv_transcription)
        tvWebhookUrl = findViewById(R.id.tv_webhook_url)
        btnSettings = findViewById(R.id.btn_settings)
    }

    private fun setupListeners() {
        btnRecord.setOnClickListener {
            if (checkPermissions()) {
                handleRecordButtonClick()
            } else {
                requestPermissions()
            }
        }

        btnPauseResume.setOnClickListener {
            handlePauseResumeButtonClick()
        }

        btnSelectFile.setOnClickListener {
            openFilePicker()
        }

        btnUploadFile.setOnClickListener {
            selectedFile?.let { file ->
                uploadFile(file)
            }
        }

        btnSettings.setOnClickListener {
            openSettingsActivity()
        }
    }

    private fun handleRecordButtonClick() {
        if (!isRecording) {
            startRecording()
            updateUIForRecordingStart()
        } else {
            stopRecording()
            updateUIForRecordingStop()
            uploadFile(File(outputFile))
        }
        isRecording = !isRecording
    }

    private fun handlePauseResumeButtonClick() {
        if (isPaused) {
            resumeRecording()
            updateUIForRecordingResume()
        } else {
            pauseRecording()
            updateUIForRecordingPause()
        }
        isPaused = !isPaused
    }

    private fun updateUIForRecordingStart() {
        btnRecord.text = getString(R.string.stop_recording)
        tvRecordingStatus.text = getString(R.string.recording)
        btnPauseResume.visibility = View.VISIBLE
    }

    private fun updateUIForRecordingStop() {
        btnRecord.text = getString(R.string.start_recording)
        tvRecordingStatus.text = getString(R.string.recording_completed)
        btnPauseResume.visibility = View.GONE
    }

    private fun updateUIForRecordingPause() {
        btnPauseResume.text = getString(R.string.resume)
        tvRecordingStatus.text = getString(R.string.recording_paused)
    }

    private fun updateUIForRecordingResume() {
        btnPauseResume.text = getString(R.string.pause)
        tvRecordingStatus.text = getString(R.string.recording)
    }

    private fun openFilePicker() {
        getContent.launch("audio/*")
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateWebhookUrlDisplay()
    }

    private fun updateWebhookUrlDisplay() {
        val webhookUrl = getWebhookUrl()
        tvWebhookUrl.text = webhookUrl
    }

    private fun getWebhookUrl(): String {
        val useNgrok = sharedPreferences.getBoolean("use_ngrok", true)
        return if (useNgrok) {
            val ngrokId = sharedPreferences.getString("ngrok_id", "") ?: ""
            val webhookPath = sharedPreferences.getString("webhook_path", "webhook-test/gSjlVYopZ2NKO7ce/webhook/webhook") ?: ""
            "https://$ngrokId.ngrok-free.app/$webhookPath"
        } else {
            sharedPreferences.getString("full_webhook_url", BuildConfig.WEBHOOK_URL) ?: BuildConfig.WEBHOOK_URL
        }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), recordAudioPermissionCode)
    }

    private fun startRecording() {
        recorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile)
            try {
                prepare()
            } catch (e: IOException) {
                Toast.makeText(this@MainActivity, "녹음 준비 실패", Toast.LENGTH_SHORT).show()
            }
            start()
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    private fun pauseRecording() {
        recorder?.pause()
    }

    private fun resumeRecording() {
        recorder?.resume()
    }

    private fun uploadFile(file: File) {
        displayFileInfo(file)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", file.name,
                file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            )
            .addFormDataPart("model", "whisper-1")
            .build()

        val request = Request.Builder()
            .url(getWebhookUrl())
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UploadFile", "파일 업로드 실패", e)
                runOnUiThread {
                    displayTranscription(getString(R.string.upload_failed, e.message))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("UploadFile", "응답: $responseBody")
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val text = jsonObject.getString("text")
                        runOnUiThread {
                            displayTranscription(getString(R.string.upload_success) + "\n\n" + text)
                        }
                    } catch (e: Exception) {
                        Log.e("UploadFile", "응답 처리 실패", e)
                        runOnUiThread {
                            displayTranscription(getString(R.string.upload_failed, e.message))
                        }
                    }
                } else {
                    runOnUiThread {
                        displayTranscription(getString(R.string.upload_failed, response.code.toString()))
                    }
                }
            }
        })
    }

    private fun displayFileInfo(file: File) {
        val fileName = file.name
        val fileExtension = file.extension
        val fileSize = file.length() / 1024  // KB 단위로 변환
        val fileMimeType = "audio/mpeg"
        val directory = file.parent ?: "Unknown"

        val fileInfo = """
            File Name: $fileName
            Directory: $directory
            File Extension: $fileExtension
            Mime Type: $fileMimeType
            File Size: $fileSize kB
        """.trimIndent()

        Log.d("FileInfo", fileInfo)

        runOnUiThread {
            Toast.makeText(this@MainActivity, fileInfo, Toast.LENGTH_LONG).show()
        }
    }

    private fun displayTranscription(text: String) {
        tvTranscription.text = text
    }

    private fun handleSelectedFile(uri: Uri) {
        val fileName = getFileName(uri)
        tvSelectedFile.text = getString(R.string.selected_file, fileName)
        selectedFile = File(cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            selectedFile?.outputStream()?.use { output ->
                input.copyTo(output)
            }
        }
        btnUploadFile.visibility = View.VISIBLE
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "unknown"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordAudioPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }
}