package com.example.llmapp

import android.content.Context
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.text.InputType
import android.content.SharedPreferences
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.view.MenuItem
import com.example.llmapp.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private var pendingSelectedModel: String? = null
    private var isEditingEmail: Boolean = false
    // When true, the next automatic focus/click should NOT open the dropdown.
    private var suppressDropdownShow = false
    // Track dropdown visibility reliably across platform variants
    private var isDropdownShowing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore pending selection (if we were recreated)
        if (savedInstanceState != null) {
            pendingSelectedModel = savedInstanceState.getString("selected_model")
        }

        // Setup toolbar with back arrow
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            
            // Set multiple navigation handlers for redundancy
            binding.toolbar.setNavigationOnClickListener {
                try {
                    onBackPressed()
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Error on back pressed: ${e.message}")
                    finish()
                }
            }
            android.util.Log.d("SettingsActivity", "Toolbar setup completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error setting up toolbar: ${e.message}")
        }

        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    // Show saved email or sample email (display-only)
    var email = prefs.getString("user_email", null)
    if (email.isNullOrBlank()) email = getString(R.string.sample_email)
    binding.sampleEmail.text = email

        // Load theme preference and wire switch
        val isDark = prefs.getBoolean("dark_mode", false)
        binding.switchTheme.isChecked = isDark

        binding.switchTheme.setOnCheckedChangeListener { _, checked ->
            // Prevent the dropdown from auto-opening due to focus changes caused by theme toggle
            suppressDropdownShow = true
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            prefs.edit().putBoolean("dark_mode", checked).apply()
        }

        // Wire up click-to-edit behavior for email
        binding.sampleEmail.setOnClickListener {
            // Start editing: hide TextView, show EditText container
            val current = prefs.getString("user_email", null) ?: ""
            binding.sampleEmail.visibility = View.GONE
            binding.emailEditContainer.visibility = View.VISIBLE
            binding.emailInput.setText(if (current.isBlank()) "" else current)
            binding.emailInput.requestFocus()
            // Show keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.emailInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            isEditingEmail = true
        }

        // Wire up save behavior
        binding.updateEmailButton.setOnClickListener {
            val newEmail = binding.emailInput.text.toString().trim()
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() || newEmail.isEmpty()) {
                // Save valid email or empty string
                prefs.edit().putString("user_email", newEmail).apply()
                binding.sampleEmail.text = if (newEmail.isBlank()) getString(R.string.sample_email) else newEmail
                binding.emailEditContainer.visibility = View.GONE
                binding.sampleEmail.visibility = View.VISIBLE
                isEditingEmail = false
                // Hide keyboard
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            } else {
                binding.emailInput.error = "Please enter a valid email address"
            }
        }

        // Setup model dropdown (separate method) to allow re-initialization after theme changes
        setupModelDropdown(prefs)
    }

    override fun onResume() {
        super.onResume()
        // Re-initialize dropdown in case the activity was recreated after theme change.
        // Post to the message queue so it runs after the view is fully laid out.
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        binding.root.post { setupModelDropdown(prefs) }
    }

    private fun setupModelDropdown(prefs: SharedPreferences) {
    val modelOptions = resources.getStringArray(R.array.model_options)
    // Use a centered dropdown item layout so menu text appears centered
    val modelAdapter = ArrayAdapter(this, R.layout.dropdown_item_centered, modelOptions)
    modelAdapter.setDropDownViewResource(R.layout.dropdown_item_centered)
    binding.modelDropdown.setAdapter(modelAdapter)

        // Disable keyboard and prevent the field itself from opening the menu.
        // Only the end-icon (down arrow) will open the dropdown.
        binding.modelDropdown.inputType = InputType.TYPE_NULL
        binding.modelDropdown.threshold = Int.MAX_VALUE // prevent auto suggestions

        // Show dropdown when clicking anywhere in the field (not just the arrow)
        binding.modelDropdown.setOnClickListener {
            if (!suppressDropdownShow) {
                if (isDropdownShowing) {
                    binding.modelDropdown.dismissDropDown()
                    binding.modelInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)
                    isDropdownShowing = false
                } else {
                    binding.modelDropdown.showDropDown()
                    binding.modelInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_expand_less)
                    isDropdownShowing = true
                }
            }
            suppressDropdownShow = false
        }
            // Prevent auto-showing dropdown on focus
            binding.modelDropdown.setOnFocusChangeListener { _, _ -> }

            // Set dropdown popup width and vertical offset
        try {
            binding.modelDropdown.dropDownWidth = resources.getDimensionPixelSize(R.dimen.model_dropdown_width)
            binding.modelDropdown.dropDownVerticalOffset = resources.getDimensionPixelSize(R.dimen.model_dropdown_vertical_offset)
            // popup background drawable already set to a no-shadow shape; no elevation API used here to stay
            // compatible across platform/library versions.
        } catch (e: Exception) {
            // Fallback to match_parent and no vertical offset if resource is unavailable
            binding.modelDropdown.dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
            binding.modelDropdown.dropDownVerticalOffset = 0
        }

        // Wire the TextInputLayout end-icon to open the popup when tapped.
        // Use the input layout id `modelInputLayout` we added in the layout.
        binding.modelInputLayout.setEndIconOnClickListener {
            if (suppressDropdownShow) {
                suppressDropdownShow = false
                return@setEndIconOnClickListener
            }

            // Use our own visibility flag to avoid timing/platform issues with isPopupShowing
            if (isDropdownShowing) {
                binding.modelDropdown.dismissDropDown()
                binding.modelInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)
                isDropdownShowing = false
            } else {
                binding.modelDropdown.showDropDown()
                binding.modelInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_expand_less)
                isDropdownShowing = true
            }
        }

        // When an item is selected, persist and collapse icon to down-arrow
        binding.modelDropdown.setOnItemClickListener { _, _, position, _ ->
            val sel = modelOptions.getOrNull(position) ?: ""
            prefs.edit().putString("selected_model", sel).apply()
            // reset end icon to down arrow and mark dropdown hidden
            binding.modelInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)
            isDropdownShowing = false
        }

        // If the popup is dismissed by tapping outside, ensure icon resets to down-arrow
        try {
            binding.modelDropdown.setOnDismissListener {
                   // Small delay to prevent immediate re-opening
                   binding.root.postDelayed({
                binding.modelInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)
                isDropdownShowing = false
                   }, 100)
            }
        } catch (e: Exception) {
            // setOnDismissListener may not be available on all platform variants; fall back to nothing
            // But we'll still rely on our isDropdownShowing flag where possible
        }

    // Prefer pendingSelectedModel (restored from savedInstanceState) if present, otherwise persisted pref
    val selectedModel = pendingSelectedModel ?: prefs.getString("selected_model", modelOptions.firstOrNull() ?: "")
    if (!selectedModel.isNullOrEmpty()) binding.modelDropdown.setText(selectedModel, false)
    // clear pending after applying
    pendingSelectedModel = null

        binding.modelDropdown.setOnItemClickListener { _, _, position, _ ->
            val sel = modelOptions.getOrNull(position) ?: ""
            prefs.edit().putString("selected_model", sel).apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

}
