/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.activity;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.Optional;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.DialogUtils;
import org.totschnig.myexpenses.dialog.MessageDialogFragment;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.dialog.TransactionDetailFragment;
import org.totschnig.myexpenses.fragment.DbWriteFragment;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.Model;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.preference.PrefHandler;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.ui.ContextWrapper;
import org.totschnig.myexpenses.ui.SnackbarAction;
import org.totschnig.myexpenses.util.PermissionHelper;
import org.totschnig.myexpenses.util.PermissionHelper.PermissionGroup;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.UiUtils;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.ads.AdHandlerFactory;
import org.totschnig.myexpenses.util.crashreporting.CrashHandler;
import org.totschnig.myexpenses.util.tracking.Tracker;
import org.totschnig.myexpenses.widget.AbstractWidget;

import java.io.Serializable;

import javax.inject.Inject;

import static org.totschnig.myexpenses.activity.ContribInfoDialogActivity.KEY_FEATURE;
import static org.totschnig.myexpenses.preference.PrefKey.GROUP_MONTH_STARTS;
import static org.totschnig.myexpenses.preference.PrefKey.GROUP_WEEK_STARTS;
import static org.totschnig.myexpenses.preference.PrefKey.HOME_CURRENCY;
import static org.totschnig.myexpenses.preference.PrefKey.PROTECTION_DEVICE_LOCK_SCREEN;
import static org.totschnig.myexpenses.preference.PrefKey.PROTECTION_LEGACY;
import static org.totschnig.myexpenses.preference.PrefKey.UI_FONTSIZE;
import static org.totschnig.myexpenses.preference.PrefKey.UI_LANGUAGE;
import static org.totschnig.myexpenses.preference.PrefKey.UI_THEME_KEY;
import static org.totschnig.myexpenses.task.TaskExecutionFragment.TASK_RESTORE;

