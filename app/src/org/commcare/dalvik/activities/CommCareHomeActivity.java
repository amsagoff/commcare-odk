package org.commcare.dalvik.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.adapters.HomeScreenAdapter;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.database.user.models.User;
import org.commcare.android.framework.BreadcrumbBarFragment;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.DumpTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.tasks.WipeTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.StorageUtils;
import org.commcare.android.view.HorizontalMediaView;
import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.AndroidShortcuts;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.NoLocalizedTextException;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.tasks.FormLoaderTask;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import android.text.Spannable;

import in.srain.cube.views.GridViewWithHeaderAndFooter;

public class CommCareHomeActivity extends CommCareActivity<CommCareHomeActivity> {
    private static final String TAG = CommCareHomeActivity.class.getSimpleName();

    public static final int LOGIN_USER = 0;
    public static final int GET_COMMAND = 1;
    public static final int GET_CASE = 2;
    public static final int MODEL_RESULT = 4;
    public static final int INIT_APP = 8;
    public static final int GET_INCOMPLETE_FORM = 16;
    public static final int GET_REFERRAL = 32;
    public static final int UPGRADE_APP = 64;
    public static final int REPORT_PROBLEM_ACTIVITY = 128;

    /**
     * Request code for automatically validating media from home dispatch.
     * Should signal a return from CommCareVerificationActivity.
     */
    public static final int MISSING_MEDIA_ACTIVITY=256;
    public static final int DUMP_FORMS_ACTIVITY=512;
    public static final int WIFI_DIRECT_ACTIVITY=1024;
    public static final int CONNECTION_DIAGNOSTIC_ACTIVITY=2048;
    public static final int PREFERENCES_ACTIVITY=4096;

    /**
     * Request code for launching media validator manually (Settings ->
     * Validate Media). Should signal a return from
     * CommCareVerificationActivity.
     */
    public static final int MEDIA_VALIDATOR_ACTIVITY=8192;

    public static final int USE_OLD_DIALOG = 1;
    public static final int DIALOG_CORRUPTED = 4;
    public static final int DIALOG_NO_STORAGE = 8;

    private static final int MENU_PREFERENCES = Menu.FIRST;
    private static final int MENU_UPDATE = Menu.FIRST + 1;
    private static final int MENU_CALL_LOG = Menu.FIRST + 2;
    private static final int MENU_REPORT_PROBLEM = Menu.FIRST + 3;
    private static final int MENU_VALIDATE_MEDIA = Menu.FIRST + 4;
    private static final int MENU_DUMP_FORMS = Menu.FIRST + 5;
    private static final int MENU_WIFI_DIRECT = Menu.FIRST + 6;
    private static final int MENU_CONNECTION_DIAGNOSTIC = Menu.FIRST + 7;
    private static final int MENU_SAVED_FORMS = Menu.FIRST + 8;

    /**
     * Restart is a special CommCare return code which means that the session was invalidated in the
     * calling activity and that the current session should be resynced
     */
    public static final int RESULT_RESTART = 3;

    public static int unsentFormNumberLimit;
    public static int unsentFormTimeLimit;

    public final static String UNSENT_FORM_NUMBER_KEY = "unsent-number-limit";
    public final static String UNSENT_FORM_TIME_KEY = "unsent-time-limit";

    public static final String SESSION_REQUEST = "ccodk_session_request";

    public static final String AIRPLANE_MODE_CATEGORY = "airplane-mode";
    
    // The API allows for external calls. When this occurs, redispatch to their
    // activity instead of commcare.
    boolean wasExternal = false;

    View homeScreen;

    private AndroidCommCarePlatform platform;

    AlertDialog mAskOldDialog;
    AlertDialog mAttemptFixDialog;
    SquareButtonWithNotification startButton;
    SquareButtonWithNotification logoutButton;
    SquareButtonWithNotification viewIncomplete;
    SquareButtonWithNotification syncButton;

    SquareButtonWithNotification viewOldForms;
    HomeScreenAdapter adapter;
    GridViewWithHeaderAndFooter gridView;
    ImageView topBannerImageView;
    int gridViewMargin;

