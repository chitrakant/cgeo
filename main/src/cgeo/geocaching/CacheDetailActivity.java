package cgeo.geocaching;

import butterknife.InjectView;
import butterknife.Views;

import cgeo.calendar.ICalendar;
import cgeo.geocaching.activity.AbstractViewPagerActivity;
import cgeo.geocaching.activity.Progress;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.IConnector;
import cgeo.geocaching.connector.gc.GCConnector;
import cgeo.geocaching.enumerations.CacheAttribute;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LoadFlags.SaveFlag;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.geopoint.GeopointFormatter;
import cgeo.geocaching.geopoint.Units;
import cgeo.geocaching.network.HtmlImage;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.ui.AbstractCachingPageViewCreator;
import cgeo.geocaching.ui.AnchorAwareLinkMovementMethod;
import cgeo.geocaching.ui.CacheDetailsCreator;
import cgeo.geocaching.ui.CoordinatesFormatSwitcher;
import cgeo.geocaching.ui.DecryptTextClickListener;
import cgeo.geocaching.ui.EditNoteDialog;
import cgeo.geocaching.ui.EditNoteDialog.EditNoteDialogListener;
import cgeo.geocaching.ui.Formatter;
import cgeo.geocaching.ui.HtmlImageCounter;
import cgeo.geocaching.ui.ImagesList;
import cgeo.geocaching.ui.LoggingUI;
import cgeo.geocaching.ui.OwnerActionsClickListener;
import cgeo.geocaching.ui.WeakReferenceHandler;
import cgeo.geocaching.ui.logs.CacheLogsViewCreator;
import cgeo.geocaching.utils.CancellableHandler;
import cgeo.geocaching.utils.ClipboardUtils;
import cgeo.geocaching.utils.CryptUtils;
import cgeo.geocaching.utils.GeoDirHandler;
import cgeo.geocaching.utils.HtmlUtils;
import cgeo.geocaching.utils.ImageUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.MatcherWrapper;
import cgeo.geocaching.utils.RunnableWithArgument;
import cgeo.geocaching.utils.TextUtils;
import cgeo.geocaching.utils.TranslationUtils;
import cgeo.geocaching.utils.UnknownTagsHandler;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import android.R.color;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Activity to handle all single-cache-stuff.
 *
 * e.g. details, description, logs, waypoints, inventory...
 */
