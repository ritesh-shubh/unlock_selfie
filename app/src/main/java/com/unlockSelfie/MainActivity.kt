package com.unlockSelfie

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.unlockSelfie.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkAndRequestStoragePermission()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
            }
        }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            updateUI()
        }

    private val requestAdminPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Prefs.setServiceEnabled(this, true)
                Toast.makeText(this, getString(R.string.admin_enabled), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.admin_required), Toast.LENGTH_LONG).show()
                binding.radioWrongPassword.isChecked = false
                binding.radioUnlock.isChecked = true
                Prefs.setTriggerMode(this, Prefs.TRIGGER_UNLOCK)
            }
            updateUI()
        }

    private val openDirectoryPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val path = getPathFromUri(uri)
                    if (path != null) {
                        Prefs.setSaveDir(this, path)
                        updateUI()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        setupClickListeners()
        updateUI()
        checkPermissions()
    }

    private fun setupClickListeners() {
        binding.btnChooseDir.setOnClickListener {
            openDirectoryChooser()
        }

        binding.radioGroupTrigger.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioUnlock -> {
                    Prefs.setTriggerMode(this, Prefs.TRIGGER_UNLOCK)
                }
                R.id.radioWrongPassword -> {
                    Prefs.setTriggerMode(this, Prefs.TRIGGER_WRONG_PASSWORD)
                    if (!devicePolicyManager.isAdminActive(adminComponent)) {
                        requestAdminAccess()
                    }
                }
            }
        }

        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAutoStart(this, isChecked)
        }

        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasRequiredPermissions()) {
                    binding.switchEnabled.isChecked = false
                    checkPermissions()
                    return@setOnCheckedChangeListener
                }
                if (Prefs.getTriggerMode(this) == Prefs.TRIGGER_WRONG_PASSWORD &&
                    !devicePolicyManager.isAdminActive(adminComponent)) {
                    binding.switchEnabled.isChecked = false
                    requestAdminAccess()
                    return@setOnCheckedChangeListener
                }
                Prefs.setServiceEnabled(this, true)
                Toast.makeText(this, getString(R.string.monitoring_enabled), Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setServiceEnabled(this, false)
                if (devicePolicyManager.isAdminActive(adminComponent)) {
                    devicePolicyManager.removeActiveAdmin(adminComponent)
                }
                Toast.makeText(this, getString(R.string.monitoring_disabled), Toast.LENGTH_SHORT).show()
            }
            updateUI()
        }
    }

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED -> {
                checkAndRequestStoragePermission()
            }
        }
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            updateUI()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storageGranted = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return cameraGranted && storageGranted
    }

    private fun requestAdminAccess() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.admin_explanation))
        }
        requestAdminPermission.launch(intent)
    }

    private fun openDirectoryChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        openDirectoryPicker.launch(intent)
    }

    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val path = uri.path
            if (path != null && path.contains("primary:")) {
                val subPath = path.substringAfter("primary:")
                "${Environment.getExternalStorageDirectory().absolutePath}/$subPath"
            } else {
                getExternalFilesDir(null)?.absolutePath
            }
        } catch (e: Exception) {
            getExternalFilesDir(null)?.absolutePath
        }
    }

    private fun updateUI() {
        val saveDir = Prefs.getSaveDir(this)
        binding.tvSaveDir.text = getString(R.string.save_dir_label, saveDir)

        val triggerMode = Prefs.getTriggerMode(this)
        binding.radioUnlock.isChecked = triggerMode == Prefs.TRIGGER_UNLOCK
        binding.radioWrongPassword.isChecked = triggerMode == Prefs.TRIGGER_WRONG_PASSWORD

        binding.switchAutoStart.isChecked = Prefs.isAutoStart(this)
        binding.switchEnabled.isChecked = Prefs.isServiceEnabled(this)

        val permissionsOk = hasRequiredPermissions()
        binding.tvPermissionStatus.text = if (permissionsOk)
            getString(R.string.permissions_granted)
        else
            getString(R.string.permissions_missing)
        binding.tvPermissionStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (permissionsOk) R.color.green else R.color.red
            )
        )

        val adminActive = devicePolicyManager.isAdminActive(adminComponent)
        binding.tvAdminStatus.visibility =
            if (triggerMode == Prefs.TRIGGER_WRONG_PASSWORD) View.VISIBLE else View.GONE
        binding.tvAdminStatus.text = if (adminActive)
            getString(R.string.admin_active)
        else
            getString(R.string.admin_inactive)
        binding.tvAdminStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (adminActive) R.color.green else R.color.orange
            )
        )
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
