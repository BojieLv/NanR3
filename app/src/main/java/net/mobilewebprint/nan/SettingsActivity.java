package net.mobilewebprint.nan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;

/**
 * SettingsActivity - Settings screen for Wi-Fi Aware configuration.
 *
 * <p>This activity provides a user interface for configuring Wi-Fi Aware service
 * parameters including service name, service-specific info, publish/subscribe types,
 * and encryption settings.</p>
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "NanR3.Settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        // The settings UI is hosted in a fragment so preference validation and visibility
        // rules stay isolated from the activity navigation shell.
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        // When the home button is pressed, take the user back to the VisualizerActivity
        if (id == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * SettingsFragment - Fragment for managing Wi-Fi Aware preference settings.
     *
     * <p>This fragment handles the preference UI and manages changes to Wi-Fi Aware
     * configuration including service name, service-specific info, publish/subscribe
     * type selection, and security passphrase settings.</p>
     */
    public static class SettingsFragment extends PreferenceFragmentCompat implements
            SharedPreferences.OnSharedPreferenceChangeListener, OnPreferenceChangeListener {
        EditTextPreference passphrase;
        String currentEncryptType;

        /**
         * Called during fragment creation to set up the preferences.
         *
         * @param savedInstanceState If non-null, this fragment is being reconstructed
         * @param rootKey If non-null, this fragment is being constructed with a preference
         *                hierarchy rooted at this key
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            PreferenceScreen prefScreen = getPreferenceScreen();
            Preference name = findPreference(getString(R.string.service_name));
            Preference info = findPreference(getString(R.string.service_specific_info));
            Preference pass = findPreference(getString(R.string.security_pass));
            if (name != null) {
                name.setOnPreferenceChangeListener(this);
            }
            if (info != null) {
                info.setOnPreferenceChangeListener(this);
            }
            if (pass != null) {
                pass.setOnPreferenceChangeListener(this);
            }
        }

        /**
         * Called when a preference value has been changed.
         *
         * <p>This method handles visibility of the passphrase field based on
         * the selected encryption type (open/pmk/psk). Input is the changed
         * SharedPreferences key; output is an updated preference UI. Invalid or missing
         * preference instances are ignored because Android can recreate this fragment while
         * the backing preference screen is still settling.</p>
         *
         * @param sharedPreferences The SharedPreferences containing the changed preference
         * @param key The key of the preference that was changed
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Figure out which preference was changed
            Preference preference = findPreference(key);

            String secType = getString(R.string.encryptType);
            String type = sharedPreferences.getString(key, "");
            if (key.equals(secType)) {
                if (type.equals("pmk")) {
                    if (passphrase != null) {
                        passphrase.setVisible(true);
                        passphrase.setText("123456789abcdef0123456789abcdef0");
                    }
                } else if (type.equals("psk")) {
                    if (passphrase != null) {
                        passphrase.setVisible(true);
                        passphrase.setText("12345678");
                    }
                } else {
                    if (passphrase != null) {
                        passphrase.setVisible(false);
                    }
                }
            }
            if (null != preference) {
                // Updates the summary for the preference
                if (!(preference instanceof EditTextPreference)) {
                    String value = sharedPreferences.getString(preference.getKey(), "");
                }
            }



        }

        /**
         * Called when a preference is about to be changed and should be validated.
         *
         * <p>This method validates that service name and service-specific info
         * are not empty before allowing the change. The return value is the validation
         * result consumed by the AndroidX Preference framework. Validation errors are
         * handled by showing a toast and returning {@code false}; no exception is thrown
         * for user-entered invalid text.</p>
         *
         * @param preference The Preference to be changed
         * @param newValue The new value for the preference
         * @return true to allow the change, false to reject
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            Toast error = Toast.makeText(getContext(), "Please enter non empty string", Toast.LENGTH_LONG);
            String namekey = getString(R.string.service_name);
            String infokey = getString(R.string.service_specific_info);
            String passkey = getString(R.string.security_pass);
            if (preference.getKey().equals(namekey)||preference.getKey().equals(infokey)) {
                Log.d(TAG,"IN HERE ");
                String name = (String) newValue;
                if (name.isEmpty()) {
                    error.show();
                    return false;
                }
                else
                    return true;
            }
            return true;
        }

/*        private void setPreferenceSummary(Preference preference, String value) {
            if (preference instanceof ListPreference) {
                // For list preferences, figure out the label of the selected value
                ListPreference listPreference = (ListPreference) preference;
                int prefIndex = listPreference.findIndexOfValue(value);
                if (prefIndex >= 0) {
                    // Set the summary to that label
                    listPreference.setSummary(listPreference.getEntries()[prefIndex]);
                }
            } else if (preference instanceof EditTextPreference) {
                // For EditTextPreferences, set the summary to the value's simple string representation.
                preference.setSummary(value);
            }
        }*/


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Register for live updates so switching between open/PMK/PSK immediately
            // shows or hides the passphrase field without leaving the settings screen.
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            String secType = getString(R.string.encryptType);
            passphrase = (EditTextPreference) getPreferenceManager().findPreference(getResources().getString(R.string.security_pass));
            currentEncryptType = getPreferenceManager().getSharedPreferences().getString(secType,"");
            Log.d(TAG,"TYPE "+currentEncryptType);
            if (passphrase == null) {
                return;
            }
            if (currentEncryptType.equals("open")){
                passphrase.setVisible(false);
            }
            else
                passphrase.setVisible(true);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}