    /*
         * (non-Javadoc)
         * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
         */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //This is a workaround required by Android Bug #2373, which is that Apps are launched from the
        //Google Play store and from the App launcher with different intent flags than everywhere else
        //in Android, which ruins the back stack and prevents the app from launching a high affinity
        //task.
        if (!isTaskRoot()) {
            Intent intent = getIntent();
            String action = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && action != null && action.equals(Intent.ACTION_MAIN)) {
                finish();
                return;
            }
        }


        if (savedInstanceState != null) {
            wasExternal = savedInstanceState.getBoolean("was_external");
        }

        setContentView(R.layout.mainnew_modern);
        adapter = new HomeScreenAdapter(this);
        View topBanner = View.inflate(this, R.layout.grid_header_top_banner, null);
        this.topBannerImageView = (ImageView)topBanner.findViewById(R.id.main_top_banner);
        gridView = (GridViewWithHeaderAndFooter)findViewById(R.id.home_gridview_buttons);
        gridView.addHeaderView(topBanner);
        gridView.setAdapter(adapter);
        gridView.requestLayout();
        adapter.notifyDataSetChanged(); // is going to populate the grid with buttons from the adapter (hardcoded there)
        gridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {
                if (adapter.getItem(0) == null) {
                    Log.e("configUi", "Items still not instantiated by gridView, configUi is going to crash!");
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    gridView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    gridView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                configUi();
            }
        });

    }

    @SuppressLint("NewApi")
    private void configUi() {
        TextView version = (TextView)findViewById(R.id.str_version);
        if (version != null) version.setText(CommCareApplication._().getCurrentVersionString());

        // enter data button. expects a result.

        startButton = adapter.getButton(R.layout.home_start_button, false);
        if (startButton == null) {
            Log.d("buttons", "startButton is null! Crashing!");
        }

        if (startButton != null) {
            startButton.setText(Localization.get("home.start"));
        }
        View.OnClickListener startListener = new OnClickListener() {
            public void onClick(View v) {
                Intent i;
                if (DeveloperPreferences.isGridMenuEnabled()) {
                    i = new Intent(getApplicationContext(), MenuGrid.class);
                } else {
                    i = new Intent(getApplicationContext(), MenuList.class);
                }
                startActivityForResult(i, GET_COMMAND);
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_start_button, false, startListener);


        // enter data button. expects a result.
        viewIncomplete = adapter.getButton(R.layout.home_incompleteforms_button, false);

        if (viewIncomplete != null) {
            setIncompleteFormsText(CommCareApplication._().getSyncDisplayParameters());
        }
        View.OnClickListener viewIncompleteListener = new OnClickListener() {
            public void onClick(View v) {
                goToFormArchive(true);
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_incompleteforms_button, false, viewIncompleteListener);


        logoutButton = adapter.getButton(R.layout.home_disconnect_button, false);
        if (logoutButton == null) {
            Log.d("buttons", "logoutButton is null! Crashing!");
        }

        if (logoutButton != null) {
            logoutButton.setText(Localization.get("home.logout"));
        }
        View.OnClickListener logoutButtonListener = new OnClickListener() {
            public void onClick(View v) {
                try {
                    CommCareApplication._().getSession().closeSession(false);
                } catch (SessionUnavailableException e) {
                    // session expired, so re-login's probably been triggered
                    return;
                }
                returnToLogin(null);
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_disconnect_button, false, logoutButtonListener);
        if (logoutButton != null) {
            logoutButton.setNotificationText(getActivityTitle());
            adapter.notifyDataSetChanged();
        }


        TextView formGroupLabel = (TextView)findViewById(R.id.home_formrecords_label);
        if (formGroupLabel != null) {
            formGroupLabel.setText(Localization.get("home.forms"));
        }

        viewOldForms = adapter.getButton(R.layout.home_savedforms_button, false);
        if (viewOldForms == null) {
            Log.d("buttons", "viewOldForms is null! Crashing!");
        }

        if (viewOldForms != null) {
            viewOldForms.setText(Localization.get("home.forms.saved"));
        }
        View.OnClickListener viewOldFormsListener = new OnClickListener() {
            public void onClick(View v) {
                goToFormArchive(false);
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_savedforms_button, false, viewOldFormsListener);


        syncButton = adapter.getButton(R.layout.home_sync_button, false);
        if (syncButton == null) {
            Log.d("buttons", "syncButton is null! Crashing!");
        }

        if (syncButton != null)
            setSyncButtonText(CommCareApplication._().getSyncDisplayParameters(), null);
        View.OnClickListener syncButtonListener = new OnClickListener() {
            public void onClick(View v) {
                if (!isOnline()) {
                    if (isAirplaneModeOn()) {
                        displayMessage(Localization.get("notification.sync.airplane.action"), true, true);
                        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_AirplaneMode, AIRPLANE_MODE_CATEGORY));
                    } else {
                        displayMessage(Localization.get("notification.sync.connections.action"), true, true);
                        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_NoConnections, AIRPLANE_MODE_CATEGORY));
                    }
                    return;
                }

                CommCareApplication._().clearNotifications(AIRPLANE_MODE_CATEGORY);

                boolean formsSentToServer = false;
                try {
                    formsSentToServer = checkAndStartUnsentTask(true);
                } catch (SessionUnavailableException e) {
                    // Session is expired, stop using the user DB.
                    return;
                }

                if(!formsSentToServer) {
                    // No forms needed to be sent to the server, so let's just
                    // trigger a data sync.
                    syncData(false);
                }
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_sync_button, false, syncButtonListener);


        // CommCare-159047: this method call rebuilds the options menu
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            invalidateOptionsMenu();
        } else {
            supportInvalidateOptionsMenu();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }

    private void goToFormArchive(boolean incomplete) {
        goToFormArchive(incomplete, null);
    }

    private void goToFormArchive(boolean incomplete, FormRecord record) {
        Intent i = new Intent(getApplicationContext(), FormRecordListActivity.class);
        if (incomplete) {
            i.putExtra(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE);
        }
        if (record != null) {
            i.putExtra(FormRecordListActivity.KEY_INITIAL_RECORD_ID, record.getID());
        }
        startActivityForResult(i, GET_INCOMPLETE_FORM);
    }

    private void syncData(boolean formsToSend) {
        User u;
        try {
            u = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }
        
        if(User.TYPE_DEMO.equals(u.getUserType())) {
            //Remind the user that there's no syncing in demo mode.0
            if (formsToSend) {
                displayMessage(Localization.get("main.sync.demo.has.forms"), true, true);
            } else {
                displayMessage(Localization.get("main.sync.demo.no.forms"), true, true);
            }
            return;
        }
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        DataPullTask<CommCareHomeActivity> mDataPullTask = new DataPullTask<CommCareHomeActivity>(u.getUsername(), u.getCachedPwd(), prefs.getString("ota-restore-url", this.getString(R.string.ota_restore_url)), "", this) {

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverResult(java.lang.Object, java.lang.Object)
             */
            @Override
            protected void deliverResult(CommCareHomeActivity receiver, Integer result) {
                receiver.refreshView();

                //TODO: SHARES _A LOT_ with login activity. Unify into service
                switch (result) {
                    case DataPullTask.AUTH_FAILED:
                        receiver.displayMessage(Localization.get("sync.fail.auth.loggedin"), true);
                        break;
                    case DataPullTask.BAD_DATA:
                        receiver.displayMessage(Localization.get("sync.fail.bad.data"), true);
                        break;
                    case DataPullTask.DOWNLOAD_SUCCESS:
                        receiver.displayMessage(Localization.get("sync.success.synced"));
                        break;
                    case DataPullTask.SERVER_ERROR:
                        receiver.displayMessage(Localization.get("sync.fail.server.error"));
                        break;
                    case DataPullTask.UNREACHABLE_HOST:
                        receiver.displayMessage(Localization.get("sync.fail.bad.network"), true);
                        break;
                    case DataPullTask.CONNECTION_TIMEOUT:
                        receiver.displayMessage(Localization.get("sync.fail.timeout"), true);
                        break;
                    case DataPullTask.UNKNOWN_FAILURE:
                        receiver.displayMessage(Localization.get("sync.fail.unknown"), true);
                        break;
                }
                //TODO: What if the user info was updated?

            }

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverUpdate(java.lang.Object, java.lang.Object[])
             */
            @Override
            protected void deliverUpdate(CommCareHomeActivity receiver, Integer... update) {
                if (update[0] == DataPullTask.PROGRESS_STARTED) {
                    receiver.updateProgress(Localization.get("sync.progress.purge"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_CLEANED) {
                    receiver.updateProgress(Localization.get("sync.progress.authing"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_AUTHED) {
                    receiver.updateProgress(Localization.get("sync.progress.downloading"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_DOWNLOADING) {
                    receiver.updateProgress(Localization.get("sync.process.downloading.progress", new String[]{String.valueOf(update[1])}), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_PROCESSING) {
                    receiver.updateProgress(Localization.get("sync.process.processing", new String[]{String.valueOf(update[1]), String.valueOf(update[2])}), DataPullTask.DATA_PULL_TASK_ID);
                    receiver.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
                    receiver.updateProgress(Localization.get("sync.recover.needed"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_RECOVERY_STARTED) {
                    receiver.updateProgress(Localization.get("sync.recover.started"), DataPullTask.DATA_PULL_TASK_ID);
                }
            }

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverError(java.lang.Object, java.lang.Exception)
             */
            @Override
            protected void deliverError(CommCareHomeActivity receiver,
                                        Exception e) {
                receiver.displayMessage(Localization.get("sync.fail.unknown"), true);
            }

        };

        mDataPullTask.connect(this);

        mDataPullTask.execute();
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("was_external", wasExternal);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        if (inState.containsKey("was_external")) {
            wasExternal = inState.getBoolean("was_external");
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(resultCode == RESULT_RESTART) {
            try {
                startNextFetch();
            } catch (SessionUnavailableException e) {
                // session has expired and re-login intent has
                // probably already been triggered
            }
            return;
        }
        
        try {
            // if handling new return code (want to return to home screen) but a return at the end of your statement
            switch(requestCode) {
            case INIT_APP:
                if(resultCode == RESULT_CANCELED) {
                    //quit somehow.
                    this.finish();
                    return;
                } else if(resultCode == RESULT_OK) {
                    //CTS - Removed a call to initializing resources here. The engine takes care of that.
                    //We do, however, need to re-init this screen to include new translations
                    configUi();
                    return;
                }
                break;
            case UPGRADE_APP:
                if(resultCode == RESULT_CANCELED) {
                    //This might actually be bad, but try to go about your business
                    //The onResume() will take us to the screen
                    return;
                } else if(resultCode == RESULT_OK) {
                    if(intent.getBooleanExtra(CommCareSetupActivity.KEY_REQUIRE_REFRESH, true)) {
                        Toast.makeText(this, Localization.get("update.success.refresh"), Toast.LENGTH_LONG).show();
                        try {
                            CommCareApplication._().getSession().closeSession(false);
                        } catch (SessionUnavailableException e) {
                            // if the session isn't available, we don't need to logout
                        }
                    }
                    return;
                }
                break;
            case PREFERENCES_ACTIVITY:
                configUi();
                return;
            case MEDIA_VALIDATOR_ACTIVITY:
                if(resultCode == RESULT_CANCELED){
                    return;
                } else if (resultCode == RESULT_OK){
                    Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
                    return;
                }
            case MISSING_MEDIA_ACTIVITY:
                if(resultCode == RESULT_CANCELED){
                    // exit the app if media wasn't validated on automatic
                    // validation check.
                    this.finish();
                    return;
                } else if(resultCode == RESULT_OK){
                    Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
                    return;
                }
            case DUMP_FORMS_ACTIVITY:
                if(resultCode == RESULT_CANCELED){
                    return;
                }
                else if(resultCode == DumpTask.BULK_DUMP_ID){
                    int dumpedCount = intent.getIntExtra(CommCareFormDumpActivity.KEY_NUMBER_DUMPED, -1);
                    
                    displayMessage(Localization.get("bulk.form.dump.success",new String[] {""+dumpedCount}), false, false);
                    
                    refreshView();
                    return;
                }
                else if(resultCode == SendTask.BULK_SEND_ID){
                    int dumpedCount = intent.getIntExtra(CommCareFormDumpActivity.KEY_NUMBER_DUMPED, -1);
                    
                    displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);
                    
                    Toast.makeText(this, Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}), Toast.LENGTH_LONG).show();
                    refreshView();
                    return;
                }
            case CONNECTION_DIAGNOSTIC_ACTIVITY:
                return;
            case WIFI_DIRECT_ACTIVITY:
                if(resultCode == RESULT_CANCELED){
                    return;
                }
                else if(resultCode == SendTask.BULK_SEND_ID){
                    int dumpedCount = intent.getIntExtra(CommCareWiFiDirectActivity.KEY_NUMBER_DUMPED, -1);
                    
                    displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);
                    
                    Toast.makeText(this, "Forms successfully submitted.", Toast.LENGTH_LONG).show();
                    refreshView();
                    return;
                } else if(resultCode == WipeTask.WIPE_TASK_ID){
                    int dumpedCount = intent.getIntExtra(CommCareWiFiDirectActivity.KEY_NUMBER_DUMPED, -1);
                    
                    displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);
                    
                    Toast.makeText(this, "Forms successfully submitted.", Toast.LENGTH_LONG).show();
                    refreshView();
                    return;
                }
            case REPORT_PROBLEM_ACTIVITY:
                if(resultCode == RESULT_CANCELED) {
                    return;
                }
                else if(resultCode == RESULT_OK){
                    CommCareApplication._().notifyLogsPending();
                    refreshView();
                    return;    
                }
            case LOGIN_USER:
                if(resultCode == RESULT_CANCELED) {
                    //quit somehow.
                    this.finish();
                    return;
                } else if(resultCode == RESULT_OK) {
                    if(intent.getBooleanExtra(LoginActivity.ALREADY_LOGGED_IN, false)) {
                        //If we were already logged in just roll with it.
                        //The onResume() will take us to the screen
                    } else {
                        refreshView();
                        
                        //Unless we're about to sync (which will handle this
                        //in a blocking fashion), trigger off a regular unsent
                        //task processor
                        if(!CommCareApplication._().isSyncPending(false)) {
                            checkAndStartUnsentTask(false);
                        }
                        
                        if(isDemoUser()) {
                            showDemoModeWarning();
                        }
                    }
                    return;
                }
                break;
                
            case GET_INCOMPLETE_FORM:
                //TODO: We might need to load this from serialized state?
                AndroidSessionWrapper currentState = CommCareApplication._().getCurrentSessionWrapper();
                
                if(resultCode == RESULT_CANCELED) {
                    refreshView();
                    return;
                }
                else if(resultCode == RESULT_OK) {
                    int record = intent.getIntExtra("FORMRECORDS", -1);
                    if(record == -1) {
                        //Hm, what to do here?
                        break;
                    }
                    FormRecord r = CommCareApplication._().getUserStorage(FormRecord.class).read(record);
                    
                    //Retrieve and load the appropriate ssd
                    SqlStorage<SessionStateDescriptor> ssdStorage = CommCareApplication._().getUserStorage(SessionStateDescriptor.class);
                    Vector<Integer> ssds = ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, r.getID());
                    if(ssds.size() == 1) {
                        currentState.loadFromStateDescription(ssdStorage.read(ssds.firstElement()));
                    } else {
                        currentState.setFormRecordId(r.getID());
                    }

                    if (platform == null &&
                            CommCareApplication._().getCurrentApp() != null) {
                        platform = CommCareApplication._().getCommCarePlatform();
                    }
                    formEntry(platform.getFormContentUri(r.getFormNamespace()), r);
                    return;
                }
                break;
            case GET_COMMAND:
                //TODO: We might need to load this from serialized state?
                currentState = CommCareApplication._().getCurrentSessionWrapper();
                if(resultCode == RESULT_CANCELED) {
                    if(currentState.getSession().getCommand() == null) {
                        //Needed a command, and didn't already have one. Stepping back from
                        //an empty state, Go home!
                        currentState.reset();
                        refreshView();
                        return;
                    } else {
                        currentState.getSession().stepBack();
                        break;
                    }
                } else if(resultCode == RESULT_OK) {
                    //Get our command, set it, and continue forward
                    String command = intent.getStringExtra(SessionFrame.STATE_COMMAND_ID);
                    currentState.getSession().setCommand(command);
                    break;
                }
                break;
            case GET_CASE:
                //TODO: We might need to load this from serialized state?
                currentState = CommCareApplication._().getCurrentSessionWrapper();
                if(resultCode == RESULT_CANCELED) {
                    currentState.getSession().stepBack();
                    break;
                } else if(resultCode == RESULT_OK) {
                    currentState.getSession().setDatum(currentState.getSession().getNeededDatum().getDataId(), intent.getStringExtra(SessionFrame.STATE_DATUM_VAL));
                    break;
                }
            case MODEL_RESULT:
                boolean fetchNext = processReturnFromFormEntry(resultCode, intent);
                if (!fetchNext) {
                    return;
                }
                break;
            }

            startNextFetch();
        } catch (SessionUnavailableException sue) {
            //TODO: Cache current return, login, and try again
            returnToLogin();
        }
        super.onActivityResult(requestCode, resultCode, intent);

    }

    /**
     * Process user returning home from the form entry activity.
     * Triggers form submission cycle, cleans up some session state.
     *
     * @param resultCode exit code of form entry activity
     * @param intent     The intent of the returning activity, with the
     *                   saved form provided as the intent URI data. Null if
     *                   the form didn't exit cleanly
     * @return Flag signifying that caller should fetch the next activity in
     * the session to launch. If false then caller should exit or spawn home
     * activity.
     */
    private boolean processReturnFromFormEntry(int resultCode, Intent intent) throws SessionUnavailableException {
        // TODO: We might need to load this from serialized state?
        AndroidSessionWrapper currentState = CommCareApplication._().getCurrentSessionWrapper();

        // This is the state we were in when we _Started_ form entry
        FormRecord current = currentState.getFormRecord();

        if (current == null) {
            // somehow we lost the form record for the current session
            // TODO: how should this be handled? -- PLM
            Toast.makeText(this,
                    "Error while trying to save the form!",
                    Toast.LENGTH_LONG).show();
            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                    "Form Entry couldn't save because of corrupt state.");
            clearSessionAndExit(currentState);
            return false;
        }

        // TODO: This should be the default unless we're in some "Uninit" or "incomplete" state
        if ((intent != null && intent.getBooleanExtra(FormEntryActivity.IS_ARCHIVED_FORM, false)) ||
                FormRecord.STATUS_COMPLETE.equals(current.getStatus()) ||
                FormRecord.STATUS_SAVED.equals(current.getStatus())) {
            // Viewing an old form, so don't change the historical record
            // regardless of the exit code
            currentState.reset();
            if (wasExternal) {
                this.finish();
            } else {
                // Return to where we started
                goToFormArchive(false, current);
            }
            return false;
        }

        if (resultCode == RESULT_OK) {
            // Determine if the form instance is complete
            // TODO: refactor this into a method -- PLM
            Uri resultInstanceURI = intent.getData();
            if (resultInstanceURI == null) {
                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.FormEntry_Unretrievable));
                Toast.makeText(this,
                        "Error while trying to read the form! See the notification",
                        Toast.LENGTH_LONG).show();
                Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                        "Form Entry did not return a form");
                clearSessionAndExit(currentState);
                return false;
            }

            Cursor c = getContentResolver().query(resultInstanceURI, null, null, null, null);
            if (!c.moveToFirst()) {
                throw new IllegalArgumentException("Empty query for instance record!");
            }
            String instanceStatus = c.getString(c.getColumnIndexOrThrow(InstanceProviderAPI.InstanceColumns.STATUS));
            // was the record marked complete?
            boolean complete = InstanceProviderAPI.STATUS_COMPLETE.equals(instanceStatus);

            // The form is either ready for processing, or not, depending on how it was saved
            if (complete) {
                // We're honoring in order submissions, now, so trigger a full
                // submission cycle
                checkAndStartUnsentTask(false);

                refreshView();

                if (wasExternal) {
                    this.finish();
                    return false;
                }

                // XXX: probably refactor part of this logic into InstanceProvider -- PLM
                // Before we can terminate the session, we need to know that the form has been processed
                // in case there is state that depends on it.
                if (!currentState.terminateSession()) {
                    // If we didn't find somewhere to go, we're gonna stay here
                    return false;
                }
                // Otherwise, we want to keep proceeding in order
                // to keep running the workflow
            } else {
                // Form record is now stored.
                // TODO: session state clearing might be something we want to
                // do in InstanceProvider.bindToFormRecord.
                clearSessionAndExit(currentState);
                return false;
            }
        } else if (resultCode == RESULT_CANCELED) {
            // Nothing was saved during the form entry activity

            Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Form Entry Cancelled");

            // If the form was unstarted, we want to wipe the record.
            if (current.getStatus() == FormRecord.STATUS_UNSTARTED) {
                // Entry was cancelled.
                FormRecordCleanupTask.wipeRecord(this, currentState);
            }

            if (wasExternal) {
                currentState.reset();
                this.finish();
                return false;
            } else if (current.getStatus().equals(FormRecord.STATUS_INCOMPLETE)) {
                currentState.reset();
                // We should head back to the incomplete forms screen
                goToFormArchive(true, current);
                return false;
            } else {
                // If we cancelled form entry from a normal menu entry
                // we want to go back to where were were right before we started
                // entering the form.
                currentState.getSession().stepBack();
                currentState.setFormRecordId(-1);
            }
        }
        return true;
    }

    /**
     * clear local state in session session, and finish if was external is set,
     * otherwise refesh the view.
     */
    private void clearSessionAndExit(AndroidSessionWrapper currentState) {
        currentState.reset();
        if (wasExternal) {
            this.finish();
        }
        refreshView();
    }


    private void showDemoModeWarning() {
        //TODO: How do we style this to "light"?
        AlertDialog demoModeWarning = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Light)).setInverseBackgroundForced(true).create();
        demoModeWarning.setTitle(Localization.get("demo.mode.warning.title"));

        DialogInterface.OnClickListener demoModeWarningListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1:
                        //Nothing, dismiss
                        break;
                }

            }
        };
        demoModeWarning.setCancelable(false);
        demoModeWarning.setButton(Localization.get("demo.mode.warning.dismiss"), demoModeWarningListener);


        String path = null;
        try {
            path = Localization.get("demo.warning.filepath");
        } catch (NoLocalizedTextException nlte) {

        }


        HorizontalMediaView tiav = new HorizontalMediaView(this);
        tiav.setAVT(Localization.get("demo.mode.warning"), path, null);
        demoModeWarning.setView(tiav);

        demoModeWarning.show();
    }

    private void createErrorDialog(String errorMsg, AlertDialog.OnClickListener errorListener) {
        AlertDialog mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setTitle(Localization.get("app.handled.error.title"));
        mAlertDialog.setMessage(errorMsg);
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(Localization.get("dialog.ok"), errorListener);
        mAlertDialog.show();
    }

    private void startNextFetch() throws SessionUnavailableException {
        //TODO: feels like this logic should... not be in a big disgusting ifghetti.
        //Interface out the transitions, maybe?

        final CommCareSession session = CommCareApplication._().getCurrentSession();
        String needed = session.getNeededData();
        StackFrameStep lastPopped = session.getPoppedStep();

        if (needed == null) {
            EvaluationContext ec = session.getEvaluationContext(new CommCareInstanceInitializer(session));
            //See if we failed any of our asseertions
            Text text = session.getCurrentEntry().getAssertions().getAssertionFailure(ec);
            if (text != null) {
                createErrorDialog(text.evaluate(ec), new DialogInterface.OnClickListener() {
                    /*
                     * (non-Javadoc)
                     * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                     */
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        session.stepBack();
                        try {
                            CommCareHomeActivity.this.startNextFetch();
                        } catch (SessionUnavailableException e) {
                            // session has expired and re-login intent has
                            // probably already been triggered
                        }
                    }
                });
                return;
            }
            startFormEntry(CommCareApplication._().getCurrentSessionWrapper());
        } else if (needed == SessionFrame.STATE_COMMAND_ID) {
            Intent i;

            if (DeveloperPreferences.isGridMenuEnabled()) {
                i = new Intent(getApplicationContext(), MenuGrid.class);
            } else {
                i = new Intent(getApplicationContext(), MenuList.class);
            }

            i.putExtra(SessionFrame.STATE_COMMAND_ID, session.getCommand());
            startActivityForResult(i, GET_COMMAND);
        } else if (needed == SessionFrame.STATE_DATUM_VAL) {
            Intent i = new Intent(getApplicationContext(), EntitySelectActivity.class);

            i.putExtra(SessionFrame.STATE_COMMAND_ID, session.getCommand());
            if (lastPopped != null && SessionFrame.STATE_DATUM_VAL.equals(lastPopped.getType())) {
                i.putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, lastPopped.getValue());
            }

            startActivityForResult(i, GET_CASE);
        } else if (needed == SessionFrame.STATE_DATUM_COMPUTED) {
            //compute
            SessionDatum datum = session.getNeededDatum();
            XPathExpression form;
            try {
                form = XPathParseTool.parseXPath(datum.getValue());
            } catch (XPathSyntaxException e) {
                //TODO: What.
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            }
            EvaluationContext ec = session.getEvaluationContext(new CommCareInstanceInitializer(session));
            if (datum.getType() == SessionDatum.DATUM_TYPE_FORM) {
                session.setXmlns(XPathFuncExpr.toString(form.eval(ec)));
                session.setDatum("", "awful");
            } else {
                try {
                    session.setDatum(datum.getDataId(), XPathFuncExpr.toString(form.eval(ec)));
                } catch (XPathException e) {
                    displayException(e);
                    return;
                }
            }
            startNextFetch();
        }

        if (lastPopped != null) {
            //overridePendingTransition(R.anim.push_right_in, R.anim.push_right_out);
        }
    }

    /**
     * Create (or re-use) a form record and pass it to the form entry activity
     * launcher. If there is an existing incomplete form that uses the same
     * case, ask the user if they want to edit or delete that one.
     *
     * @param state Needed for FormRecord manipulations
     */
    private void startFormEntry(AndroidSessionWrapper state) throws SessionUnavailableException {
            if (state.getFormRecordId() == -1) {
                if (CommCarePreferences.isIncompleteFormsEnabled()) {
                    // Are existing (incomplete) forms using the same case?
                    SessionStateDescriptor existing =
                        state.getExistingIncompleteCaseDescriptor();

                    if (existing != null) {
                        // Ask user if they want to just edit existing form that
                        // uses the same case.
                        createAskUseOldDialog(state, existing);
                        return;
                    }
                }

                // Generate a stub form record and commit it
                state.commitStub();
            } else {
                Logger.log("form-entry", "Somehow ended up starting form entry with old state?");
            }

            FormRecord record = state.getFormRecord();

            if (platform == null &&
                    CommCareApplication._().getCurrentApp() != null) {
                platform = CommCareApplication._().getCommCarePlatform();
            }

            formEntry(platform.getFormContentUri(record.getFormNamespace()), record, CommCareActivity.getTitle(this, null));
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#getActivityTitle()
     */
    @Override
    public String getActivityTitle() {
        String userName = null;

        try {
            userName = CommCareApplication._().getSession().getLoggedInUser().getUsername();
            if (userName != null) {
                return Localization.get("home.logged.in.message", new String[]{userName});
            }
        } catch (Exception e) {
            //TODO: Better catch, here
        }
        return "";
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#isTopNavEnabled()
     */
    @Override
    protected boolean isTopNavEnabled() {
        return false;
    }

    private void formEntry(Uri formUri, FormRecord r) throws SessionUnavailableException {
        formEntry(formUri, r, null);
    }

    private void formEntry(Uri formUri, FormRecord r, String headerTitle) throws SessionUnavailableException {
        Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Form Entry Starting|" + r.getFormNamespace());


        //TODO: This is... just terrible. Specify where external instance data should come from
        FormLoaderTask.iif = new CommCareInstanceInitializer(CommCareApplication._().getCurrentSession());

        //Create our form entry activity callout
        Intent i = new Intent(getApplicationContext(), FormEntryActivity.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra("odk_title_fragment", BreadcrumbBarFragment.class.getName());

        i.putExtra("instancedestination", CommCareApplication._().getCurrentApp().fsPath((GlobalConstants.FILE_CC_FORMS)));
        
        // See if there's existing form data that we want to continue entering
        // (note, this should be stored in the form record as a URI link to
        // the instance provider in the future)
        if(r.getInstanceURI() != null) {
            i.setData(r.getInstanceURI());
        } else {
            i.setData(formUri);
        }

        i.putExtra("org.odk.collect.resizing.enabled", CommCarePreferences.getResizeMethod());

        i.putExtra("org.odk.collect.form.management", CommCarePreferences.isIncompleteFormsEnabled());

        i.putExtra("readonlyform", FormRecord.STATUS_SAVED.equals(r.getStatus()));

        i.putExtra("key_aes_storage", Base64.encodeToString(r.getAesKey(), Base64.DEFAULT));

        i.putExtra("form_content_uri", FormsProviderAPI.FormsColumns.CONTENT_URI.toString());
        i.putExtra("instance_content_uri", InstanceProviderAPI.InstanceColumns.CONTENT_URI.toString());
        if (headerTitle != null) {
            i.putExtra("form_header", headerTitle);
        }

        startActivityForResult(i, MODEL_RESULT);
    }

    /**
     * @return Were forms sent to the server by this method invocation?
     */
    protected boolean checkAndStartUnsentTask(final boolean syncAfterwards) throws SessionUnavailableException {
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        FormRecord[] records = StorageUtils.getUnsentRecords(storage);

        if(records.length > 0) {
            processAndSend(records, syncAfterwards);
            return true;
        } else {
            //Nothing.
            return false;
        }
    }

    private String getFormPostURL() {
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        return settings.getString("PostURL", this.getString(R.string.PostURL));
    }

    @SuppressLint("NewApi")
    private void processAndSend(FormRecord[] records, final boolean syncAfterwards) {

        int sendTaskId = syncAfterwards ? ProcessAndSendTask.SEND_PHASE_ID : -1;

        ProcessAndSendTask<CommCareHomeActivity> mProcess = new ProcessAndSendTask<CommCareHomeActivity>(this, getFormPostURL(),
                sendTaskId, syncAfterwards) {

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverResult(java.lang.Object, java.lang.Object)
             */
            @Override
            protected void deliverResult(CommCareHomeActivity receiver, Integer result) {
                if (result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {
                    returnToLogin(Localization.get("app.workflow.login.lost"));
                    return;
                }
                receiver.refreshView();

                int successfulSends = this.getSuccesfulSends();

                if (result == FormUploadUtil.FULL_SUCCESS) {
                    String label = Localization.get("sync.success.sent.singular", new String[]{String.valueOf(successfulSends)});
                    if (successfulSends > 1) {
                        label = Localization.get("sync.success.sent", new String[]{String.valueOf(successfulSends)});
                    }
                    receiver.displayMessage(label);

                    if (syncAfterwards) {
                        syncData(true);
                    }
                } else if (result == FormUploadUtil.FAILURE) {
                    //Failures make their own notification box
                } else {
                    receiver.displayMessage(Localization.get("sync.fail.unsent"), true);
                }

            }

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverUpdate(java.lang.Object, java.lang.Object[])
             */
            @Override
            protected void deliverUpdate(CommCareHomeActivity receiver, Long... update) {
                //we don't need to deliver updates here, it happens on the notification bar
            }

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverError(java.lang.Object, java.lang.Exception)
             */
            @Override
            protected void deliverError(CommCareHomeActivity receiver, Exception e) {
                //TODO: Display somewhere useful
                receiver.displayMessage(Localization.get("sync.fail.unsent"), true);
            }

        };

        try {
            mProcess.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }
        mProcess.connect(this);

        //Execute on a true multithreaded chain. We should probably replace all of our calls with this
        //but this is the big one for now.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mProcess.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, records);
        } else {
            mProcess.execute(records);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (platform == null && CommCareApplication._().getCurrentApp() != null) {
            platform = CommCareApplication._().getCommCarePlatform();
        }
        dispatchHomeScreen();
    }

    private void dispatchHomeScreen() {
        try {
            //First make sure nothing catastrophic has happened
            if (CommCareApplication._().getAppResourceState() == CommCareApplication.STATE_CORRUPTED ||
                    CommCareApplication._().getDatabaseState() == CommCareApplication.STATE_CORRUPTED) {
                if (!CommCareApplication._().isStorageAvailable()) {
                    createNoStorageDialog();
                } else {
                    //see if we're logged in. If so, prompt for recovery.
                    try {
                        CommCareApplication._().getSession();
                        showDialog(DIALOG_CORRUPTED);
                    } catch (SessionUnavailableException sue) {
                        //otherwise, log in first
                        returnToLogin();
                    }
                }
            }

            //Now we need to catch any resource or database upgrade flags and make sure that the application
            //is ready to go.
            else if (CommCareApplication._().getAppResourceState() != CommCareApplication.STATE_READY ||
                    CommCareApplication._().getDatabaseState() != CommCareApplication.STATE_READY) {
                Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);

                this.startActivityForResult(i, INIT_APP);
            } else if (!CommCareApplication._().getCurrentApp().areResourcesValidated()
                    // if superuser is enabled, we won't need to validate multimedia, just launch the home screen directly
                    && !DeveloperPreferences.isSuperuserEnabled()) {

                Intent i = new Intent(this, CommCareVerificationActivity.class);
                this.startActivityForResult(i, MISSING_MEDIA_ACTIVITY);
                
            } else if(!CommCareApplication._().getSession().isActive()) {
                //We got brought back to this point despite 
                returnToLogin();
            } else if (this.getIntent().hasExtra(SESSION_REQUEST)) {
                wasExternal = true;
                String sessionRequest = this.getIntent().getStringExtra(SESSION_REQUEST);
                SessionStateDescriptor ssd = new SessionStateDescriptor();
                ssd.fromBundle(sessionRequest);
                CommCareApplication._().getCurrentSessionWrapper().loadFromStateDescription(ssd);
                this.startNextFetch();
            } else if (this.getIntent().hasExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT)) {
                //We were launched in shortcut mode. Get the command and load us up.
                CommCareApplication._().getCurrentSession().setCommand(this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));
                startNextFetch();
                //Only launch shortcuts once per intent
                this.getIntent().removeExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT);
            } else if (CommCareApplication._().isUpdatePending()) {
                //We've got an update pending that we need to check on.
                Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Auto-Update Triggered");

                //Create the update intent
                Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
                SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
                String ref = prefs.getString("default_app_server", null);

                i.putExtra(CommCareSetupActivity.KEY_PROFILE_REF, ref);
                i.putExtra(CommCareSetupActivity.KEY_UPGRADE_MODE, true);
                i.putExtra(CommCareSetupActivity.KEY_AUTO, true);

                startActivityForResult(i, UPGRADE_APP);
            } else if(CommCareApplication._().isSyncPending(true)) {
                long lastSync = CommCareApplication._().getCurrentApp().getAppPreferences().getLong("last-ota-restore", 0);
                String footer = lastSync == 0 ? "never" : SimpleDateFormat.getDateTimeInstance().format(lastSync);
                Logger.log(AndroidLogger.TYPE_USER, "autosync triggered. Last Sync|" + footer);
                refreshView();

                //Send unsent forms first. If the process detects unsent forms
                //it will sync after the are submitted
                if(!this.checkAndStartUnsentTask(true)) {
                    //If there were no unsent forms to be sent, we should immediately
                    //trigger a sync
                    this.syncData(false);
                }
            } else {
                //Normal Home Screen login time!
                refreshView();
            }
        } catch (SessionUnavailableException sue) {
            //TODO: See how much context we have, and go login
            returnToLogin();
        }
    }

    private void returnToLogin() {
        returnToLogin(Localization.get("app.workflow.login.lost"));
    }

    private void returnToLogin(String message) {
        //Not yet.
        if (message != null) {
            //Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        Intent i = new Intent(getApplicationContext(), LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivityForResult(i, LOGIN_USER);
    }

    public void createNoStorageDialog() {
        CommCareApplication._().triggerHandledAppExit(this, Localization.get("app.storage.missing.message"), Localization.get("app.storage.missing.title"));
    }

    private void createAskUseOldDialog(final AndroidSessionWrapper state, final SessionStateDescriptor existing) {
        mAskOldDialog = new AlertDialog.Builder(this).create();
        mAskOldDialog.setTitle(Localization.get("app.workflow.incomplete.continue.title"));
        mAskOldDialog.setMessage(Localization.get("app.workflow.incomplete.continue"));
        DialogInterface.OnClickListener useOldListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                try {
                    switch (i) {
                        case DialogInterface.BUTTON1: // yes, use old
                            //Replace the current state from the descriptor
                            state.loadFromStateDescription(existing);
                            formEntry(platform.getFormContentUri(state.getSession().getForm()), state.getFormRecord());
                            break;
                        case DialogInterface.BUTTON2: // no, and delete the old one
                            FormRecordCleanupTask.wipeRecord(CommCareHomeActivity.this, existing);
                            //fallthrough to new now that old record is gone
                        case DialogInterface.BUTTON3: // no, create new
                            state.commitStub();
                            formEntry(platform.getFormContentUri(state.getSession().getForm()), state.getFormRecord());
                            break;
                    }
                } catch (SessionUnavailableException sue) {
                    //TODO: From home activity, login again and return to form list if possible.
                } catch (StorageFullException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        mAskOldDialog.setCancelable(false);
        mAskOldDialog.setButton(Localization.get("option.yes"), useOldListener);
        mAskOldDialog.setButton2(Localization.get("app.workflow.incomplete.continue.option.delete"), useOldListener);
        mAskOldDialog.setButton3(Localization.get("option.no"), useOldListener);

        mAskOldDialog.show();
    }

    private void displayMessage(String message) {
        displayMessage(message, false);
    }

    private void displayMessage(String message, boolean bad) {
        displayMessage(message, bad, false);
    }

    private void displayMessage(String message, boolean bad, boolean suppressToast) {
        if (!suppressToast) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        adapter.setNotificationTextForButton(R.layout.home_sync_button, false, message);

    }

    private void refreshView() {
        TextView version = (TextView)findViewById(R.id.str_version);
        if (version == null) return;
        version.setText(CommCareApplication._().getCurrentVersionString());
        boolean syncOK = true;

        Pair<Long, int[]> syncDetails;
        try {
            syncDetails = CommCareApplication._().getSyncDisplayParameters();
        } catch (UserStorageClosedException e) {
            returnToLogin(Localization.get("home.logged.out"));
            return;
        }

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        unsentFormNumberLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_NUMBER_KEY, "5"));
        unsentFormTimeLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_TIME_KEY, "5"));

        String syncKey = "home.sync";
        String lastMessageKey = "home.sync.message.last";
        String homeMessageKey = "home.start";
        String logoutMessageKey = "home.logout";

        if (isDemoUser()) {
            syncKey = "home.sync.demo";
            lastMessageKey = "home.sync.message.last";
            homeMessageKey = "home.start.demo";
            logoutMessageKey = "home.logout.demo";
        }

        // Override default CommCare banner if requested
        String customBannerURI = prefs.getString(CommCarePreferences.BRAND_BANNER_HOME, "");
        if (!"".equals(customBannerURI)) {
            Bitmap bitmap = ViewUtil.inflateDisplayImage(this, customBannerURI);
            if (bitmap != null) {
                if (topBannerImageView != null) topBannerImageView.setImageBitmap(bitmap);
                else Log.i("TopBanner", "TopBanner is null!");

            }
        }

        if (startButton != null) {
            startButton.setText(Localization.get(homeMessageKey));
        }
        if (logoutButton != null) {
            logoutButton.setText(Localization.get(logoutMessageKey));
        }
        if (syncButton != null) {
            setSyncButtonText(syncDetails, syncKey);
        }

        CharSequence syncTime;
        if (syncDetails.first == 0) {
            syncTime = Localization.get("home.sync.message.last.never");
        } else {
            syncTime = DateUtils.formatSameDayTime(syncDetails.first, new Date().getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT);
        }

        //TODO: Localize this all
        String message = "";
        if (syncDetails.second[0] == 1) {
            message += Localization.get("home.sync.message.unsent.singular") + "\n";
        } else if (syncDetails.second[0] > 1) {
            message += Localization.get("home.sync.message.unsent.plural", new String[]{String.valueOf(syncDetails.second[0])}) + "\n";
        }

        setIncompleteFormsText(syncDetails);

        if (syncDetails.second[0] > unsentFormNumberLimit) {
            syncOK = false;
        }

        long then = syncDetails.first;
        long now = new Date().getTime();

        int secs_ago = (int)((then - now) / 1000);
        int days_ago = secs_ago / 86400;

        if ((-days_ago) > unsentFormTimeLimit && (prefs.getString("server-tether", "push-only").equals("sync"))) {
            syncOK = false;
        }

        message += Localization.get(lastMessageKey, new String[]{syncTime.toString()});

        displayMessage(message, !syncOK, true);


        //Make sure that the review button is properly enabled.
        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        if (p != null && p.isFeatureActive(Profile.FEATURE_REVIEW)) {
            adapter.setButtonVisibility(R.layout.home_savedforms_button, false, false);

        }

        // set adapter to hide the buttons...
        boolean showSavedForms = CommCarePreferences.isSavedFormsEnabled();
        boolean showIncompleteForms = CommCarePreferences.isIncompleteFormsEnabled();

        Log.i("ShowForms", "ShowSavedForms: " + showSavedForms + " | ShowIncompleteForms: " + showIncompleteForms);

        adapter.setButtonVisibility(R.layout.home_savedforms_button, false, !showSavedForms);
        adapter.setButtonVisibility(R.layout.home_incompleteforms_button, false, !showIncompleteForms);

        adapter.notifyDataSetChanged();

    }

    private void setSyncButtonText(Pair<Long, int[]> syncDetails, String syncTextKey) {
        if (syncTextKey == null) {
            syncTextKey = isDemoUser() ? "home.sync.demo" : "home.sync";
        }
        if (syncDetails.second[0] > 0) {
            Spannable syncIndicator = (this.localize("home.sync.indicator", new String[]{String.valueOf(syncDetails.second[0]), Localization.get(syncTextKey)}));
            syncButton.setNotificationText(syncIndicator);
            adapter.notifyDataSetChanged();
        } else {
            syncButton.setText(this.localize(syncTextKey));
        }
    }

    private void setIncompleteFormsText(Pair<Long, int[]> syncDetails) {
        if (syncDetails.second[1] > 0) {
            Log.i("syncDetails", "SyncDetails has count " + syncDetails.second[1]);
            Spannable incompleteIndicator = (this.localize("home.forms.incomplete.indicator", new String[]{String.valueOf(syncDetails.second[1]), Localization.get("home.forms.incomplete")}));
            if (viewIncomplete != null) viewIncomplete.setText(incompleteIndicator);

        } else {
            Log.i("syncDetails", "SyncDetails has no count");
            if (viewIncomplete != null)
                viewIncomplete.setText(this.localize("home.forms.incomplete"));
        }
    }

    //Process and send listeners

    private boolean isDemoUser() {
        try {
            User u = CommCareApplication._().getSession().getLoggedInUser();
            if (User.TYPE_DEMO.equals(u.getUserType())) {
                return true;
            }
        } catch (SessionUnavailableException e) {

        }
        return false;
    }

    //END - Process and Send Listeners


    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PREFERENCES, 0, Localization.get("home.menu.settings")).setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_UPDATE, 0, Localization.get("home.menu.update")).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_CALL_LOG, 0, Localization.get("home.menu.call.log")).setIcon(
                android.R.drawable.ic_menu_recent_history);
        menu.add(0, MENU_REPORT_PROBLEM, 0, Localization.get("problem.report.menuitem")).setIcon(
                android.R.drawable.ic_menu_report_image);
        menu.add(0, MENU_VALIDATE_MEDIA, 0, Localization.get("home.menu.validate")).setIcon(
                android.R.drawable.ic_menu_gallery);
        menu.add(0, MENU_DUMP_FORMS, 0, Localization.get("home.menu.formdump")).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_WIFI_DIRECT, 0, Localization.get("home.menu.wifi.direct")).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_CONNECTION_DIAGNOSTIC, 0, Localization.get("home.menu.connection.diagnostic")).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_SAVED_FORMS, 0, Localization.get("home.menu.saved.forms")).setIcon(
                R.drawable.notebook_full);
        return true;
    }


    /* (non-Javadoc)
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //In Holo theme this gets called on startup
        try {
            User u = CommCareApplication._().getSession().getLoggedInUser();
            boolean enableMenus = !User.TYPE_DEMO.equals(u.getUserType());
            menu.findItem(MENU_PREFERENCES).setVisible(enableMenus);
            menu.findItem(MENU_UPDATE).setVisible(enableMenus);
            menu.findItem(MENU_VALIDATE_MEDIA).setVisible(enableMenus);
            menu.findItem(MENU_DUMP_FORMS).setVisible(enableMenus);
            menu.findItem(MENU_WIFI_DIRECT).setVisible(enableMenus && hasP2p());
            menu.findItem(MENU_CONNECTION_DIAGNOSTIC).setVisible(enableMenus);
            menu.findItem(MENU_SAVED_FORMS).setVisible(enableMenus);
        } catch (SessionUnavailableException sue) {
            //Nothing
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                //CommCareUtil.printInstance("jr://instance/stockdb");
                createPreferencesMenu(this);
                return true;
            case MENU_UPDATE:
                if (!isOnline() && isAirplaneModeOn()) {
                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_AirplaneMode));
                    return true;
                }
                Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
                SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
                String ref = prefs.getString("default_app_server", null);
                i.putExtra(CommCareSetupActivity.KEY_PROFILE_REF, ref);
                i.putExtra(CommCareSetupActivity.KEY_UPGRADE_MODE, true);

                startActivityForResult(i, UPGRADE_APP);
                return true;
            case MENU_CALL_LOG:
                createCallLogActivity();
                return true;
            case MENU_REPORT_PROBLEM:
                startReportActivity();
                return true;
            case MENU_VALIDATE_MEDIA:
                startValidationActivity();
                return true;
            case MENU_DUMP_FORMS:
                startFormDumpActivity();
                return true;
            case MENU_WIFI_DIRECT:
                startWifiDirectActivity();
                return true;
            case MENU_CONNECTION_DIAGNOSTIC:
                startMenuConnectionActivity();
                return true;
            case MENU_SAVED_FORMS:
                goToFormArchive(false);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void createPreferencesMenu() {
        createPreferencesMenu(this);
    }

    public static void createPreferencesMenu(Activity activity) {
        Intent i = new Intent(activity, CommCarePreferences.class);
        activity.startActivityForResult(i, PREFERENCES_ACTIVITY);
    }

    private void createCallLogActivity() {
        Intent i = new Intent(this, PhoneLogActivity.class);
        startActivity(i);

    }

    private void startReportActivity() {
        Intent i = new Intent(this, ReportProblemActivity.class);
        CommCareHomeActivity.this.startActivityForResult(i, REPORT_PROBLEM_ACTIVITY);
    }

    private void startValidationActivity() {
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        CommCareHomeActivity.this.startActivityForResult(i, MEDIA_VALIDATOR_ACTIVITY);
    }

    private void startFormDumpActivity() {
        Intent i = new Intent(this, CommCareFormDumpActivity.class);
        i.putExtra(CommCareFormDumpActivity.EXTRA_FILE_DESTINATION, CommCareApplication._().getCurrentApp().storageRoot());
        CommCareHomeActivity.this.startActivityForResult(i, DUMP_FORMS_ACTIVITY);
    }

    private void startWifiDirectActivity() {
        Intent i = new Intent(this, CommCareWiFiDirectActivity.class);
        CommCareHomeActivity.this.startActivityForResult(i, WIFI_DIRECT_ACTIVITY);
    }

    private void startMenuConnectionActivity() {
        Intent i = new Intent(this, ConnectionDiagnosticActivity.class);
        CommCareHomeActivity.this.startActivityForResult(i, CONNECTION_DIAGNOSTIC_ACTIVITY);
    }

//    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//      super.onConfigurationChanged(newConfig);
//      setContentView(R.layout.mainnew);
//      try {
//          configUi();
//          refreshView();
//      } catch(SessionUnavailableException sue) {
//          //we'll handle this in resume?
//      }
//    }

    private boolean isAirplaneModeOn() {

        return Settings.System.getInt(getApplicationContext().getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private boolean hasP2p() {
        return (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH && getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT));
    }

    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_CORRUPTED) {
            return createAskFixDialog();
        } else return null;
    }

    public Dialog createAskFixDialog() {
        //TODO: Localize this in theory, but really shift it to the upgrade/management state
        mAttemptFixDialog = new AlertDialog.Builder(this).create();

        mAttemptFixDialog.setTitle("Storage is Corrupt :/");
        mAttemptFixDialog.setMessage("Sorry, something really bad has happened, and the app can't start up. With your permission CommCare can try to repair itself if you have network access.");
        DialogInterface.OnClickListener attemptFixDialog = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // attempt repair
                        Intent intent = new Intent(CommCareHomeActivity.this, RecoveryActivity.class);
                        startActivity(intent);
                        break;

                    case DialogInterface.BUTTON2: // Shut down
                        CommCareHomeActivity.this.finish();
                        break;
                }
            }
        };
        mAttemptFixDialog.setCancelable(false);
        mAttemptFixDialog.setButton("Enter Recovery Mode", attemptFixDialog);
        mAttemptFixDialog.setButton2("Shut Down", attemptFixDialog);

        return mAttemptFixDialog;
    }


    /**
     * All methods for implementation of DialogController that are not already handled in CommCareActivity *
     */
    

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#generateProgressDialog(int)
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case ProcessAndSendTask.SEND_PHASE_ID:
                title = Localization.get("sync.progress.submitting.title");
                message = Localization.get("sync.progress.submitting");
                break;
            case ProcessAndSendTask.PROCESSING_PHASE_ID:
                title = Localization.get("form.entry.processing.title");
                message = Localization.get("form.entry.processing");
                break;
            case DataPullTask.DATA_PULL_TASK_ID:
                title = Localization.get("sync.progress.title");
                message = Localization.get("sync.progress.purge");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in CommCareHomeActivity");
                return null;
        }
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        if (taskId == ProcessAndSendTask.PROCESSING_PHASE_ID) {
            dialog.addProgressBar();
        }
        return dialog;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#isBackEnabled()
     */
    @Override
    public boolean isBackEnabled() {
        return false;
    }
}