public abstract class ProtectedFragmentActivity extends AppCompatActivity
    implements MessageDialogListener, OnSharedPreferenceChangeListener,
    ConfirmationDialogFragment.ConfirmationDialogListener,
    TaskExecutionFragment.TaskCallbacks, DbWriteFragment.TaskCallbacks,
    ProgressDialogFragment.ProgressDialogListener {
  public static final int CALCULATOR_REQUEST = 0;
  public static final int EDIT_TRANSACTION_REQUEST = 1;
  public static final int EDIT_ACCOUNT_REQUEST = 2;
  public static final int PREFERENCES_REQUEST = 3;
  public static final int CREATE_ACCOUNT_REQUEST = 4;
  public static final int FILTER_CATEGORY_REQUEST = 5;
  public static final int FILTER_COMMENT_REQUEST = 6;
  public static final int TEMPLATE_TITLE_REQUEST = 7;
  public static final int EDIT_SPLIT_REQUEST = 8;
  public static final int SELECT_CATEGORY_REQUEST = 9;
  public static final int PICK_COLOR_REQUEST = 11;
  public static final int PURCHASE_PREMIUM_REQUEST = 12;
  public static final int PICTURE_REQUEST_CODE = 14;
  public static final int IMPORT_FILENAME_REQUESTCODE = 15;
  public static final int SYNC_BACKEND_SETUP_REQUEST = 16;
  public static final int RESTORE_REQUEST = 17;
  public static final int CONTRIB_REQUEST = 18;
  public static final int PLAN_REQUEST = 19;
  private static final int CONFIRM_DEVICE_CREDENTIALS_UNLOCK_REQUEST = 20;
  protected static final int CONFIRM_DEVICE_CREDENTIALS_MANAGE_PROTECTION_SETTINGS_REQUEST = 21;
  public static final String SAVE_TAG = "SAVE_TASK";
  public static final String SORT_ORDER_USAGES = "USAGES";
  public static final String SORT_ORDER_LAST_USED = "LAST_USED";
  public static final String SORT_ORDER_AMOUNT = "AMOUNT";
  public static final String SORT_ORDER_TITLE = "TITLE";
  public static final String SORT_ORDER_CUSTOM = "CUSTOM";
  public static final String SORT_ORDER_NEXT_INSTANCE = "NEXT_INSTANCE";
  public static final int RESULT_RESTORE_OK = RESULT_FIRST_USER + 1;
  public static final String ACCOUNT_COLOR_DIALOG = "editColorDialog";

  public static final String ASYNC_TAG = "ASYNC_TASK";
  public static final String PROGRESS_TAG = "PROGRESS";

  private AlertDialog pwDialog;
  private boolean scheduledRestart = false;
  private Optional<Boolean> confirmCredentialResult = Optional.empty();
  public Enum<?> helpVariant = null;
  protected int colorExpense;
  protected int colorIncome;
  protected int colorAggregate;
  protected ColorStateList textColorSecondary;
  protected FloatingActionButton floatingActionButton;

  private Snackbar snackbar;

  @Inject
  protected Tracker tracker;

  @Inject
  protected CrashHandler crashHandler;

  @Inject
  protected AdHandlerFactory adHandlerFactory;

  @Inject
  protected PrefHandler prefHandler;

  public int getColorIncome() {
    return colorIncome;
  }

  public int getColorExpense() {
    return colorExpense;
  }

  public int getColorAggregate() {
    return colorAggregate;
  }

  public ColorStateList getTextColorSecondary() {
    return textColorSecondary;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    injectDependencies();
    if (MyApplication.getInstance().isProtected()) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
          WindowManager.LayoutParams.FLAG_SECURE);
    }
    MyApplication.getInstance().getSettings().registerOnSharedPreferenceChangeListener(this);
    Resources.Theme theme = getTheme();
    TypedValue color = new TypedValue();
    theme.resolveAttribute(R.attr.colorExpense, color, true);
    colorExpense = color.data;
    theme.resolveAttribute(R.attr.colorIncome, color, true);
    colorIncome = color.data;
    theme.resolveAttribute(R.attr.colorAggregate, color, true);
    colorAggregate = color.data;
    TypedArray themeArray = theme.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
    textColorSecondary = themeArray.getColorStateList(0);

    tracker.init(this);
  }

  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(ContextWrapper.wrap(newBase, MyApplication.getUserPreferedLocale()));
  }

  protected void injectDependencies() {
    MyApplication.getInstance().getAppComponent().inject(this);
  }

  @Override
  public void setContentView(int layoutResID) {
    super.setContentView(layoutResID);
  }

  protected void configureFloatingActionButton(int fabDescription) {
    if (!requireFloatingActionButtonWithContentDescription(getString(fabDescription))) return;
    TypedValue color = new TypedValue();
    getTheme().resolveAttribute(R.attr.colorControlActivated, color, true);
    UiUtils.setBackgroundTintListOnFab(floatingActionButton, color.data);
  }

  protected boolean requireFloatingActionButtonWithContentDescription(String fabDescription) {
    floatingActionButton = findViewById(R.id.CREATE_COMMAND);
    if (floatingActionButton == null) return false;
    floatingActionButton.setContentDescription(fabDescription);
    return true;
  }

  protected Toolbar setupToolbar(boolean withHome) {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (withHome) {
      final ActionBar actionBar = getSupportActionBar();
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
    return toolbar;
  }

  @Override
  protected void onPause() {
    super.onPause();
    MyApplication app = MyApplication.getInstance();
    if (app.isLocked() && pwDialog != null) {
      pwDialog.dismiss();
    } else {
      app.setLastPause(this);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    MyApplication.getInstance().getSettings().unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    crashHandler.addBreadcrumb(getClass().getSimpleName());
    if (scheduledRestart) {
      scheduledRestart = false;
      recreate();
    } else {
      if (confirmCredentialResult.isPresent()) {
        if (!confirmCredentialResult.get()) {
          moveTaskToBack(true);
        }
        confirmCredentialResult = Optional.empty();
      } else {
        MyApplication app = MyApplication.getInstance();
        if (app.shouldLock(this)) {
          confirmCredentials(CONFIRM_DEVICE_CREDENTIALS_UNLOCK_REQUEST, null, true);
        }
      }
    }
  }

  protected void confirmCredentials(int requestCode, DialogUtils.PasswordDialogUnlockedCallback legacyUnlockCallback, boolean shouldHideWindow) {
    if (Utils.hasApiLevel(Build.VERSION_CODES.LOLLIPOP) && PROTECTION_DEVICE_LOCK_SCREEN.getBoolean(false)) {
      Intent intent = ((KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE))
          .createConfirmDeviceCredentialIntent(null, null);
      if (intent != null) {
        if (shouldHideWindow) hideWindow();
        startActivityForResult(intent, requestCode);
      } else {
        showDeviceLockScreenWarning();
        if (legacyUnlockCallback != null) {
          legacyUnlockCallback.onPasswordDialogUnlocked();
        }
      }
    } else if (PROTECTION_LEGACY.getBoolean(true)) {
      if (shouldHideWindow) hideWindow();
      if (pwDialog == null) {
        pwDialog = DialogUtils.passwordDialog(this, false);
      }
      DialogUtils.showPasswordDialog(this, pwDialog, legacyUnlockCallback);
    }
  }

  public void showDeviceLockScreenWarning() {
    showSnackbar(
        Utils.concatResStrings(this, " ", R.string.warning_device_lock_screen_not_set_up_1, R.string.warning_device_lock_screen_not_set_up_2),
        Snackbar.LENGTH_LONG);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                        String key) {
    if (prefHandler.matches(key, UI_THEME_KEY, UI_LANGUAGE, UI_FONTSIZE, PROTECTION_LEGACY,
        PROTECTION_DEVICE_LOCK_SCREEN, GROUP_MONTH_STARTS, GROUP_WEEK_STARTS, HOME_CURRENCY)) {
      scheduledRestart = true;
    }
  }

  @Override
  public void onMessageDialogDismissOrCancel() {
  }

  @Override
  public void onProgressDialogDismiss() {
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.help, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Bundle bundle = new Bundle();
    int itemId = item.getItemId();
    if (itemId != 0) {
      String fullResourceName = getResources().getResourceName(itemId);
      bundle.putString(Tracker.EVENT_PARAM_ITEM_ID, fullResourceName.substring(fullResourceName.indexOf('/') + 1));
      logEvent(Tracker.EVENT_SELECT_MENU, bundle);
      if (dispatchCommand(itemId, null)) {
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public boolean dispatchCommand(int command, Object tag) {
    if (CommonCommands.dispatchCommand(this, command, tag)) {
      return true;
    }
    switch(command) {
      case R.id.GDPR_CONSENT_COMMAND: {
        adHandlerFactory.setConsent(true);
        return true;
      }
      case R.id.GDPR_NO_CONSENT_COMMAND: {
        adHandlerFactory.clearConsent();
        dispatchCommand(R.id.CONTRIB_INFO_COMMAND, null);
        return true;
      }
    }
    return false;
  }

  public void dispatchCommand(View v) {
    dispatchCommand(v.getId(), v.getTag());
  }

  @Override
  public void onPreExecute() {
  }

  @Override
  public void onProgressUpdate(Object progress) {
    FragmentManager m = getSupportFragmentManager();
    ProgressDialogFragment f = ((ProgressDialogFragment) m.findFragmentByTag(PROGRESS_TAG));
    if (f != null) {
      if (progress instanceof Integer) {
        f.setProgress((Integer) progress);
      } else if (progress instanceof String) {
        f.appendToMessage((String) progress);
      } else if (progress instanceof Result) {
        String print = ((Result) progress).print(this);
        if (print != null) {
          f.appendToMessage(print);
        }
      }
    }
  }

  @Override
  public void onCancelled() {
    removeAsyncTaskFragment(false);
  }

  protected boolean shouldKeepProgress(int taskId) {
    return false;
  }

  @Override
  public void onPostExecute(int taskId, Object o) {
    removeAsyncTaskFragment(shouldKeepProgress(taskId));
    switch (taskId) {
      case TaskExecutionFragment.TASK_DELETE_TRANSACTION:
      case TaskExecutionFragment.TASK_DELETE_ACCOUNT:
      case TaskExecutionFragment.TASK_DELETE_PAYMENT_METHODS:
      case TaskExecutionFragment.TASK_DELETE_CATEGORY:
      case TaskExecutionFragment.TASK_DELETE_PAYEES:
      case TaskExecutionFragment.TASK_DELETE_TEMPLATES:
      case TaskExecutionFragment.TASK_UNDELETE_TRANSACTION: {
        Result result = (Result) o;
        if (!result.isSuccess()) {
          showSnackbar("There was an error deleting the object. Please contact support@myexenses.mobi !",
              Snackbar.LENGTH_LONG);
        }
        break;
      }
      case TaskExecutionFragment.TASK_INSTANTIATE_TRANSACTION_2: {
        TransactionDetailFragment tdf = (TransactionDetailFragment)
            getSupportFragmentManager().findFragmentByTag(TransactionDetailFragment.class.getName());
        if (tdf != null) {
          tdf.fillData((Transaction) o);
        }
        break;
      }
      case TASK_RESTORE: {
        onPostRestoreTask(((Result) o));
        break;
      }
    }
  }

  protected void onPostRestoreTask(Result result) {
    if (result.isSuccess()) {
      MyApplication.getInstance().getLicenceHandler().reset();
      // if the backup is password protected, we want to force the password
      // check
      // is it not enough to set mLastPause to zero, since it would be
      // overwritten by the callings activity onpause
      // hence we need to set isLocked if necessary
      MyApplication.getInstance().resetLastPause();
      MyApplication.getInstance().shouldLock(this);
    }
  }

  @Override
  public Model getObject() {
    return null;
  }

  @Override
  public void onPostExecute(Object result) {
    FragmentManager m = getSupportFragmentManager();
    FragmentTransaction t = m.beginTransaction();
    t.remove(m.findFragmentByTag(SAVE_TAG));
    t.remove(m.findFragmentByTag(PROGRESS_TAG));
    t.commitAllowingStateLoss();
  }

  /**
   * starts the given task, only if no task is currently executed,
   * informs user through snackbar in that case
   *
   * @param taskId
   * @param objectIds
   * @param extra
   * @param progressMessage if 0 no progress dialog will be shown
   */
  public <T> void startTaskExecution(int taskId, T[] objectIds, Serializable extra,
                                     int progressMessage) {
    startTaskExecution(taskId, objectIds, extra, progressMessage, false);
  }
  public <T> void startTaskExecution(int taskId, T[] objectIds, Serializable extra,
                                     int progressMessage, boolean withButton) {
    FragmentManager m = getSupportFragmentManager();
    if (hasPendingTask(true)) {
      return;
    }
    //noinspection AndroidLintCommitTransaction
    FragmentTransaction ft = m.beginTransaction()
        .add(TaskExecutionFragment.newInstance(
            taskId,
            objectIds, extra),
            ASYNC_TAG);
    if (progressMessage != 0) {
      ft.add(ProgressDialogFragment.newInstance(progressMessage, withButton), PROGRESS_TAG);
    }
    ft.commit();
  }

  public boolean hasPendingTask(boolean shouldWarn) {
    FragmentManager m = getSupportFragmentManager();
    final boolean result = m.findFragmentByTag(ASYNC_TAG) != null;
    if (result && shouldWarn) {
      showSnackbar("Previous task still executing, please try again later",
          Snackbar.LENGTH_LONG);
    }
    return result;
  }

  private void showTaskNotFinishedWarning() {

  }

  public void startTaskExecution(int taskId, @NonNull Bundle extras, int progressMessage) {
    FragmentManager m = getSupportFragmentManager();
    if (hasPendingTask(true)) {
      return;
    }
    //noinspection AndroidLintCommitTransaction
    FragmentTransaction ft = m.beginTransaction()
        .add(TaskExecutionFragment.newInstanceWithBundle(extras, taskId),
            ASYNC_TAG);
    if (progressMessage != 0) {
      ft.add(ProgressDialogFragment.newInstance(progressMessage), PROGRESS_TAG);
    }
    ft.commit();
  }

  private void removeAsyncTaskFragment(boolean keepProgress) {
    FragmentManager m = getSupportFragmentManager();
    FragmentTransaction t = m.beginTransaction();
    ProgressDialogFragment f = ((ProgressDialogFragment) m.findFragmentByTag(PROGRESS_TAG));
    if (f != null) {
      if (keepProgress) {
        f.onTaskCompleted();
      } else {
        t.remove(f);
      }
    }
    t.remove(m.findFragmentByTag(ASYNC_TAG));
    t.commitAllowingStateLoss();
    //we might want to call a new task immediately after executing the last one
    m.executePendingTransactions();
  }

  public void startDbWriteTask(boolean returnSequenceCount) {
    getSupportFragmentManager().beginTransaction()
        .add(DbWriteFragment.newInstance(returnSequenceCount), SAVE_TAG)
        .add(ProgressDialogFragment.newInstance(R.string.progress_dialog_saving),
            PROGRESS_TAG)
        .commitAllowingStateLoss();
  }

  public void recordUsage(ContribFeature f) {
    f.recordUsage();
  }

  /**
   * Workaround for broken {@link NavUtils#shouldUpRecreateTask(android.app.Activity, Intent)}
   *
   * @param from
   * @return
   * @see <a href="http://stackoverflow.com/a/20643984/1199911">http://stackoverflow.com/a/20643984/1199911</a>
   */
  protected final boolean shouldUpRecreateTask(Activity from) {
    return from.getIntent().getBooleanExtra(AbstractWidget.EXTRA_START_FROM_WIDGET, false);
  }

  /*
   * @see android.support.v7.app.ActionBarActivity#onBackPressed()
   * https://code.google.com/p/android/issues/detail?id=25517
   */
  @Override
  public void onBackPressed() {
    try {
      super.onBackPressed();
    } catch (IllegalStateException e) {
      CrashHandler.report(e);
      finish();
    }
  }

  public void toggleCrStatus(View v) {
    Long id = (Long) v.getTag();
    if (id != -1) {
      startTaskExecution(
          TaskExecutionFragment.TASK_TOGGLE_CRSTATUS,
          new Long[]{id},
          null,
          0);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode,
                                  Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (requestCode == CONTRIB_REQUEST && intent != null) {
      ContribFeature contribFeature = ContribFeature.valueOf(intent.getStringExtra(KEY_FEATURE));
      if (resultCode == RESULT_OK) {
        ((ContribIFace) this).contribFeatureCalled(contribFeature,
            intent.getSerializableExtra(ContribInfoDialogActivity.KEY_TAG));
      } else if (resultCode == RESULT_CANCELED) {
        ((ContribIFace) this).contribFeatureNotCalled(contribFeature);
      }
    }
    if ((requestCode == PREFERENCES_REQUEST || requestCode == RESTORE_REQUEST) && resultCode == RESULT_RESTORE_OK) {
      restartAfterRestore();
    }
    if (requestCode == CONFIRM_DEVICE_CREDENTIALS_UNLOCK_REQUEST) {
      if (resultCode == RESULT_OK) {
        confirmCredentialResult = Optional.of(true);
        showWindow();
        MyApplication.getInstance().setLocked(false);
      } else {
        confirmCredentialResult = Optional.of(false);
      }
    }
  }

  protected void restartAfterRestore() {
    if (!isFinishing()) {
      Intent i = new Intent(this, MyExpenses.class);
      i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(i);
      finish();
    }
  }

  public void contribFeatureRequested(@NonNull ContribFeature feature, Serializable tag) {
    if (feature.hasAccess()) {
      ((ContribIFace) this).contribFeatureCalled(feature, tag);
    } else {
      CommonCommands.showContribDialog(this, feature, tag);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    boolean granted = PermissionHelper.allGranted(grantResults);
    storePermissionRequested(requestCode);
    if (granted) {
      switch (requestCode) {
        case PermissionHelper.PERMISSIONS_REQUEST_WRITE_CALENDAR: {
          MyApplication.getInstance().initPlanner();
        }
      }
    } else {
      if (permissions.length > 0 && ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
        showSnackbar(PermissionHelper.permissionRequestRationale(this, requestCode),
            Snackbar.LENGTH_LONG);
      }
    }
  }

  private void storePermissionRequested(int requestCode) {
    PermissionHelper.permissionRequestedKey(requestCode).putBoolean(true);
  }

  public boolean isCalendarPermissionPermanentlyDeclined() {
    return isPermissionPermanentlyDeclined(PermissionHelper.PermissionGroup.CALENDAR);
  }

  private boolean isPermissionPermanentlyDeclined(PermissionGroup permissionGroup) {
    if (prefHandler.getBoolean(permissionGroup.prefKey,false)) {
      if (!permissionGroup.hasPermission(this)) {
        if (!permissionGroup.shouldShowRequestPermissionRationale(this)) {
          return true;
        }
      }
    }
    return false;
  }

  public void requestCalendarPermission() {
    requestPermissionOrStartApplicationDetailSettings(PermissionGroup.CALENDAR);
  }

  public void requestStoragePermission() {
    requestPermissionOrStartApplicationDetailSettings(PermissionGroup.STORAGE);
  }

  private void requestPermissionOrStartApplicationDetailSettings(PermissionGroup permissionGroup) {
    if (isPermissionPermanentlyDeclined(permissionGroup)) {
      //noinspection InlinedApi
      Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      Uri uri = Uri.fromParts("package", getPackageName(), null);
      intent.setData(uri);
      startActivity(intent);
    } else {
      requestPermission(permissionGroup);
    }
  }

  public void requestPermission(PermissionGroup permissionGroup) {
    ActivityCompat.requestPermissions(this, permissionGroup.androidPermissions,
        permissionGroup.requestCode);
  }

  @Override
  public void onPositive(Bundle args) {
    dispatchCommand(args.getInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE), null);
  }

  protected void doRestore(Bundle args) {
    getSupportFragmentManager()
        .beginTransaction()
        .add(TaskExecutionFragment.newInstanceWithBundle(args, TASK_RESTORE), ASYNC_TAG)
        .add(ProgressDialogFragment.newInstance(R.string.pref_restore_title, true), PROGRESS_TAG).commit();
  }

  @Override
  public void onNegative(Bundle args) {
  }

  @Override
  public void onDismissOrCancel(Bundle args) {

  }

  public void setTrackingEnabled(boolean enabled) {
    tracker.setEnabled(enabled);
  }

  public void logEvent(String event, Bundle params) {
    tracker.logEvent(event, params);
  }

  @VisibleForTesting
  public Fragment getCurrentFragment() {
    return null;
  }

  public void hideWindow() {
    findViewById(android.R.id.content).setVisibility(View.GONE);
    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) actionBar.hide();
  }

  public void showWindow() {
    findViewById(android.R.id.content).setVisibility(View.VISIBLE);
    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) actionBar.show();
  }

  public void showDismissableSnackbar(int message) {
    showSnackbar(getText(message), Snackbar.LENGTH_INDEFINITE,
        new SnackbarAction(R.string.snackbar_dismiss, v -> snackbar.dismiss()));
  }

  public void showSnackbar(int message, int duration) {
    showSnackbar(getText(message), duration);
  }

  public void showSnackbar(@NonNull CharSequence message, int duration) {
    showSnackbar(message, duration, null);
  }

  public void showSnackbar(@NonNull CharSequence message, int duration, SnackbarAction snackbarAction) {
    showSnackbar(message, duration, snackbarAction, null);
  }

  public void showSnackbar(@NonNull CharSequence message, int duration, SnackbarAction snackbarAction, Snackbar.Callback callback) {
    View container = findViewById(getSnackbarContainerId());
    if (container == null) {
      CrashHandler.report(String.format("Class %s is unable to display snackbar", getClass()));
      Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    } else {
      snackbar = Snackbar.make(container, message, duration);
      View snackbarView = snackbar.getView();
      TextView textView = snackbarView.findViewById(android.support.design.R.id.snackbar_text);
      textView.setMaxLines(4);
      UiUtils.configureSnackbarForDarkTheme(snackbar);
      if (snackbarAction != null) {
        snackbar.setAction(snackbarAction.resId, snackbarAction.listener);
      }
      if (callback != null) {
        snackbar.addCallback(callback);
      }
      snackbar.show();
      snackbar.addCallback(new Snackbar.Callback());
    }
  }

  public void dismissSnackbar() {
    if (snackbar != null) {
      snackbar.dismiss();
    }
  }

  @IdRes
  protected int getSnackbarContainerId() {
    return R.id.fragment_container;
  }

  public void showMessage(int resId) {
    showMessage(getString(resId));
  }

  public void showMessage(CharSequence message) {
   showMessage(0, message);
  }

  public void showMessage(int title, CharSequence message) {
    MessageDialogFragment.newInstance(
        title,
        message,
        MessageDialogFragment.Button.okButton(),
        null, null)
        .show(getSupportFragmentManager(), "MESSAGE");
  }

  public void checkGdprConsent(boolean forceShow) {
    adHandlerFactory.gdprConsent(this, forceShow);
  }
}