public class CacheDetailActivity extends AbstractViewPagerActivity<CacheDetailActivity.Page>
        implements EditNoteDialogListener {

    private static final int MENU_FIELD_COPY = 1;
    private static final int MENU_FIELD_TRANSLATE = 2;
    private static final int MENU_FIELD_TRANSLATE_EN = 3;
    private static final int MENU_FIELD_SHARE = 4;
    private static final int MENU_SHARE = 12;
    private static final int MENU_CALENDAR = 11;
    private static final int MENU_CACHES_AROUND = 10;
    private static final int MENU_BROWSER = 7;
    private static final int MENU_DEFAULT_NAVIGATION = 13;

    private static final int CONTEXT_MENU_WAYPOINT_EDIT = 1234;
    private static final int CONTEXT_MENU_WAYPOINT_DUPLICATE = 1235;
    private static final int CONTEXT_MENU_WAYPOINT_DELETE = 1236;
    private static final int CONTEXT_MENU_WAYPOINT_NAVIGATE = 1238;
    private static final int CONTEXT_MENU_WAYPOINT_CACHES_AROUND = 1239;
    private static final int CONTEXT_MENU_WAYPOINT_DEFAULT_NAVIGATION = 1240;
    private static final int CONTEXT_MENU_WAYPOINT_RESET_ORIGINAL_CACHE_COORDINATES = 1241;

    private static final Pattern[] DARK_COLOR_PATTERNS = {
            Pattern.compile("((?<!bg)color)=\"#" + "(0[0-9]){3}" + "\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("((?<!bg)color)=\"" + "black" + "\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("((?<!bg)color)=\"#" + "000080" + "\"", Pattern.CASE_INSENSITIVE) };
    private static final Pattern[] LIGHT_COLOR_PATTERNS = {
            Pattern.compile("((?<!bg)color)=\"#" + "([F][6-9A-F]){3}" + "\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("((?<!bg)color)=\"" + "white" + "\"", Pattern.CASE_INSENSITIVE) };
    public static final String STATE_PAGE_INDEX = "cgeo.geocaching.pageIndex";

    private Geocache cache;
    private final Progress progress = new Progress();
    private SearchResult search;

    private EditNoteDialogListener editNoteDialogListener;

    private final GeoDirHandler locationUpdater = new GeoDirHandler() {
        @Override
        public void updateGeoData(final IGeoData geo) {
            if (cacheDistanceView == null) {
                return;
            }

            try {
                final StringBuilder dist = new StringBuilder();

                if (geo.getCoords() != null && cache != null && cache.getCoords() != null) {
                    dist.append(Units.getDistanceFromKilometers(geo.getCoords().distanceTo(cache.getCoords())));
                }

                if (cache != null && cache.getElevation() != null) {
                    if (geo.getAltitude() != 0.0) {
                        final float diff = (float) (cache.getElevation() - geo.getAltitude());
                        dist.append(' ').append(Units.getElevation(diff));
                    }
                }

                cacheDistanceView.setText(dist.toString());
                cacheDistanceView.bringToFront();
            } catch (final Exception e) {
                Log.w("Failed to update location.");
            }
        }
    };

    private CharSequence clickedItemText = null;
    private int contextMenuWPIndex = -1;

    /**
     * If another activity is called and can modify the data of this activity, we refresh it on resume.
     */
    private boolean refreshOnResume = false;

    // some views that must be available from everywhere // TODO: Reference can block GC?
    private TextView cacheDistanceView;

    private final Handler cacheChangeNotificationHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            notifyDataSetChanged();
        }
    };
    protected ImagesList imagesList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.cachedetail_activity);

        // set title in code, as the activity needs a hard coded title due to the intent filters
        setTitle(res.getString(R.string.cache));

        String geocode = null;

        // TODO Why can it happen that search is not null? onCreate should be called only once and it is not set before.
        if (search != null) {
            cache = search.getFirstCacheFromResult(LoadFlags.LOAD_ALL_DB_ONLY);
            if (cache != null && cache.getGeocode() != null) {
                geocode = cache.getGeocode();
            }
        }

        // get parameters
        final Bundle extras = getIntent().getExtras();
        final Uri uri = getIntent().getData();

        // try to get data from extras
        String name = null;
        String guid = null;
        if (geocode == null && extras != null) {
            geocode = extras.getString(Intents.EXTRA_GEOCODE);
            name = extras.getString(Intents.EXTRA_NAME);
            guid = extras.getString(Intents.EXTRA_GUID);
        }

        // try to get data from URI
        if (geocode == null && guid == null && uri != null) {
            final String uriHost = uri.getHost().toLowerCase(Locale.US);
            final String uriPath = uri.getPath().toLowerCase(Locale.US);
            final String uriQuery = uri.getQuery();

            if (uriQuery != null) {
                Log.i("Opening URI: " + uriHost + uriPath + "?" + uriQuery);
            } else {
                Log.i("Opening URI: " + uriHost + uriPath);
            }

            if (uriHost.contains("geocaching.com")) {
                geocode = uri.getQueryParameter("wp");
                guid = uri.getQueryParameter("guid");

                if (StringUtils.isNotBlank(geocode)) {
                    geocode = geocode.toUpperCase(Locale.US);
                    guid = null;
                } else if (StringUtils.isNotBlank(guid)) {
                    geocode = null;
                    guid = guid.toLowerCase(Locale.US);
                } else {
                    showToast(res.getString(R.string.err_detail_open));
                    finish();
                    return;
                }
            } else if (uriHost.contains("coord.info")) {
                if (uriPath != null && uriPath.startsWith("/gc")) {
                    geocode = uriPath.substring(1).toUpperCase(Locale.US);
                } else {
                    showToast(res.getString(R.string.err_detail_open));
                    finish();
                    return;
                }
            } else if (uriHost.contains("opencaching.de")) {
                if (uriPath != null && uriPath.startsWith("/oc")) {
                    geocode = uriPath.substring(1).toUpperCase(Locale.US);
                } else {
                    geocode = uri.getQueryParameter("wp");
                    if (StringUtils.isNotBlank(geocode)) {
                        geocode = geocode.toUpperCase(Locale.US);
                    } else {
                        showToast(res.getString(R.string.err_detail_open));
                        finish();
                        return;
                    }
                }
            } else {
                showToast(res.getString(R.string.err_detail_open));
                finish();
                return;
            }
        }

        // no given data
        if (geocode == null && guid == null) {
            showToast(res.getString(R.string.err_detail_cache));
            finish();
            return;
        }

        final LoadCacheHandler loadCacheHandler = new LoadCacheHandler();

        try {
            String title = res.getString(R.string.cache);
            if (StringUtils.isNotBlank(name)) {
                title = name;
            } else if (null != geocode && StringUtils.isNotBlank(geocode)) { // can't be null, but the compiler doesn't understand StringUtils.isNotBlank()
                title = geocode;
            }
            progress.show(this, title, res.getString(R.string.cache_dialog_loading_details), true, loadCacheHandler.cancelMessage());
        } catch (final Exception e) {
            // nothing, we lost the window
        }

        final ImageView defaultNavigationImageView = (ImageView) findViewById(R.id.defaultNavigation);
        defaultNavigationImageView.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startDefaultNavigation2();
                return true;
            }
        });

        final int pageToOpen = savedInstanceState != null ?
                savedInstanceState.getInt(STATE_PAGE_INDEX, 0) :
                Settings.isOpenLastDetailsPage() ? Settings.getLastDetailsPage() : 1;
        createViewPager(pageToOpen, new OnPageSelectedListener() {

            @Override
            public void onPageSelected(int position) {
                if (Settings.isOpenLastDetailsPage()) {
                    Settings.setLastDetailsPage(position);
                }
                // lazy loading of cache images
                if (getPage(position) == Page.IMAGES) {
                    loadCacheImages();
                }
            }
        });

        // Initialization done. Let's load the data with the given information.
        new LoadCacheThread(geocode, guid, loadCacheHandler).start();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE_INDEX, getCurrentItem());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (refreshOnResume) {
            notifyDataSetChanged();
            refreshOnResume = false;
        }
        locationUpdater.startGeo();
    }

    @Override
    public void onStop() {
        if (cache != null) {
            cache.setChangeNotificationHandler(null);
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        locationUpdater.stopGeo();
        super.onPause();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, view, info);
        final int viewId = view.getId();
        contextMenuWPIndex = -1;
        switch (viewId) {
            case R.id.value: // coordinates, gc-code, name
                clickedItemText = ((TextView) view).getText();
                final String itemTitle = (String) ((TextView) ((View) view.getParent()).findViewById(R.id.name)).getText();
                buildOptionsContextmenu(menu, viewId, itemTitle, true);
                break;
            case R.id.shortdesc:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_description), false);
                break;
            case R.id.longdesc:
                // combine short and long description
                final String shortDesc = cache.getShortDescription();
                if (StringUtils.isBlank(shortDesc)) {
                    clickedItemText = ((TextView) view).getText();
                } else {
                    clickedItemText = shortDesc + "\n\n" + ((TextView) view).getText();
                }
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_description), false);
                break;
            case R.id.personalnote:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_personal_note), true);
                break;
            case R.id.hint:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_hint), false);
                break;
            case R.id.log:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_logs), false);
                break;
            case -1:
                if (null != cache.getWaypoints()) {
                    try {
                        final ViewGroup parent = ((ViewGroup) view.getParent());
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            if (parent.getChildAt(i) == view) {
                                final List<Waypoint> sortedWaypoints = new ArrayList<Waypoint>(cache.getWaypoints());
                                Collections.sort(sortedWaypoints);
                                final Waypoint waypoint = sortedWaypoints.get(i);
                                final int index = cache.getWaypoints().indexOf(waypoint);
                                menu.setHeaderTitle(res.getString(R.string.waypoint));
                                if (waypoint.getWaypointType().equals(WaypointType.ORIGINAL)) {
                                    menu.add(CONTEXT_MENU_WAYPOINT_RESET_ORIGINAL_CACHE_COORDINATES, index, 0, R.string.waypoint_reset_cache_coords);
                                } else {
                                    menu.add(CONTEXT_MENU_WAYPOINT_EDIT, index, 0, R.string.waypoint_edit);
                                    menu.add(CONTEXT_MENU_WAYPOINT_DUPLICATE, index, 0, R.string.waypoint_duplicate);
                                }
                                contextMenuWPIndex = index;
                                if (waypoint.isUserDefined() && !waypoint.getWaypointType().equals(WaypointType.ORIGINAL)) {
                                    menu.add(CONTEXT_MENU_WAYPOINT_DELETE, index, 0, R.string.waypoint_delete);
                                }
                                if (waypoint.getCoords() != null) {
                                    menu.add(CONTEXT_MENU_WAYPOINT_DEFAULT_NAVIGATION, index, 0, NavigationAppFactory.getDefaultNavigationApplication().getName());
                                    menu.add(CONTEXT_MENU_WAYPOINT_NAVIGATE, index, 0, R.string.cache_menu_navigate).setIcon(R.drawable.ic_menu_mapmode);
                                    menu.add(CONTEXT_MENU_WAYPOINT_CACHES_AROUND, index, 0, R.string.cache_menu_around);
                                }
                                break;
                            }
                        }
                    } catch (final Exception e) {
                    }
                }
                break;
            default:
                if (imagesList != null) {
                    imagesList.onCreateContextMenu(menu, view);
                }
                break;
        }
    }

    private void buildOptionsContextmenu(ContextMenu menu, int viewId, String fieldTitle, boolean copyOnly) {
        menu.setHeaderTitle(fieldTitle);
        menu.add(viewId, MENU_FIELD_COPY, 0, res.getString(android.R.string.copy));
        if (!copyOnly) {
            if (clickedItemText.length() > TranslationUtils.translationTextLengthToWarn) {
                showToast(res.getString(R.string.translate_length_warning));
            }
            menu.add(viewId, MENU_FIELD_TRANSLATE, 0, res.getString(R.string.translate_to_sys_lang, Locale.getDefault().getDisplayLanguage()));
            if (Settings.isUseEnglish() && !StringUtils.equals(Locale.getDefault().getLanguage(), Locale.ENGLISH.getLanguage())) {
                menu.add(viewId, MENU_FIELD_TRANSLATE_EN, 0, res.getString(R.string.translate_to_english));
            }

        }
        menu.add(viewId, MENU_FIELD_SHARE, 0, res.getString(R.string.cache_share_field));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final int groupId = item.getGroupId();
        final int index = item.getItemId();
        switch (groupId) {
            case R.id.value:
            case R.id.shortdesc:
            case R.id.longdesc:
            case R.id.personalnote:
            case R.id.hint:
            case R.id.log:
                switch (index) {
                    case MENU_FIELD_COPY:
                        ClipboardUtils.copyToClipboard(clickedItemText);
                        showToast(res.getString(R.string.clipboard_copy_ok));
                        return true;
                    case MENU_FIELD_TRANSLATE:
                        TranslationUtils.startActivityTranslate(this, Locale.getDefault().getLanguage(), HtmlUtils.extractText(clickedItemText));
                        return true;
                    case MENU_FIELD_TRANSLATE_EN:
                        TranslationUtils.startActivityTranslate(this, Locale.ENGLISH.getLanguage(), HtmlUtils.extractText(clickedItemText));
                        return true;
                    case MENU_FIELD_SHARE:
                        final Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, clickedItemText.toString());
                        startActivity(Intent.createChooser(intent, res.getText(R.string.cache_share_field)));
                        return true;
                    default:
                        break;
                }

                break;
            case CONTEXT_MENU_WAYPOINT_EDIT:
                final Waypoint waypointEdit = cache.getWaypoint(index);
                if (waypointEdit != null) {
                    EditWaypointActivity.startActivityEditWaypoint(this, waypointEdit.getId());
                    refreshOnResume = true;
                }
                break;
            case CONTEXT_MENU_WAYPOINT_DUPLICATE:
                final Waypoint waypointDuplicate = cache.getWaypoint(index);
                if (cache.duplicateWaypoint(waypointDuplicate)) {
                    cgData.saveCache(cache, EnumSet.of(SaveFlag.SAVE_DB));
                    notifyDataSetChanged();
                }
                break;
            case CONTEXT_MENU_WAYPOINT_DELETE:
                final Waypoint waypointDelete = cache.getWaypoint(index);
                if (cache.deleteWaypoint(waypointDelete)) {
                    cgData.saveCache(cache, EnumSet.of(SaveFlag.SAVE_DB));
                    notifyDataSetChanged();
                }
                break;
            case CONTEXT_MENU_WAYPOINT_DEFAULT_NAVIGATION:
                final Waypoint waypointNavigation = cache.getWaypoint(index);
                if (waypointNavigation != null) {
                    NavigationAppFactory.startDefaultNavigationApplication(1, this, waypointNavigation);
                }
                break;
            case CONTEXT_MENU_WAYPOINT_NAVIGATE:
                final Waypoint waypointNav = cache.getWaypoint(contextMenuWPIndex);
                if (waypointNav != null) {
                    NavigationAppFactory.showNavigationMenu(this, null, waypointNav, null);
                }
                break;
            case CONTEXT_MENU_WAYPOINT_CACHES_AROUND:
                final Waypoint waypointAround = cache.getWaypoint(index);
                if (waypointAround != null) {
                    cgeocaches.startActivityCoordinates(this, waypointAround.getCoords());
                }
                break;

            case CONTEXT_MENU_WAYPOINT_RESET_ORIGINAL_CACHE_COORDINATES:
                final Waypoint waypointReset = cache.getWaypoint(index);
                if (ConnectorFactory.getConnector(cache).supportsOwnCoordinates()) {
                    createResetCacheCoordinatesDialog(cache, waypointReset).show();
                }
                else {
                    final ProgressDialog progressDialog = ProgressDialog.show(this, getString(R.string.cache), getString(R.string.waypoint_reset), true);
                    final HandlerResetCoordinates handler = new HandlerResetCoordinates(this, progressDialog, false);
                    new ResetCoordsThread(cache, handler, waypointReset, true, false, progressDialog).start();
                }
                break;

            default:
                if (imagesList != null && imagesList.onContextItemSelected(item)) {
                    return true;
                }
                return onOptionsItemSelected(item);
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (null != cache) {
            menu.add(0, MENU_DEFAULT_NAVIGATION, 0, NavigationAppFactory.getDefaultNavigationApplication().getName()).setIcon(R.drawable.ic_menu_compass); // default navigation tool

            final SubMenu subMenu = menu.addSubMenu(0, 0, 0, res.getString(R.string.cache_menu_navigate)).setIcon(R.drawable.ic_menu_mapmode);
            NavigationAppFactory.addMenuItems(subMenu, cache);

            menu.add(0, MENU_CALENDAR, 0, res.getString(R.string.cache_menu_event)).setIcon(R.drawable.ic_menu_agenda); // add event to calendar
            LoggingUI.addMenuItems(this, menu, cache);
            menu.add(0, MENU_CACHES_AROUND, 0, res.getString(R.string.cache_menu_around)).setIcon(R.drawable.ic_menu_rotate); // caches around
            menu.add(0, MENU_BROWSER, 0, res.getString(R.string.cache_menu_browser)).setIcon(R.drawable.ic_menu_globe); // browser
            menu.add(0, MENU_SHARE, 0, res.getString(R.string.cache_menu_share)).setIcon(R.drawable.ic_menu_share); // share cache
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (cache != null) {
            menu.findItem(MENU_DEFAULT_NAVIGATION).setVisible(null != cache.getCoords());
            menu.findItem(MENU_CALENDAR).setVisible(cache.canBeAddedToCalendar());
            menu.findItem(MENU_CACHES_AROUND).setVisible(null != cache.getCoords() && cache.supportsCachesAround());
            menu.findItem(MENU_BROWSER).setVisible(cache.canOpenInBrowser());
            LoggingUI.onPrepareOptionsMenu(menu, cache);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int menuItem = item.getItemId();

        switch (menuItem) {
            case 0:
                // no menu selected, but a new sub menu shown
                return false;
            case MENU_DEFAULT_NAVIGATION:
                startDefaultNavigation();
                return true;
            case MENU_BROWSER:
                cache.openInBrowser(this);
                return true;
            case MENU_CACHES_AROUND:
                cgeocaches.startActivityCoordinates(this, cache.getCoords());
                return true;
            case MENU_CALENDAR:
                addToCalendarWithIntent();
                return true;
            case MENU_SHARE:
                if (cache != null) {
                    cache.shareCache(this, res);
                    return true;
                }
                return false;
            default:
                if (NavigationAppFactory.onMenuItemSelected(item, this, cache)) {
                    return true;
                }
                if (LoggingUI.onMenuItemSelected(item, this, cache)) {
                    refreshOnResume = true;
                    return true;
                }
        }

        return true;
    }

    private class LoadCacheHandler extends CancellableHandler {
        @Override
        public void handleRegularMessage(final Message msg) {
            if (UPDATE_LOAD_PROGRESS_DETAIL == msg.what && msg.obj instanceof String) {
                updateStatusMsg((String) msg.obj);
            } else {
                if (search == null) {
                    showToast(res.getString(R.string.err_dwld_details_failed));

                    progress.dismiss();
                    finish();
                    return;
                }

                if (search.getError() != null) {
                    showToast(res.getString(R.string.err_dwld_details_failed) + " " + search.getError().getErrorString(res) + ".");

                    progress.dismiss();
                    finish();
                    return;
                }

                updateStatusMsg(res.getString(R.string.cache_dialog_loading_details_status_render));

                // Data loaded, we're ready to show it!
                notifyDataSetChanged();
            }
        }

        private void updateStatusMsg(final String msg) {
            progress.setMessage(res.getString(R.string.cache_dialog_loading_details)
                    + "\n\n"
                    + msg);
        }

        @Override
        public void handleCancel(final Object extra) {
            finish();
        }

    }

    private void notifyDataSetChanged() {
        if (search == null) {
            return;
        }

        cache = search.getFirstCacheFromResult(LoadFlags.LOAD_ALL_DB_ONLY);

        if (cache == null) {
            progress.dismiss();
            showToast(res.getString(R.string.err_detail_cache_find_some));
            finish();
            return;
        }

        // allow cache to notify CacheDetailActivity when it changes so it can be reloaded
        cache.setChangeNotificationHandler(cacheChangeNotificationHandler);

        // action bar: title and icon
        if (StringUtils.isNotBlank(cache.getName())) {
            setTitle(cache.getName() + " (" + cache.getGeocode() + ')');
        } else {
            setTitle(cache.getGeocode());
        }
        ((TextView) findViewById(R.id.actionbar_title)).setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(cache.getType().markerId), null, null, null);

        // reset imagesList so Images view page will be redrawn
        imagesList = null;
        reinitializeViewPager();

        // rendering done! remove progress popup if any there
        invalidateOptionsMenuCompatible();
        progress.dismiss();
    }

    /**
     * Loads the cache with the given geocode or guid.
     */
    private class LoadCacheThread extends Thread {

        private CancellableHandler handler = null;
        private String geocode;
        private String guid;

        public LoadCacheThread(final String geocode, final String guid, final CancellableHandler handlerIn) {
            handler = handlerIn;

            if (StringUtils.isBlank(geocode) && StringUtils.isBlank(guid)) {
                showToast(res.getString(R.string.err_detail_cache_forgot));

                progress.dismiss();
                finish();
                return;
            }

            this.geocode = geocode;
            this.guid = guid;
        }

        @Override
        public void run() {
            search = Geocache.searchByGeocode(geocode, StringUtils.isBlank(geocode) ? guid : null, 0, false, handler);
            handler.sendMessage(Message.obtain());
        }
    }

    /**
     * Indicates whether the specified action can be used as an intent. This
     * method queries the package manager for installed packages that can
     * respond to an intent with the specified action. If no suitable package is
     * found, this method returns false.
     *
     * @param context
     *            The application's environment.
     * @param action
     *            The Intent action to check for availability.
     * @param uri
     *            The Intent URI to check for availability.
     *
     * @return True if an Intent with the specified action can be sent and
     *         responded to, false otherwise.
     */
    private static boolean isIntentAvailable(Context context, String action, Uri uri) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent;
        if (uri == null) {
            intent = new Intent(action);
        } else {
            intent = new Intent(action, uri);
        }
        final List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    private void addToCalendarWithIntent() {

        final boolean calendarAddOnAvailable = isIntentAvailable(this, ICalendar.INTENT, Uri.parse(ICalendar.URI_SCHEME + "://" + ICalendar.URI_HOST));

        if (calendarAddOnAvailable) {
            final Parameters params = new Parameters(
                    ICalendar.PARAM_NAME, cache.getName(),
                    ICalendar.PARAM_NOTE, StringUtils.defaultString(cache.getPersonalNote()),
                    ICalendar.PARAM_HIDDEN_DATE, String.valueOf(cache.getHiddenDate().getTime()),
                    ICalendar.PARAM_URL, StringUtils.defaultString(cache.getUrl()),
                    ICalendar.PARAM_COORDS, cache.getCoords() == null ? "" : cache.getCoords().format(GeopointFormatter.Format.LAT_LON_DECMINUTE_RAW),
                    ICalendar.PARAM_LOCATION, StringUtils.defaultString(cache.getLocation()),
                    ICalendar.PARAM_SHORT_DESC, StringUtils.defaultString(cache.getShortDescription()),
                    ICalendar.PARAM_START_TIME_MINUTES, StringUtils.defaultString(cache.guessEventTimeMinutes())
                    );

            startActivity(new Intent(ICalendar.INTENT,
                    Uri.parse(ICalendar.URI_SCHEME + "://" + ICalendar.URI_HOST + "?" + params.toString())));
        } else {
            // Inform user the calendar add-on is not installed and let them get it from Google Play
            new AlertDialog.Builder(this)
                    .setTitle(res.getString(R.string.addon_missing_title))
                    .setMessage(new StringBuilder(res.getString(R.string.helper_calendar_missing))
                            .append(' ')
                            .append(res.getString(R.string.addon_download_prompt))
                            .toString())
                    .setPositiveButton(getString(android.R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            final Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(ICalendar.CALENDAR_ADDON_URI));
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    })
                    .create()
                    .show();
        }
    }

    /**
     * Tries to navigate to the {@link Geocache} of this activity.
     */
    private void startDefaultNavigation() {
        NavigationAppFactory.startDefaultNavigationApplication(1, this, cache);
    }

    /**
     * Tries to navigate to the {@link Geocache} of this activity.
     */
    private void startDefaultNavigation2() {
        NavigationAppFactory.startDefaultNavigationApplication(2, this, cache);
    }

    /**
     * Wrapper for the referenced method in the xml-layout.
     */
    public void goDefaultNavigation(@SuppressWarnings("unused") View view) {
        startDefaultNavigation();
    }

    /**
     * referenced from XML view
     */
    public void showNavigationMenu(@SuppressWarnings("unused") View view) {
        NavigationAppFactory.showNavigationMenu(this, cache, null, null, true, true);
    }

    private void loadCacheImages() {
        if (imagesList != null) {
            return;
        }
        final PageViewCreator creator = getViewCreator(Page.IMAGES);
        if (creator == null) {
            return;
        }
        final View imageView = creator.getView();
        if (imageView == null) {
            return;
        }
        imagesList = new ImagesList(this, cache.getGeocode());
        imagesList.loadImages(imageView, cache.getImages(), false);
    }

    public static void startActivity(final Context context, final String geocode) {
        final Intent detailIntent = new Intent(context, CacheDetailActivity.class);
        detailIntent.putExtra(Intents.EXTRA_GEOCODE, geocode);
        context.startActivity(detailIntent);
    }

    /**
     * Enum of all possible pages with methods to get the view and a title.
     */
    public enum Page {
        DETAILS(R.string.detail),
        DESCRIPTION(R.string.cache_description),
        LOGS(R.string.cache_logs),
        LOGSFRIENDS(R.string.cache_logsfriends),
        WAYPOINTS(R.string.cache_waypoints),
        INVENTORY(R.string.cache_inventory),
        IMAGES(R.string.cache_images);

        final private int titleStringId;

        Page(final int titleStringId) {
            this.titleStringId = titleStringId;
        }
    }

    private class AttributeViewBuilder {
        private ViewGroup attributeIconsLayout; // layout for attribute icons
        private ViewGroup attributeDescriptionsLayout; // layout for attribute descriptions
        private boolean attributesShowAsIcons = true; // default: show icons
        /**
         * If the cache is from a non GC source, it might be without icons. Disable switching in those cases.
         */
        private boolean noAttributeIconsFound = false;
        private int attributeBoxMaxWidth;

        public void fillView(final LinearLayout attributeBox) {
            // first ensure that the view is empty
            attributeBox.removeAllViews();

            // maximum width for attribute icons is screen width - paddings of parents
            attributeBoxMaxWidth = Compatibility.getDisplayWidth();
            ViewParent child = attributeBox;
            do {
                if (child instanceof View) {
                    attributeBoxMaxWidth -= ((View) child).getPaddingLeft() + ((View) child).getPaddingRight();
                }
                child = child.getParent();
            } while (child != null);

            // delete views holding description / icons
            attributeDescriptionsLayout = null;
            attributeIconsLayout = null;

            attributeBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // toggle between attribute icons and descriptions
                    toggleAttributeDisplay(attributeBox, attributeBoxMaxWidth);
                }
            });

            // icons or text?
            //
            // also show icons when noAttributeImagesFound == true. Explanation:
            //  1. no icons could be found in the first invocation of this method
            //  2. user refreshes cache from web
            //  3. now this method is called again
            //  4. attributeShowAsIcons is false but noAttributeImagesFound is true
            //     => try to show them now
            if (attributesShowAsIcons || noAttributeIconsFound) {
                showAttributeIcons(attributeBox, attributeBoxMaxWidth);
            } else {
                showAttributeDescriptions(attributeBox);
            }
        }

        /**
         * lazy-creates the layout holding the icons of the caches attributes
         * and makes it visible
         */
        private void showAttributeIcons(LinearLayout attribBox, int parentWidth) {
            if (attributeIconsLayout == null) {
                attributeIconsLayout = createAttributeIconsLayout(parentWidth);
                // no matching icons found? show text
                if (noAttributeIconsFound) {
                    showAttributeDescriptions(attribBox);
                    return;
                }
            }
            attribBox.removeAllViews();
            attribBox.addView(attributeIconsLayout);
            attributesShowAsIcons = true;
        }

        /**
         * lazy-creates the layout holding the descriptions of the caches attributes
         * and makes it visible
         */
        private void showAttributeDescriptions(LinearLayout attribBox) {
            if (attributeDescriptionsLayout == null) {
                attributeDescriptionsLayout = createAttributeDescriptionsLayout();
            }
            attribBox.removeAllViews();
            attribBox.addView(attributeDescriptionsLayout);
            attributesShowAsIcons = false;
        }

        /**
         * toggle attribute descriptions and icons
         */
        private void toggleAttributeDisplay(LinearLayout attribBox, int parentWidth) {
            // Don't toggle when there are no icons to show.
            if (noAttributeIconsFound) {
                return;
            }

            // toggle
            if (attributesShowAsIcons) {
                showAttributeDescriptions(attribBox);
            } else {
                showAttributeIcons(attribBox, parentWidth);
            }
        }

        private ViewGroup createAttributeIconsLayout(int parentWidth) {
            final LinearLayout rows = new LinearLayout(CacheDetailActivity.this);
            rows.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            rows.setOrientation(LinearLayout.VERTICAL);

            LinearLayout attributeRow = newAttributeIconsRow();
            rows.addView(attributeRow);

            noAttributeIconsFound = true;

            for (final String attributeName : cache.getAttributes()) {
                // check if another attribute icon fits in this row
                attributeRow.measure(0, 0);
                final int rowWidth = attributeRow.getMeasuredWidth();
                final FrameLayout fl = (FrameLayout) getLayoutInflater().inflate(R.layout.attribute_image, null);
                final ImageView iv = (ImageView) fl.getChildAt(0);
                if ((parentWidth - rowWidth) < iv.getLayoutParams().width) {
                    // make a new row
                    attributeRow = newAttributeIconsRow();
                    rows.addView(attributeRow);
                }

                final boolean strikethru = !CacheAttribute.isEnabled(attributeName);
                final CacheAttribute attrib = CacheAttribute.getByRawName(CacheAttribute.trimAttributeName(attributeName));
                if (attrib != null) {
                    noAttributeIconsFound = false;
                    Drawable d = res.getDrawable(attrib.drawableId);
                    iv.setImageDrawable(d);
                    // strike through?
                    if (strikethru) {
                        // generate strikethru image with same properties as attribute image
                        final ImageView strikethruImage = new ImageView(CacheDetailActivity.this);
                        strikethruImage.setLayoutParams(iv.getLayoutParams());
                        d = res.getDrawable(R.drawable.attribute__strikethru);
                        strikethruImage.setImageDrawable(d);
                        fl.addView(strikethruImage);
                    }
                } else {
                    final Drawable d = res.getDrawable(R.drawable.attribute_unknown);
                    iv.setImageDrawable(d);
                }

                attributeRow.addView(fl);
            }

            return rows;
        }

        private LinearLayout newAttributeIconsRow() {
            final LinearLayout rowLayout = new LinearLayout(CacheDetailActivity.this);
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            return rowLayout;
        }

        private ViewGroup createAttributeDescriptionsLayout() {
            final LinearLayout descriptions = (LinearLayout) getLayoutInflater().inflate(
                    R.layout.attribute_descriptions, null);
            final TextView attribView = (TextView) descriptions.getChildAt(0);

            final StringBuilder buffer = new StringBuilder();
            for (String attributeName : cache.getAttributes()) {
                final boolean enabled = CacheAttribute.isEnabled(attributeName);
                // search for a translation of the attribute
                CacheAttribute attrib = CacheAttribute.getByRawName(CacheAttribute.trimAttributeName(attributeName));
                if (attrib == null) {
                    attrib = CacheAttribute.UNKNOWN;
                }
                attributeName = attrib.getL10n(enabled);
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(attributeName);
            }

            attribView.setText(buffer);

            return descriptions;
        }
    }

    /**
     * Creator for details-view.
     */
    private class DetailsViewCreator extends AbstractCachingPageViewCreator<ScrollView> {
        /**
         * Reference to the details list, so that the helper-method can access it without an additional argument
         */
        private LinearLayout detailsList;

        // TODO Do we need this thread-references?
        private StoreCacheThread storeThread;
        private RefreshCacheThread refreshThread;
        private Thread watchlistThread;

        @Override
        public ScrollView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ScrollView) getLayoutInflater().inflate(R.layout.cachedetail_details_page, null);

            // Start loading preview map
            if (Settings.isStoreOfflineMaps()) {
                new PreviewMapTask().execute((Void) null);
            }

            detailsList = (LinearLayout) view.findViewById(R.id.details_list);
            final CacheDetailsCreator details = new CacheDetailsCreator(CacheDetailActivity.this, detailsList);

            // cache name (full name)
            final Spannable span = (new Spannable.Factory()).newSpannable(Html.fromHtml(cache.getName()).toString());
            if (cache.isDisabled() || cache.isArchived()) { // strike
                span.setSpan(new StrikethroughSpan(), 0, span.toString().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (cache.isArchived()) {
                span.setSpan(new ForegroundColorSpan(res.getColor(R.color.archived_cache_color)), 0, span.toString().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            registerForContextMenu(details.add(R.string.cache_name, span));
            details.add(R.string.cache_type, cache.getType().getL10n());
            details.addSize(cache);
            registerForContextMenu(details.add(R.string.cache_geocode, cache.getGeocode()));
            details.addCacheState(cache);

            details.addDistance(cache, cacheDistanceView);
            cacheDistanceView = details.getValueView();

            details.addDifficulty(cache);
            details.addTerrain(cache);
            details.addRating(cache);

            // favorite count
            if (cache.getFavoritePoints() > 0) {
                details.add(R.string.cache_favorite, cache.getFavoritePoints() + "×");
            }

            // own rating
            if (cache.getMyVote() > 0) {
                details.addStars(R.string.cache_own_rating, cache.getMyVote());
            }

            // cache author
            if (StringUtils.isNotBlank(cache.getOwnerDisplayName()) || StringUtils.isNotBlank(cache.getOwnerUserId())) {
                final TextView ownerView = details.add(R.string.cache_owner, "");
                if (StringUtils.isNotBlank(cache.getOwnerDisplayName())) {
                    ownerView.setText(cache.getOwnerDisplayName(), TextView.BufferType.SPANNABLE);
                } else { // OwnerReal guaranteed to be not blank based on above
                    ownerView.setText(cache.getOwnerUserId(), TextView.BufferType.SPANNABLE);
                }
                ownerView.setOnClickListener(new OwnerActionsClickListener(cache));
            }

            // cache hidden
            if (cache.getHiddenDate() != null) {
                final long time = cache.getHiddenDate().getTime();
                if (time > 0) {
                    String dateString = Formatter.formatFullDate(time);
                    if (cache.isEventCache()) {
                        dateString = DateUtils.formatDateTime(cgeoapplication.getInstance().getBaseContext(), time, DateUtils.FORMAT_SHOW_WEEKDAY) + ", " + dateString;
                    }
                    details.add(cache.isEventCache() ? R.string.cache_event : R.string.cache_hidden, dateString);
                }
            }

            // cache location
            if (StringUtils.isNotBlank(cache.getLocation())) {
                details.add(R.string.cache_location, cache.getLocation());
            }

            // cache coordinates
            if (cache.getCoords() != null) {
                final TextView valueView = details.add(R.string.cache_coordinates, cache.getCoords().toString());
                valueView.setOnClickListener(new CoordinatesFormatSwitcher(cache.getCoords()));
                registerForContextMenu(valueView);
            }

            // cache attributes
            if (!cache.getAttributes().isEmpty()) {
                new AttributeViewBuilder().fillView((LinearLayout) view.findViewById(R.id.attributes_innerbox));
                view.findViewById(R.id.attributes_box).setVisibility(View.VISIBLE);
            }

            updateOfflineBox(view, cache, res, new RefreshCacheClickListener(), new DropCacheClickListener(), new StoreCacheClickListener());

            // watchlist
            final Button buttonWatchlistAdd = (Button) view.findViewById(R.id.add_to_watchlist);
            final Button buttonWatchlistRemove = (Button) view.findViewById(R.id.remove_from_watchlist);
            buttonWatchlistAdd.setOnClickListener(new AddToWatchlistClickListener());
            buttonWatchlistRemove.setOnClickListener(new RemoveFromWatchlistClickListener());
            updateWatchlistBox();

            // favorite points
            final Button buttonFavPointAdd = (Button) view.findViewById(R.id.add_to_favpoint);
            final Button buttonFavPointRemove = (Button) view.findViewById(R.id.remove_from_favpoint);
            buttonFavPointAdd.setOnClickListener(new FavoriteAddClickListener());
            buttonFavPointRemove.setOnClickListener(new FavoriteRemoveClickListener());
            updateFavPointBox();

            // list
            final Button buttonChangeList = (Button) view.findViewById(R.id.change_list);
            buttonChangeList.setOnClickListener(new ChangeListClickListener());
            updateListBox();

            // data license
            final IConnector connector = ConnectorFactory.getConnector(cache);
            if (connector != null) {
                final String license = connector.getLicenseText(cache);
                if (StringUtils.isNotBlank(license)) {
                    view.findViewById(R.id.license_box).setVisibility(View.VISIBLE);
                    final TextView licenseView = ((TextView) view.findViewById(R.id.license));
                    licenseView.setText(Html.fromHtml(license), BufferType.SPANNABLE);
                    licenseView.setClickable(true);
                    licenseView.setMovementMethod(AnchorAwareLinkMovementMethod.getInstance());
                } else {
                    view.findViewById(R.id.license_box).setVisibility(View.GONE);
                }
            }

            return view;
        }

        private class StoreCacheHandler extends CancellableHandler {
            @Override
            public void handleRegularMessage(Message msg) {
                if (UPDATE_LOAD_PROGRESS_DETAIL == msg.what && msg.obj instanceof String) {
                    updateStatusMsg((String) msg.obj);
                } else {
                    storeThread = null;
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }

            private void updateStatusMsg(final String msg) {
                progress.setMessage(res.getString(R.string.cache_dialog_offline_save_message)
                        + "\n\n"
                        + msg);
            }
        }

        private class RefreshCacheHandler extends CancellableHandler {
            @Override
            public void handleRegularMessage(Message msg) {
                if (UPDATE_LOAD_PROGRESS_DETAIL == msg.what && msg.obj instanceof String) {
                    updateStatusMsg((String) msg.obj);
                } else {
                    refreshThread = null;
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }

            private void updateStatusMsg(final String msg) {
                progress.setMessage(res.getString(R.string.cache_dialog_refresh_message)
                        + "\n\n"
                        + msg);
            }
        }

        private class DropCacheHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                CacheDetailActivity.this.notifyDataSetChanged();
            }
        }

        private class StoreCacheClickListener implements View.OnClickListener {
            @Override
            public void onClick(View arg0) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_detail_still_working));
                    return;
                }

                if (Settings.getChooseList()) {
                    // let user select list to store cache in
                    new StoredList.UserInterface(CacheDetailActivity.this).promptForListSelection(R.string.list_title,
                            new RunnableWithArgument<Integer>() {
                                @Override
                                public void run(final Integer selectedListId) {
                                    storeCache(selectedListId);
                                }
                            }, true, StoredList.TEMPORARY_LIST_ID);
                } else {
                    storeCache(StoredList.TEMPORARY_LIST_ID);
                }
            }

            protected void storeCache(int listId) {
                final StoreCacheHandler storeCacheHandler = new StoreCacheHandler();

                progress.show(CacheDetailActivity.this, res.getString(R.string.cache_dialog_offline_save_title), res.getString(R.string.cache_dialog_offline_save_message), true, storeCacheHandler.cancelMessage());

                if (storeThread != null) {
                    storeThread.interrupt();
                }

                storeThread = new StoreCacheThread(listId, storeCacheHandler);
                storeThread.start();
            }
        }

        private class RefreshCacheClickListener implements View.OnClickListener {
            @Override
            public void onClick(View arg0) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_detail_still_working));
                    return;
                }

                if (!Network.isNetworkConnected(getApplicationContext())) {
                    showToast(getString(R.string.err_server));
                    return;
                }

                final RefreshCacheHandler refreshCacheHandler = new RefreshCacheHandler();

                progress.show(CacheDetailActivity.this, res.getString(R.string.cache_dialog_refresh_title), res.getString(R.string.cache_dialog_refresh_message), true, refreshCacheHandler.cancelMessage());

                if (refreshThread != null) {
                    refreshThread.interrupt();
                }

                refreshThread = new RefreshCacheThread(refreshCacheHandler);
                refreshThread.start();
            }
        }

        private class StoreCacheThread extends Thread {
            final private int listId;
            final private CancellableHandler handler;

            public StoreCacheThread(final int listId, final CancellableHandler handler) {
                this.listId = listId;
                this.handler = handler;
            }

            @Override
            public void run() {
                cache.store(listId, handler);
            }
        }

        private class RefreshCacheThread extends Thread {
            final private CancellableHandler handler;

            public RefreshCacheThread(final CancellableHandler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                cache.refresh(cache.getListId(), handler);

                handler.sendEmptyMessage(0);
            }
        }

        private class DropCacheClickListener implements View.OnClickListener {
            @Override
            public void onClick(View arg0) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_detail_still_working));
                    return;
                }

                final DropCacheHandler dropCacheHandler = new DropCacheHandler();

                progress.show(CacheDetailActivity.this, res.getString(R.string.cache_dialog_offline_drop_title), res.getString(R.string.cache_dialog_offline_drop_message), true, null);
                new DropCacheThread(dropCacheHandler).start();
            }
        }

        private class DropCacheThread extends Thread {

            private Handler handler = null;

            public DropCacheThread(Handler handlerIn) {
                handler = handlerIn;
            }

            @Override
            public void run() {
                cache.drop(handler);
            }
        }

        /**
         * Abstract Listener for add / remove buttons for watchlist
         */
        private abstract class AbstractWatchlistClickListener implements View.OnClickListener {
            public void doExecute(int titleId, int messageId, Thread thread) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_watchlist_still_managing));
                    return;
                }
                progress.show(CacheDetailActivity.this, res.getString(titleId), res.getString(messageId), true, null);

                if (watchlistThread != null) {
                    watchlistThread.interrupt();
                }

                watchlistThread = thread;
                watchlistThread.start();
            }
        }

        /**
         * Listener for "add to watchlist" button
         */
        private class AddToWatchlistClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_watchlist_add_title,
                        R.string.cache_dialog_watchlist_add_message,
                        new WatchlistAddThread(new WatchlistHandler()));
            }
        }

        /**
         * Listener for "remove from watchlist" button
         */
        private class RemoveFromWatchlistClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_watchlist_remove_title,
                        R.string.cache_dialog_watchlist_remove_message,
                        new WatchlistRemoveThread(new WatchlistHandler()));
            }
        }

        /** Thread to add this cache to the watchlist of the user */
        private class WatchlistAddThread extends Thread {
            private final Handler handler;

            public WatchlistAddThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(ConnectorFactory.getConnector(cache).addToWatchlist(cache) ? 1 : -1);
            }
        }

        /** Thread to remove this cache from the watchlist of the user */
        private class WatchlistRemoveThread extends Thread {
            private final Handler handler;

            public WatchlistRemoveThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(ConnectorFactory.getConnector(cache).removeFromWatchlist(cache) ? 1 : -1);
            }
        }

        /** Thread to add this cache to the favorite list of the user */
        private class FavoriteAddThread extends Thread {
            private final Handler handler;

            public FavoriteAddThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(GCConnector.addToFavorites(cache) ? 1 : -1);
            }
        }

        /** Thread to remove this cache to the favorite list of the user */
        private class FavoriteRemoveThread extends Thread {
            private final Handler handler;

            public FavoriteRemoveThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(GCConnector.removeFromFavorites(cache) ? 1 : -1);
            }
        }

        private class FavoriteUpdateHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                progress.dismiss();
                if (msg.what == -1) {
                    showToast(res.getString(R.string.err_favorite_failed));
                } else {
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }
        }

        /**
         * Listener for "add to favorites" button
         */
        private class FavoriteAddClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_favorite_add_title,
                        R.string.cache_dialog_favorite_add_message,
                        new FavoriteAddThread(new FavoriteUpdateHandler()));
            }
        }

        /**
         * Listener for "remove from favorites" button
         */
        private class FavoriteRemoveClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_favorite_remove_title,
                        R.string.cache_dialog_favorite_remove_message,
                        new FavoriteRemoveThread(new FavoriteUpdateHandler()));
            }
        }

        /**
         * Listener for "change list" button
         */
        private class ChangeListClickListener implements View.OnClickListener {
            @Override
            public void onClick(View view) {
                new StoredList.UserInterface(CacheDetailActivity.this).promptForListSelection(R.string.list_title,
                        new RunnableWithArgument<Integer>() {
                            @Override
                            public void run(final Integer selectedListId) {
                                switchListById(selectedListId);
                            }
                        }, true, cache.getListId());
            }
        }

        /**
         * move cache to another list
         *
         * @param listId
         *            the ID of the list
         */
        public void switchListById(int listId) {
            if (listId < 0) {
                return;
            }

            Settings.saveLastList(listId);
            cgData.moveToList(cache, listId);
            updateListBox();
        }

        /**
         * shows/hides buttons, sets text in watchlist box
         */
        private void updateWatchlistBox() {
            final LinearLayout layout = (LinearLayout) view.findViewById(R.id.watchlist_box);
            final boolean supportsWatchList = cache.supportsWatchList();
            layout.setVisibility(supportsWatchList ? View.VISIBLE : View.GONE);
            if (!supportsWatchList) {
                return;
            }
            final Button buttonAdd = (Button) view.findViewById(R.id.add_to_watchlist);
            final Button buttonRemove = (Button) view.findViewById(R.id.remove_from_watchlist);
            final TextView text = (TextView) view.findViewById(R.id.watchlist_text);

            if (cache.isOnWatchlist() || cache.isOwner()) {
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setVisibility(View.VISIBLE);
                text.setText(R.string.cache_watchlist_on);
            } else {
                buttonAdd.setVisibility(View.VISIBLE);
                buttonRemove.setVisibility(View.GONE);
                text.setText(R.string.cache_watchlist_not_on);
            }

            // the owner of a cache has it always on his watchlist. Adding causes an error
            if (cache.isOwner()) {
                buttonAdd.setEnabled(false);
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setEnabled(false);
                buttonRemove.setVisibility(View.GONE);
            }

        }

        /**
         * shows/hides buttons, sets text in watchlist box
         */
        private void updateFavPointBox() {
            final LinearLayout layout = (LinearLayout) view.findViewById(R.id.favpoint_box);
            final boolean supportsFavoritePoints = cache.supportsFavoritePoints();
            layout.setVisibility(supportsFavoritePoints ? View.VISIBLE : View.GONE);
            if (!supportsFavoritePoints || cache.isOwner() || !Settings.isPremiumMember()) {
                return;
            }
            final Button buttonAdd = (Button) view.findViewById(R.id.add_to_favpoint);
            final Button buttonRemove = (Button) view.findViewById(R.id.remove_from_favpoint);
            final TextView text = (TextView) view.findViewById(R.id.favpoint_text);

            if (cache.isFavorite()) {
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setVisibility(View.VISIBLE);
                text.setText(R.string.cache_favpoint_on);
            } else {
                buttonAdd.setVisibility(View.VISIBLE);
                buttonRemove.setVisibility(View.GONE);
                text.setText(R.string.cache_favpoint_not_on);
            }

            // Add/remove to Favorites is only possible if the cache has been found
            if (!cache.isFound()) {
                buttonAdd.setEnabled(false);
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setEnabled(false);
                buttonRemove.setVisibility(View.GONE);
            }
        }

        /**
         * shows/hides/updates list box
         */
        private void updateListBox() {
            final View box = view.findViewById(R.id.list_box);

            if (cache.isOffline()) {
                // show box
                box.setVisibility(View.VISIBLE);

                // update text
                final TextView text = (TextView) view.findViewById(R.id.list_text);
                final StoredList list = cgData.getList(cache.getListId());
                if (list != null) {
                    text.setText(res.getString(R.string.cache_list_text) + " " + list.title);
                } else {
                    // this should not happen
                    text.setText(R.string.cache_list_unknown);
                }
            } else {
                // hide box
                box.setVisibility(View.GONE);
            }
        }

        /**
         * Handler, called when watchlist add or remove is done
         */
        private class WatchlistHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                watchlistThread = null;
                progress.dismiss();
                if (msg.what == -1) {
                    showToast(res.getString(R.string.err_watchlist_failed));
                } else {
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }
        }

        private class PreviewMapTask extends AsyncTask<Void, Void, BitmapDrawable> {
            @Override
            protected BitmapDrawable doInBackground(Void... parameters) {
                try {
                    // persistent preview from storage
                    Bitmap image = decode(cache);

                    if (image == null) {
                        StaticMapsProvider.storeCachePreviewMap(cache);
                        image = decode(cache);
                        if (image == null) {
                            return null;
                        }
                    }

                    return ImageUtils.scaleBitmapToFitDisplay(image);
                } catch (final Exception e) {
                    Log.w("CacheDetailActivity.PreviewMapTask", e);
                    return null;
                }
            }

            private Bitmap decode(final Geocache cache) {
                return StaticMapsProvider.getPreviewMap(cache.getGeocode());
            }

            @Override
            protected void onPostExecute(BitmapDrawable image) {
                if (image == null) {
                    return;
                }

                try {
                    final Bitmap bitmap = image.getBitmap();
                    if (bitmap == null || bitmap.getWidth() <= 10) {
                        return;
                    }

                    ((ImageView) view.findViewById(R.id.map_preview)).setImageDrawable(image);
                    view.findViewById(R.id.map_preview_box).setVisibility(View.VISIBLE);
                } catch (final Exception e) {
                    Log.e("CacheDetailActivity.PreviewMapTask", e);
                }
            }
        }
    }

    protected class DescriptionViewCreator extends AbstractCachingPageViewCreator<ScrollView> {

        @InjectView(R.id.personalnote) protected TextView personalNoteView;

        @Override
        public ScrollView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ScrollView) getLayoutInflater().inflate(R.layout.cachedetail_description_page, null);
            Views.inject(this, view);

            // cache short description
            if (StringUtils.isNotBlank(cache.getShortDescription())) {
                new LoadDescriptionTask(cache.getShortDescription(), view.findViewById(R.id.shortdesc), null, null).execute();
            }

            // long description
            if (StringUtils.isNotBlank(cache.getDescription())) {
                if (Settings.isAutoLoadDescription()) {
                    loadLongDescription();
                } else {
                    final Button showDesc = (Button) view.findViewById(R.id.show_description);
                    showDesc.setVisibility(View.VISIBLE);
                    showDesc.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View arg0) {
                            loadLongDescription();
                        }
                    });
                }
            }

            // cache personal note
            setPersonalNote();
            personalNoteView.setMovementMethod(AnchorAwareLinkMovementMethod.getInstance());
            registerForContextMenu(personalNoteView);
            final Button personalNoteEdit = (Button) view.findViewById(R.id.edit_personalnote);
            personalNoteEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (cache.isOffline()) {
                        editPersonalNote();
                    } else {
                        warnPersonalNoteNeedsStoring();
                    }
                }
            });
            final Button personalNoteUpload = (Button) view.findViewById(R.id.upload_personalnote);
            if (cache.isOffline() && ConnectorFactory.getConnector(cache).supportsPersonalNote()) {
                personalNoteUpload.setVisibility(View.VISIBLE);
                personalNoteUpload.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        uploadPersonalNote();
                    }
                });
            } else {
                personalNoteUpload.setVisibility(View.GONE);
            }

            // cache hint and spoiler images
            final View hintBoxView = view.findViewById(R.id.hint_box);
            if (StringUtils.isNotBlank(cache.getHint()) || CollectionUtils.isNotEmpty(cache.getSpoilers())) {
                hintBoxView.setVisibility(View.VISIBLE);
            } else {
                hintBoxView.setVisibility(View.GONE);
            }

            final TextView hintView = ((TextView) view.findViewById(R.id.hint));
            if (StringUtils.isNotBlank(cache.getHint())) {
                if (TextUtils.containsHtml(cache.getHint())) {
                    hintView.setText(Html.fromHtml(cache.getHint(), new HtmlImage(cache.getGeocode(), false, cache.getListId(), false), null), TextView.BufferType.SPANNABLE);
                    hintView.setText(CryptUtils.rot13((Spannable) hintView.getText()));
                }
                else {
                    hintView.setText(CryptUtils.rot13(cache.getHint()));
                }
                hintView.setVisibility(View.VISIBLE);
                hintView.setClickable(true);
                hintView.setOnClickListener(new DecryptTextClickListener());
                registerForContextMenu(hintView);
            } else {
                hintView.setVisibility(View.GONE);
                hintView.setClickable(false);
                hintView.setOnClickListener(null);
            }

            final TextView spoilerlinkView = ((TextView) view.findViewById(R.id.hint_spoilerlink));
            if (CollectionUtils.isNotEmpty(cache.getSpoilers())) {
                spoilerlinkView.setVisibility(View.VISIBLE);
                spoilerlinkView.setClickable(true);
                spoilerlinkView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        if (cache == null || CollectionUtils.isEmpty(cache.getSpoilers())) {
                            showToast(res.getString(R.string.err_detail_no_spoiler));
                            return;
                        }

                        ImagesActivity.startActivitySpoilerImages(CacheDetailActivity.this, cache.getGeocode(), cache.getSpoilers());
                    }
                });
            } else {
                spoilerlinkView.setVisibility(View.GONE);
                spoilerlinkView.setClickable(true);
                spoilerlinkView.setOnClickListener(null);
            }

            return view;
        }

        private void editPersonalNote() {
            if (cache.isOffline()) {
                editNoteDialogListener = new EditNoteDialogListener() {
                    @Override
                    public void onFinishEditNoteDialog(final String note) {
                        cache.setPersonalNote(note);
                        cache.parseWaypointsFromNote();
                        setPersonalNote();
                        cgData.saveCache(cache, EnumSet.of(SaveFlag.SAVE_DB));
                        CacheDetailActivity.this.notifyDataSetChanged();
                    }
                };
                final FragmentManager fm = getSupportFragmentManager();
                final EditNoteDialog dialog = EditNoteDialog.newInstance(cache.getPersonalNote());
                dialog.show(fm, "fragment_edit_note");
            }
        }

        Thread currentThread;

        private void uploadPersonalNote() {
            final SimpleHandler myHandler = new SimpleHandler();

            Message cancelMessage = myHandler.cancelMessage(res.getString(R.string.cache_personal_note_upload_cancelled));
            progress.show(CacheDetailActivity.this, res.getString(R.string.cache_personal_note_uploading), res.getString(R.string.cache_personal_note_uploading), true, cancelMessage);

            if (currentThread != null) {
                currentThread.interrupt();
            }
            currentThread = new UploadPersonalNoteThread(cache, myHandler);
            currentThread.start();
        }

        private void setPersonalNote() {
            final String personalNote = cache.getPersonalNote();
            personalNoteView.setText(personalNote, TextView.BufferType.SPANNABLE);
            if (StringUtils.isNotBlank(personalNote)) {
                personalNoteView.setVisibility(View.VISIBLE);
            } else {
                personalNoteView.setVisibility(View.GONE);
            }
        }

        private void loadLongDescription() {
            final Button showDesc = (Button) view.findViewById(R.id.show_description);
            showDesc.setVisibility(View.GONE);
            showDesc.setOnClickListener(null);
            view.findViewById(R.id.loading).setVisibility(View.VISIBLE);

            new LoadDescriptionTask(cache.getDescription(), view.findViewById(R.id.longdesc), view.findViewById(R.id.loading), view.findViewById(R.id.shortdesc)).execute();
        }

        private void warnPersonalNoteNeedsStoring() {
            final AlertDialog.Builder builder = new AlertDialog.Builder(CacheDetailActivity.this);
            builder.setTitle(R.string.cache_personal_note_unstored);
            builder.setMessage(R.string.cache_personal_note_store);
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // do nothing
                }
            });

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    cache.store(null);
                    editPersonalNote();
                }

            });
            final AlertDialog dialog = builder.create();
            dialog.setOwnerActivity(CacheDetailActivity.this);
            dialog.show();
        }

    }

    @Override
    public void onFinishEditNoteDialog(final String note) {
        editNoteDialogListener.onFinishEditNoteDialog(note);
    }

    /**
     * Loads the description in background. <br />
     * <br />
     * Params:
     * <ol>
     * <li>description string (String)</li>
     * <li>target description view (TextView)</li>
     * <li>loading indicator view (View, may be null)</li>
     * </ol>
     */
    private class LoadDescriptionTask extends AsyncTask<Object, Void, Void> {
        private final View loadingIndicatorView;
        private final TextView descriptionView;
        private final String descriptionString;
        private Spanned description;
        private final View shortDescView;

        public LoadDescriptionTask(final String description, final View descriptionView, final View loadingIndicatorView, final View shortDescView) {
            this.descriptionString = description;
            this.descriptionView = (TextView) descriptionView;
            this.loadingIndicatorView = loadingIndicatorView;
            this.shortDescView = shortDescView;
        }

        @Override
        protected Void doInBackground(Object... params) {
            try {
                // Fast preview: parse only HTML without loading any images
                final HtmlImageCounter imageCounter = new HtmlImageCounter();
                final UnknownTagsHandler unknownTagsHandler = new UnknownTagsHandler();
                description = Html.fromHtml(descriptionString, imageCounter, unknownTagsHandler);
                publishProgress();

                boolean needsRefresh = false;
                if (imageCounter.getImageCount() > 0) {
                    // Complete view: parse again with loading images - if necessary ! If there are any images causing problems the user can see at least the preview
                    description = Html.fromHtml(descriptionString, new HtmlImage(cache.getGeocode(), true, cache.getListId(), false), unknownTagsHandler);
                    needsRefresh = true;
                }

                // If description has an HTML construct which may be problematic to render, add a note at the end of the long description.
                // Technically, it may not be a table, but a pre, which has the same problems as a table, so the message is ok even though
                // sometimes technically incorrect.
                if (unknownTagsHandler.isProblematicDetected() && descriptionView != null) {
                    final int startPos = description.length();
                    final IConnector connector = ConnectorFactory.getConnector(cache);
                    final Spanned tableNote = Html.fromHtml(res.getString(R.string.cache_description_table_note, "<a href=\"" + cache.getUrl() + "\">" + connector.getName() + "</a>"));
                    ((Editable) description).append("\n\n").append(tableNote);
                    ((Editable) description).setSpan(new StyleSpan(Typeface.ITALIC), startPos, description.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    needsRefresh = true;
                }

                if (needsRefresh) {
                    publishProgress();
                }
            } catch (final Exception e) {
                Log.e("LoadDescriptionTask: ", e);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            if (description == null) {
                showToast(res.getString(R.string.err_load_descr_failed));
                return;
            }
            if (StringUtils.isNotBlank(descriptionString)) {
                try {
                    descriptionView.setText(description, TextView.BufferType.SPANNABLE);
                } catch (final Exception e) {
                    // On 4.1, there is sometimes a crash on measuring the layout: https://code.google.com/p/android/issues/detail?id=35412
                    Log.e("Android bug setting text: ", e);
                    // remove the formatting by converting to a simple string
                    descriptionView.setText(description.toString());
                }
                descriptionView.setMovementMethod(AnchorAwareLinkMovementMethod.getInstance());
                fixTextColor(descriptionView, descriptionString);
                descriptionView.setVisibility(View.VISIBLE);
                registerForContextMenu(descriptionView);

                hideDuplicatedShortDescription();
            }
        }

        /**
         * Hide the short description, if it is contained somewhere at the start of the long description.
         */
        private void hideDuplicatedShortDescription() {
            if (shortDescView != null) {
                final String shortDescription = cache.getShortDescription();
                if (StringUtils.isNotBlank(shortDescription)) {
                    final int index = descriptionString.indexOf(shortDescription);
                    if (index >= 0 && index < 200) {
                        shortDescView.setVisibility(View.GONE);
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            if (null != loadingIndicatorView) {
                loadingIndicatorView.setVisibility(View.GONE);
            }
        }

        /**
         * Handle caches with black font color in dark skin and white font color in light skin
         * by changing background color of the view
         *
         * @param view
         *            containing the text
         * @param text
         *            to be checked
         */
        private void fixTextColor(final TextView view, final String text) {
            int backcolor;
            if (Settings.isLightSkin()) {
                backcolor = color.white;

                for (final Pattern pattern : LIGHT_COLOR_PATTERNS) {
                    final MatcherWrapper matcher = new MatcherWrapper(pattern, text);
                    if (matcher.find()) {
                        view.setBackgroundResource(color.darker_gray);
                        return;
                    }
                }
            } else {
                backcolor = color.black;

                for (final Pattern pattern : DARK_COLOR_PATTERNS) {
                    final MatcherWrapper matcher = new MatcherWrapper(pattern, text);
                    if (matcher.find()) {
                        view.setBackgroundResource(color.darker_gray);
                        return;
                    }
                }
            }
            view.setBackgroundResource(backcolor);
        }
    }

    private class WaypointsViewCreator extends AbstractCachingPageViewCreator<ScrollView> {

        @Override
        public ScrollView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ScrollView) getLayoutInflater().inflate(R.layout.cachedetail_waypoints_page, null);

            final LinearLayout waypoints = (LinearLayout) view.findViewById(R.id.waypoints);

            // sort waypoints: PP, Sx, FI, OWN
            final List<Waypoint> sortedWaypoints = new ArrayList<Waypoint>(cache.getWaypoints());
            Collections.sort(sortedWaypoints);

            for (final Waypoint wpt : sortedWaypoints) {
                final LinearLayout waypointView = (LinearLayout) getLayoutInflater().inflate(R.layout.waypoint_item, null);

                // coordinates
                if (null != wpt.getCoords()) {
                    final TextView coordinatesView = (TextView) waypointView.findViewById(R.id.coordinates);
                    coordinatesView.setOnClickListener(new CoordinatesFormatSwitcher(wpt.getCoords()));
                    coordinatesView.setText(wpt.getCoords().toString());
                    coordinatesView.setVisibility(View.VISIBLE);
                }

                // info
                final String waypointInfo = Formatter.formatWaypointInfo(wpt);
                if (StringUtils.isNotBlank(waypointInfo)) {
                    final TextView infoView = (TextView) waypointView.findViewById(R.id.info);
                    infoView.setText(waypointInfo);
                    infoView.setVisibility(View.VISIBLE);
                }

                // title
                final TextView nameView = (TextView) waypointView.findViewById(R.id.name);
                if (StringUtils.isNotBlank(wpt.getName())) {
                    nameView.setText(StringEscapeUtils.unescapeHtml4(wpt.getName()));
                } else if (null != wpt.getCoords()) {
                    nameView.setText(wpt.getCoords().toString());
                } else {
                    nameView.setText(res.getString(R.string.waypoint));
                }
                wpt.setIcon(res, nameView);

                // visited
                if (wpt.isVisited()) {
                    final TypedValue a = new TypedValue();
                    getTheme().resolveAttribute(R.attr.text_color_grey, a, true);
                    if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        // really should be just a color!
                        nameView.setTextColor(a.data);
                    }
                }

                // note
                if (StringUtils.isNotBlank(wpt.getNote())) {
                    final TextView noteView = (TextView) waypointView.findViewById(R.id.note);
                    noteView.setVisibility(View.VISIBLE);
                    if (TextUtils.containsHtml(wpt.getNote())) {
                        noteView.setText(Html.fromHtml(wpt.getNote()), TextView.BufferType.SPANNABLE);
                    }
                    else {
                        noteView.setText(wpt.getNote());
                    }
                }

                final View wpNavView = waypointView.findViewById(R.id.wpDefaultNavigation);
                wpNavView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NavigationAppFactory.startDefaultNavigationApplication(1, CacheDetailActivity.this, wpt);
                    }
                });
                wpNavView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        NavigationAppFactory.startDefaultNavigationApplication(2, CacheDetailActivity.this, wpt);
                        return true;
                    }
                });

                registerForContextMenu(waypointView);
                waypointView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openContextMenu(v);
                    }
                });

                waypointView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        EditWaypointActivity.startActivityEditWaypoint(CacheDetailActivity.this, wpt.getId());
                        refreshOnResume = true;
                        return true;
                    }
                });

                waypoints.addView(waypointView);
            }

            final Button addWaypoint = (Button) view.findViewById(R.id.add_waypoint);
            addWaypoint.setClickable(true);
            addWaypoint.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    EditWaypointActivity.startActivityAddWaypoint(CacheDetailActivity.this, cache);
                    refreshOnResume = true;
                }
            });

            return view;
        }
    }

    private class InventoryViewCreator extends AbstractCachingPageViewCreator<ListView> {

        @Override
        public ListView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ListView) getLayoutInflater().inflate(R.layout.cachedetail_inventory_page, null);

            // TODO: fix layout, then switch back to Android-resource and delete copied one
            // this copy is modified to respect the text color
            view.setAdapter(new ArrayAdapter<Trackable>(CacheDetailActivity.this, R.layout.simple_list_item_1, cache.getInventory()));
            view.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                    final Object selection = arg0.getItemAtPosition(arg2);
                    if (selection instanceof Trackable) {
                        final Trackable trackable = (Trackable) selection;
                        TrackableActivity.startActivity(CacheDetailActivity.this, trackable.getGuid(), trackable.getGeocode(), trackable.getName());
                    }
                }
            });

            return view;
        }
    }

    private class ImagesViewCreator extends AbstractCachingPageViewCreator<View> {

        @Override
        public View getDispatchedView() {
            if (cache == null) {
                return null; // something is really wrong
            }

            view = getLayoutInflater().inflate(R.layout.cachedetail_images_page, null);
            if (imagesList == null && isCurrentPage(Page.IMAGES)) {
                loadCacheImages();
            }
            return view;
        }
    }

    public static void startActivity(final Context context, final String geocode, final String cacheName) {
        final Intent cachesIntent = new Intent(context, CacheDetailActivity.class);
        cachesIntent.putExtra(Intents.EXTRA_GEOCODE, geocode);
        cachesIntent.putExtra(Intents.EXTRA_NAME, cacheName);
        context.startActivity(cachesIntent);
    }

    public static void startActivityGuid(final Context context, final String guid, final String cacheName) {
        final Intent cacheIntent = new Intent(context, CacheDetailActivity.class);
        cacheIntent.putExtra(Intents.EXTRA_GUID, guid);
        cacheIntent.putExtra(Intents.EXTRA_NAME, cacheName);
        context.startActivity(cacheIntent);
    }

    /**
     * A dialog to allow the user to select reseting coordinates local/remote/both.
     */
    private AlertDialog createResetCacheCoordinatesDialog(final Geocache cache, final Waypoint wpt) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.waypoint_reset_cache_coords);

        final String[] items = new String[] { res.getString(R.string.waypoint_localy_reset_cache_coords), res.getString(R.string.waypoint_reset_local_and_remote_cache_coords) };
        builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, final int which) {
                dialog.dismiss();
                final ProgressDialog progressDialog = ProgressDialog.show(CacheDetailActivity.this, getString(R.string.cache), getString(R.string.waypoint_reset), true);
                final HandlerResetCoordinates handler = new HandlerResetCoordinates(CacheDetailActivity.this, progressDialog, which == 1);
                new ResetCoordsThread(cache, handler, wpt, which == 0 || which == 1, which == 1, progressDialog).start();
            }
        });
        return builder.create();
    }

    private static class HandlerResetCoordinates extends WeakReferenceHandler<CacheDetailActivity> {
        private boolean remoteFinished = false;
        private boolean localFinished = false;
        private final ProgressDialog progressDialog;
        private final boolean resetRemote;

        protected HandlerResetCoordinates(CacheDetailActivity activity, ProgressDialog progressDialog, boolean resetRemote) {
            super(activity);
            this.progressDialog = progressDialog;
            this.resetRemote = resetRemote;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ResetCoordsThread.LOCAL) {
                localFinished = true;
            } else {
                remoteFinished = true;
            }

            if (localFinished && (remoteFinished || !resetRemote)) {
                progressDialog.dismiss();
                final CacheDetailActivity activity = getActivity();
                if (activity != null) {
                    activity.notifyDataSetChanged();
                }
            }
        }

    }

    private class ResetCoordsThread extends Thread {

        private final Geocache cache;
        private final Handler handler;
        private final boolean local;
        private final boolean remote;
        private final Waypoint wpt;
        private final ProgressDialog progress;
        public static final int LOCAL = 0;
        public static final int ON_WEBSITE = 1;

        public ResetCoordsThread(Geocache cache, Handler handler, final Waypoint wpt, boolean local, boolean remote, final ProgressDialog progress) {
            this.cache = cache;
            this.handler = handler;
            this.local = local;
            this.remote = remote;
            this.wpt = wpt;
            this.progress = progress;
        }

        @Override
        public void run() {

            if (local) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.setMessage(res.getString(R.string.waypoint_reset_cache_coords));
                    }
                });
                cache.setCoords(wpt.getCoords());
                cache.setUserModifiedCoords(false);
                cache.deleteWaypointForce(wpt);
                cgData.saveChangedCache(cache);
                handler.sendEmptyMessage(LOCAL);
            }

            final IConnector con = ConnectorFactory.getConnector(cache);
            if (remote && con.supportsOwnCoordinates()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.setMessage(res.getString(R.string.waypoint_coordinates_being_reset_on_website));
                    }
                });

                final boolean result = con.deleteModifiedCoordinates(cache);

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if (result) {
                            showToast(getString(R.string.waypoint_coordinates_has_been_reset_on_website));
                        } else {
                            showToast(getString(R.string.waypoint_coordinates_upload_error));
                        }
                        handler.sendEmptyMessage(ON_WEBSITE);
                        notifyDataSetChanged();
                    }

                });

            }
        }
    }

    public class SimpleHandler extends CancellableHandler {
        public static final String SUCCESS_TEXT = "success_message";

        @Override
        public void handleRegularMessage(final Message msg) {
            if (msg.getData() != null && msg.getData().getString(SUCCESS_TEXT) != null) {
                showToast(msg.getData().getString(SUCCESS_TEXT));
            }
            progress.dismiss();
            return;
        }

        @Override
        public void handleCancel(final Object extra) {
            showToast((String) extra);
            progress.dismiss();
        }
    }

    private class UploadPersonalNoteThread extends Thread {
        private Geocache cache = null;
        private CancellableHandler handler = null;

        public UploadPersonalNoteThread(Geocache cache, CancellableHandler handler) {
            this.cache = cache;
            this.handler = handler;
        }

        @Override
        public void run() {
            IConnector con = ConnectorFactory.getConnector(cache);
            if (con.supportsPersonalNote()) {
                con.uploadPersonalNote(cache);
            }
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString(SimpleHandler.SUCCESS_TEXT, res.getString(R.string.cache_personal_note_upload_done));
            msg.setData(bundle);
            handler.sendMessage(msg);
        }
    }

    @Override
    protected String getTitle(Page page) {
        // show number of waypoints directly in waypoint title
        if (page == Page.WAYPOINTS) {
            final int waypointCount = cache.getWaypoints().size();
            return res.getQuantityString(R.plurals.waypoints, waypointCount, waypointCount);
        }
        return res.getString(page.titleStringId);
    }

    @Override
    protected Pair<List<? extends Page>, Integer> getOrderedPages() {
        final ArrayList<Page> pages = new ArrayList<Page>();
        pages.add(Page.WAYPOINTS);
        pages.add(Page.DETAILS);
        final int detailsIndex = pages.size() - 1;
        pages.add(Page.DESCRIPTION);
        if (!cache.getLogs().isEmpty()) {
            pages.add(Page.LOGS);
        }
        if (CollectionUtils.isNotEmpty(cache.getFriendsLogs())) {
            pages.add(Page.LOGSFRIENDS);
        }
        if (CollectionUtils.isNotEmpty(cache.getInventory())) {
            pages.add(Page.INVENTORY);
        }
        if (CollectionUtils.isNotEmpty(cache.getImages())) {
            pages.add(Page.IMAGES);
        }
        return new ImmutablePair<List<? extends Page>, Integer>(pages, detailsIndex);
    }

    @Override
    protected AbstractViewPagerActivity.PageViewCreator createViewCreator(Page page) {
        switch (page) {
            case DETAILS:
                return new DetailsViewCreator();

            case DESCRIPTION:
                return new DescriptionViewCreator();

            case LOGS:
                return new CacheLogsViewCreator(this, cache, true);

            case LOGSFRIENDS:
                return new CacheLogsViewCreator(this, cache, false);

            case WAYPOINTS:
                return new WaypointsViewCreator();

            case INVENTORY:
                return new InventoryViewCreator();

            case IMAGES:
                return new ImagesViewCreator();

            default:
                throw new IllegalArgumentException();
        }
    }

    static void updateOfflineBox(final View view, final Geocache cache, final Resources res,
            final OnClickListener refreshCacheClickListener,
            final OnClickListener dropCacheClickListener,
            final OnClickListener storeCacheClickListener) {
        // offline use
        final TextView offlineText = (TextView) view.findViewById(R.id.offline_text);
        final Button offlineRefresh = (Button) view.findViewById(R.id.offline_refresh);
        final Button offlineStore = (Button) view.findViewById(R.id.offline_store);

        if (cache.isOffline()) {
            final long diff = (System.currentTimeMillis() / (60 * 1000)) - (cache.getDetailedUpdate() / (60 * 1000)); // minutes

            String ago;
            if (diff < 15) {
                ago = res.getString(R.string.cache_offline_time_mins_few);
            } else if (diff < 50) {
                ago = res.getString(R.string.cache_offline_time_about) + " " + diff + " " + res.getString(R.string.cache_offline_time_mins);
            } else if (diff < 90) {
                ago = res.getString(R.string.cache_offline_time_about) + " " + res.getString(R.string.cache_offline_time_hour);
            } else if (diff < (48 * 60)) {
                ago = res.getString(R.string.cache_offline_time_about) + " " + (diff / 60) + " " + res.getString(R.string.cache_offline_time_hours);
            } else {
                ago = res.getString(R.string.cache_offline_time_about) + " " + (diff / (24 * 60)) + " " + res.getString(R.string.cache_offline_time_days);
            }

            offlineText.setText(res.getString(R.string.cache_offline_stored) + "\n" + ago);
            offlineRefresh.setOnClickListener(refreshCacheClickListener);

            offlineStore.setText(res.getString(R.string.cache_offline_drop));
            offlineStore.setClickable(true);
            offlineStore.setOnClickListener(dropCacheClickListener);
        } else {
            offlineText.setText(res.getString(R.string.cache_offline_not_ready));
            offlineRefresh.setOnClickListener(refreshCacheClickListener);

            offlineStore.setText(res.getString(R.string.cache_offline_store));
            offlineStore.setClickable(true);
            offlineStore.setOnClickListener(storeCacheClickListener);
        }
        offlineRefresh.setVisibility(cache.supportsRefresh() ? View.VISIBLE : View.GONE);
        offlineRefresh.setClickable(true);
    }

}
