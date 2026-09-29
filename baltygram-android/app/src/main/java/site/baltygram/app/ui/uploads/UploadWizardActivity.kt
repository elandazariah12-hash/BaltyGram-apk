package site.baltygram.app.ui.uploads

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.data.SessionManager
import site.baltygram.app.network.SupabaseApi
import java.util.UUID

class UploadWizardActivity : AppCompatActivity() {

    private lateinit var flipper: ViewFlipper
    private lateinit var stepLabel: TextView
    private lateinit var btnNext: View
    private lateinit var btnBack: View

    private val totalSteps = 6
    private var currentStep = 0

    // ---- Wizard state ----
    private var projectName = ""
    private var description = ""
    private var submissionKind = "software"
    private var platform: String? = "android"
    private var iconUri: Uri? = null
    private val screenshotUris = mutableListOf<Uri>()
    private var fileUri: Uri? = null
    private var fileName: String? = null
    private var fileSize: Long = 0

    private val submissionKinds = listOf("software", "tool", "game", "app", "source_code", "project", "other")
    private val platforms = listOf(
        "android" to "Android", "windows" to "Windows", "macos" to "macOS", "ios" to "iOS",
        "linux" to "Linux", "web" to "Web", "cross_platform" to "Cross-platform",
        "source_code" to "Source Code", "other" to "Other"
    )

