/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.dialer.app.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.BlockedNumberContract;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;


import com.android.dialer.R;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.lookup.LookupSettingsFragment;
import com.android.dialer.proguard.UsedByReflection;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.voicemail.settings.VoicemailSettingsFragment;
import com.android.voicemail.VoicemailClient;
import java.util.List;

/** Activity for dialer settings. */
@SuppressWarnings("FragmentInjection") // Activity not exported
@UsedByReflection(value = "AndroidManifest-app.xml")
public class DialerSettingsActivity extends CollapsingToolbarBaseActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

  public static final String PREFS_FRAGMENT_TAG = "prefs_fragment";

  public static final String EXTRA_SHOW_FRAGMENT = "show_fragment";
  public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = "show_fragment_args";


  protected SharedPreferences preferences;
//  private List<Header> headers;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    LogUtil.enterBlock("DialerSettingsActivity.onCreate");
    super.onCreate(savedInstanceState);
    preferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
    Intent intent = getIntent();
    Uri data = intent.getData();

    /*if (data != null) {
      String headerToOpen = data.getSchemeSpecificPart();
      if (headerToOpen != null && headers != null) {
        for (Header header : headers) {
          if (headerToOpen.equals(header.fragment)) {
            LogUtil.i("DialerSettingsActivity.onCreate", "switching to header: " + headerToOpen);
            switchToHeader(header);
            break;
          }
        }
      }
    }*/

    String initialFragment = getIntent().getStringExtra(EXTRA_SHOW_FRAGMENT);
    Bundle initialArguments = getIntent().getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);

    // If savedInstanceState is non-null, then the activity is being
    // recreated and super.onCreate() has already recreated the fragment.
    if (savedInstanceState == null) {
      if (initialFragment == null)
        initialFragment = PrefsFragment.class.getName();
      Fragment fragment = getSupportFragmentManager()
              .getFragmentFactory()
              .instantiate(getClassLoader(), initialFragment);
      fragment.setArguments(initialArguments);
      getSupportFragmentManager().beginTransaction()
              .replace(R.id.content_frame, fragment)
              .commit();
    }
  }

  @Override
  public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                                           @NonNull Preference pref) {
    //startFragment(pref.getFragment(), pref.getExtras());
    Fragment fragment = getSupportFragmentManager()
            .getFragmentFactory()
            .instantiate(getClassLoader(), pref.getFragment());
    fragment.setArguments(pref.getExtras());
    getSupportFragmentManager().beginTransaction()
            .replace(R.id.content_frame, fragment)
            .addToBackStack(null)
            .commit();
    setTitle(pref.getTitle());
    return true;
  }

  /**
   * Starts a new Preferences activity showing the desired fragment.
   *
   * @param fragmentClass The Class of the fragment to show.
   * @param args Arguments to pass to Fragment.instantiate(), or null.
   */
  public void startFragment(String fragmentClass, Bundle args) {
    Intent intent = new Intent(Intent.ACTION_MAIN);
    intent.setClass(this, getClass());
    intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentClass);
    intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
    startActivity(intent);
  }

  @Override
  public boolean onNavigateUp() {
    Log.d("MICHAEL", "super!");
    return super.onNavigateUp();
  }

  public static class PrefsFragment extends PreferenceFragmentCompat implements
          Preference.OnPreferenceChangeListener,
          Preference.OnPreferenceClickListener {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {

      addPreferencesFromResource(R.xml.dialer_settings);

      if (showDisplayOptions()) {
        Preference displayOptions = new Preference(getContext());
        displayOptions.setTitle(R.string.display_options_title);
        displayOptions.setFragment(DisplayOptionsSettingsFragment.class.getName());
        getPreferenceScreen().addPreference(displayOptions);
      }

      Preference soundSettings = new Preference(getContext());
      soundSettings.setTitle(R.string.sounds_and_vibration_title);
      soundSettings.setFragment(SoundSettingsFragment.class.getName());
      soundSettings.setViewId(R.id.settings_header_sounds_and_vibration);
      getPreferenceScreen().addPreference(soundSettings);

      Preference quickResponseSettings = new Preference(getContext());
      Intent quickResponseSettingsIntent =
              new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
      quickResponseSettings.setTitle(R.string.respond_via_sms_setting_title);
      quickResponseSettings.setIntent(quickResponseSettingsIntent);
      getPreferenceScreen().addPreference(quickResponseSettings);

      final Preference lookupSettings = new Preference(getContext());
      lookupSettings.setTitle(R.string.lookup_settings_label);
      lookupSettings.setFragment(LookupSettingsFragment.class.getName());
      getPreferenceScreen().addPreference(lookupSettings);

      TelephonyManager telephonyManager =
              (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);

      // "Call Settings" (full settings) is shown if the current user is primary user and there
      // is only one SIM. Otherwise, "Calling accounts" is shown.
      boolean isPrimaryUser = isPrimaryUser();
      if (isPrimaryUser && TelephonyManagerCompat.getPhoneCount(telephonyManager) <= 1) {
        Preference callSettings = new Preference(getContext());
        Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
        callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        callSettings.setTitle(R.string.call_settings_label);
        callSettings.setIntent(callSettingsIntent);
        getPreferenceScreen().addPreference(callSettings);
      } else {
        Preference phoneAccountSettings = new Preference(getContext());
        Intent phoneAccountSettingsIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
        phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        phoneAccountSettings.setTitle(R.string.phone_account_settings_label);
        phoneAccountSettings.setIntent(phoneAccountSettingsIntent);
        getPreferenceScreen().addPreference(phoneAccountSettings);
      }
      if (BlockedNumberContract.canCurrentUserBlockNumbers(getContext())) {
        Preference blockedCallsHeader = new Preference(getContext());
        blockedCallsHeader.setTitle(R.string.manage_blocked_numbers_label);
        blockedCallsHeader.setIntent(getContext().getSystemService(TelecomManager.class)
                .createManageBlockedNumbersIntent());
        getPreferenceScreen().addPreference(blockedCallsHeader);
      }

      addVoicemailSettings(isPrimaryUser);

      if (isPrimaryUser
              && (TelephonyManagerCompat.isTtyModeSupported(telephonyManager)
              || TelephonyManagerCompat.isHearingAidCompatibilitySupported(telephonyManager))) {
        Preference accessibilitySettings = new Preference(getContext());
        Intent accessibilitySettingsIntent =
                new Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS);
        accessibilitySettings.setTitle(R.string.accessibility_settings_title);
        accessibilitySettings.setIntent(accessibilitySettingsIntent);
        getPreferenceScreen().addPreference(accessibilitySettings);
      }
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
      return false;
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
      return false;
    }

    @Override
    public void onResume() {
      super.onResume();
    }

    @Override
    public void onAttach(@NonNull Context context) {
      super.onAttach(context);
      Log.d("MICHAEL", "onAttach!");
    }

    @Override
    public void onNavigateToScreen(@NonNull PreferenceScreen preferenceScreen) {
      super.onNavigateToScreen(preferenceScreen);
      Log.d("MICHAEL", "onNavigateToScreen!");
    }

    private void addVoicemailSettings(boolean isPrimaryUser) {
      if (!isPrimaryUser) {
        LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "user not primary user");
        return;
      }

      if (!PermissionsUtil.hasReadPhoneStatePermissions(getContext())) {
        LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "Missing READ_PHONE_STATE");
        return;
      }

      LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "adding voicemail settings");
      Preference voicemailSettings = new Preference(getContext());
      voicemailSettings.setTitle(R.string.voicemail_settings_label);
      Bundle bundle = new Bundle();
      PhoneAccountHandle soleAccount = getSoleSimAccount();
      if (soleAccount == null) {
        LogUtil.i("DialerSettingsActivity.addVoicemailSettings",
                "showing multi-SIM voicemail settings");
        voicemailSettings.setFragment(PhoneAccountSelectionFragment.class.getName());
        bundle.putString(
                PhoneAccountSelectionFragment.PARAM_TARGET_FRAGMENT,
                VoicemailSettingsFragment.class.getName());
        bundle.putString(
                PhoneAccountSelectionFragment.PARAM_PHONE_ACCOUNT_HANDLE_KEY,
                VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE);
        bundle.putBundle(PhoneAccountSelectionFragment.PARAM_ARGUMENTS, new Bundle());
        bundle.putInt(PhoneAccountSelectionFragment.PARAM_TARGET_TITLE_RES,
                R.string.voicemail_settings_label);
      } else {
        LogUtil.i(
                "DialerSettingsActivity.addVoicemailSettings", "showing single-SIM voicemail settings");
        voicemailSettings.setFragment(VoicemailSettingsFragment.class.getName());
        bundle.putParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, soleAccount);
      }
      voicemailSettings.getExtras().putAll(bundle);
      getPreferenceScreen().addPreference(voicemailSettings);
    }

    /**
     * @return the only SIM phone account, or {@code null} if there are none or more than one. Note:
     * having a empty SIM slot still count as a PhoneAccountHandle that is "invalid", and
     * voicemail settings should still be available for it.
     */
    @SuppressLint("MissingPermission")
    @Nullable
    private PhoneAccountHandle getSoleSimAccount() {
      TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
      PhoneAccountHandle result = null;
      for (PhoneAccountHandle phoneAccountHandle : telecomManager.getCallCapablePhoneAccounts()) {
        PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
        if (phoneAccount == null) {
          continue;
        }
        if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
          LogUtil.i(
                  "DialerSettingsActivity.getSoleSimAccount", phoneAccountHandle + " is a SIM account");
          if (result != null) {
            return null;
          }
          result = phoneAccountHandle;
        }
      }
      return result;
    }

    /**
     * Returns {@code true} or {@code false} based on whether the display options setting should be
     * shown. For languages such as Chinese, Japanese, or Korean, display options aren't useful since
     * contacts are sorted and displayed family name first by default.
     *
     * @return {@code true} if the display options should be shown, {@code false} otherwise.
     */
    private boolean showDisplayOptions() {
      return getResources().getBoolean(R.bool.config_display_order_user_changeable)
              && getResources().getBoolean(R.bool.config_sort_order_user_changeable);
    }

    /*@Override
    public void onHeaderClick(Header header, int position) {
      if (header.id == R.id.settings_header_sounds_and_vibration) {
        // If we don't have the permission to write to system settings, go to system sound
        // settings instead. Otherwise, perform the super implementation (which launches our
        // own preference fragment.
        if (!Settings.System.canWrite(this)) {
          Toast.makeText(
                          this,
                          getResources().getString(R.string.toast_cannot_write_system_settings),
                          Toast.LENGTH_SHORT)
                  .show();
          startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
          return;
        }
      }
      super.onHeaderClick(header, position);
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == android.R.id.home) {
        getActivity().onBackPressed();
        return true;
      }
      return false;
    }

    /**
     * @return Whether the current user is the primary user.
     */
    private boolean isPrimaryUser() {
      return requireContext().getSystemService(UserManager.class).isSystemUser();
    }
  }
}