    private val iconPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            iconUri = uri
            findViewById<ImageView>(R.id.icon_preview).setImageURI(uri)
        }
    }

    private val screenshotsPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val room = 5 - screenshotUris.size
        if (room <= 0) {
            Toast.makeText(this, "Maximum 5 screenshots.", Toast.LENGTH_SHORT).show()
        } else {
            screenshotUris.addAll(uris.take(room))
            renderScreenshotsRow()
        }
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileUri = uri
            val (name, size) = queryFileInfo(uri)
            fileName = name
            fileSize = size
            findViewById<android.widget.Button>(R.id.btn_choose_file).text =
                "$name (${String.format("%.2f", size / 1e6)} MB)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_wizard)

        flipper = findViewById(R.id.view_flipper)
        stepLabel = findViewById(R.id.step_label)
        btnNext = findViewById(R.id.btn_wizard_next)
        btnBack = findViewById(R.id.btn_wizard_back)

        findViewById<View>(R.id.btn_back_activity).setOnClickListener { finish() }

        setupSpinners()

        findViewById<View>(R.id.btn_choose_icon).setOnClickListener { iconPicker.launch("image/*") }
        findViewById<View>(R.id.btn_add_screenshots).setOnClickListener { screenshotsPicker.launch("image/*") }
        findViewById<View>(R.id.btn_choose_file).setOnClickListener {
            filePicker.launch(arrayOf(
                "application/vnd.android.package-archive", "application/zip", "application/x-7z-compressed",
                "application/x-rar-compressed", "application/octet-stream", "application/x-msdownload", "*/*"
            ))
        }

        btnNext.setOnClickListener { onNextClicked() }
        btnBack.setOnClickListener { goToStep(currentStep - 1) }

        updateStepUi()
    }

    private fun setupSpinners() {
        val kindSpinner = findViewById<Spinner>(R.id.spinner_kind)
        kindSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, submissionKinds.map {
            it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }
        })
        kindSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                submissionKind = submissionKinds[position]
                val isSourceCode = submissionKind == "source_code"
                findViewById<Spinner>(R.id.spinner_platform).isEnabled = !isSourceCode
                findViewById<View>(R.id.platform_note).visibility = if (isSourceCode) View.VISIBLE else View.GONE
                if (isSourceCode) platform = null
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val platformSpinner = findViewById<Spinner>(R.id.spinner_platform)
        platformSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, platforms.map { it.second })
        platformSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (submissionKind != "source_code") platform = platforms[position].first
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun renderScreenshotsRow() {
        val row = findViewById<LinearLayout>(R.id.screenshots_row)
        row.removeAllViews()
        val size = (72 * resources.displayMetrics.density).toInt()
        screenshotUris.forEach { uri ->
            val iv = ImageView(this)
            iv.setImageURI(uri)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = 8
            iv.layoutParams = params
            iv.setBackgroundResource(R.drawable.bg_card)
            row.addView(iv)
        }
    }

    private fun queryFileInfo(uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun onNextClicked() {
        when (currentStep) {
            0 -> {
                val input = findViewById<EditText>(R.id.input_project_name)
                projectName = input.text.toString().trim()
                if (projectName.isEmpty()) {
                    input.error = "Project name is required"
                    return
                }
                goToStep(1)
            }
            1 -> {
                description = findViewById<EditText>(R.id.input_description).text.toString().trim()
                goToStep(2)
            }
            2 -> goToStep(3)
            3 -> goToStep(4)
            4 -> goToStep(5)
            5 -> submitUpload()
        }
    }

    private fun goToStep(step: Int) {
        currentStep = step.coerceIn(0, totalSteps - 1)
        flipper.displayedChild = currentStep
        updateStepUi()
    }

    private fun updateStepUi() {
        val labels = listOf("Project name", "Description", "Category & platform", "Icon", "Screenshots", "File & submit")
        stepLabel.text = "Step ${currentStep + 1} of $totalSteps - ${labels[currentStep]}"
        btnBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
        (btnNext as android.widget.Button).text = if (currentStep == totalSteps - 1) "Finish" else "Next"
    }

    private fun showError(message: String) {
        val errView = findViewById<TextView>(R.id.wizard_error)
        errView.text = message
        errView.visibility = View.VISIBLE
    }

    private fun submitUpload() {
        val ownershipChecked = findViewById<CheckBox>(R.id.checkbox_ownership).isChecked
        if (fileUri == null) return showError("Please select a file to upload.")
        if (!ownershipChecked) return showError("You must confirm ownership before submitting.")

        val userId = SessionManager.userId ?: return showError("You must be signed in.")
        findViewById<TextView>(R.id.wizard_error).visibility = View.GONE
        (btnNext as android.widget.Button).apply { isEnabled = false; text = "Uploading..." }

        val timestamp = System.currentTimeMillis()

        // Step A: icon (optional)
        if (iconUri != null) {
            val bytes = contentResolver.openInputStream(iconUri!!)!!.use { it.readBytes() }
            val path = "$userId/icons/$timestamp.jpg"
            SupabaseApi.uploadToStorage("software-media", path, bytes, "image/jpeg", object : SupabaseApi.Callback {
                override fun onSuccess(body: String) { uploadScreenshotsThenContinue(userId, timestamp, SupabaseApi.publicUrl("software-media", path)) }
                override fun onError(message: String) { finishWithError("Icon upload failed: $message") }
            })
        } else {
            uploadScreenshotsThenContinue(userId, timestamp, null)
        }
    }

    private fun uploadScreenshotsThenContinue(userId: String, timestamp: Long, iconUrl: String?) {
        if (screenshotUris.isEmpty()) {
            uploadPackageFileThenInsert(userId, timestamp, iconUrl, emptyList())
            return
        }
        val urls = mutableListOf<String>()
        var index = 0

        fun uploadNext() {
            if (index >= screenshotUris.size) {
                uploadPackageFileThenInsert(userId, timestamp, iconUrl, urls)
                return
            }
            val uri = screenshotUris[index]
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val path = "$userId/screenshots/$timestamp-$index.jpg"
            SupabaseApi.uploadToStorage("software-media", path, bytes, "image/jpeg", object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    urls.add(SupabaseApi.publicUrl("software-media", path))
                    index++
                    uploadNext()
                }
                override fun onError(message: String) { finishWithError("Screenshot upload failed: $message") }
            })
        }
        uploadNext()
    }

    private fun uploadPackageFileThenInsert(userId: String, timestamp: Long, iconUrl: String?, screenshotUrls: List<String>) {
        val bytes = contentResolver.openInputStream(fileUri!!)!!.use { it.readBytes() }
        val safeName = (fileName ?: "file").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val storagePath = "$userId/$timestamp-$safeName"

        SupabaseApi.uploadToStorage("software-packages", storagePath, bytes, "application/octet-stream", object : SupabaseApi.Callback {
            override fun onSuccess(body: String) {
                insertUploadRecord(userId, storagePath, iconUrl, screenshotUrls)
            }
            override fun onError(message: String) { finishWithError("File upload failed: $message") }
        })
    }

    private fun insertUploadRecord(userId: String, storagePath: String, iconUrl: String?, screenshotUrls: List<String>) {
        val nowIso = isoNow()
        val screenshotsJson = JSONArray(screenshotUrls)

        val json = JSONObject()
            .put("uploader_id", userId)
            .put("status", "pending")
            .put("submitted_at", nowIso)
            .put("file_name", fileName)
            .put("file_size_bytes", fileSize)
            .put("file_type", fileName?.substringAfterLast('.', ""))
            .put("security_scan_status", "pending")
            .put("submitted_step", totalSteps)
            .put("project_name", projectName)
            .put("project_description", description)
            .put("platform", platform)
            .put("submission_kind", submissionKind)
            .put("storage_path", storagePath)
            .put("icon_path", iconUrl)
            .put("screenshot_paths", screenshotsJson)
            .put("ownership_confirmed_at", nowIso)

        SupabaseApi.insert("uploads", "[$json]", object : SupabaseApi.Callback {
            override fun onSuccess(body: String) {
                Toast.makeText(this@UploadWizardActivity, "Upload submitted for review!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            override fun onError(message: String) { finishWithError(message) }
        })
    }

    private fun finishWithError(message: String) {
        showError(message)
        (btnNext as android.widget.Button).apply { isEnabled = true; text = "Finish" }
    }

    /** ISO-8601 UTC timestamp without needing java.time (which requires API 26+;
     *  minSdk here is 24, and adding core library desugaring just for this felt
     *  like unnecessary build complexity for an AIDE project). */
    private fun isoNow(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }
}
