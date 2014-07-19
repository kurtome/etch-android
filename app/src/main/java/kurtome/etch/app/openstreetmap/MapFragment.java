package kurtome.etch.app.openstreetmap;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.*;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import kurtome.etch.app.ObjectGraphUtils;
import kurtome.etch.app.location.LocationUpdatedEvent;
import org.metalev.multitouch.controller.MultiTouchController;
import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.ResourceProxyImpl;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MinimapOverlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import javax.inject.Inject;

public class MapFragment extends Fragment {

    public static final String PREFS_NAME = "org.andnav.osm.prefs";
    public static final String PREFS_TILE_SOURCE = "tilesource";
    public static final String PREFS_SCROLL_X = "scrollX";
    public static final String PREFS_SCROLL_Y = "scrollY";
    public static final String PREFS_ZOOM_LEVEL = "zoomLevel";
    public static final String PREFS_SHOW_LOCATION = "showLocation";
    public static final String PREFS_SHOW_COMPASS = "showCompass";

    private static final int DIALOG_ABOUT_ID = 1;

    private static final int MENU_SAMPLES = Menu.FIRST + 1;
    private static final int MENU_ABOUT = MENU_SAMPLES + 1;

    private static final int MENU_LAST_ID = MENU_ABOUT + 1; // Always set to last unused id

    private SharedPreferences mPrefs;
    private MapView mMapView;
//    private MyLocationNewOverlay mLocationOverlay;
    private CompassOverlay mCompassOverlay;
    private MinimapOverlay mMinimapOverlay;
    private ScaleBarOverlay mScaleBarOverlay;
    //    private RotationGestureOverlay mRotationGestureOverlay;
    private ResourceProxy mResourceProxy;
    private Location mLocation;

    @Inject Bus mEventBus;

    public static MapFragment newInstance() {
        MapFragment fragment = new MapFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ObjectGraphUtils.inject(this);
        mEventBus.register(this);

        mResourceProxy = new ResourceProxyImpl(inflater.getContext().getApplicationContext());
        mMapView = new MapView(inflater.getContext(), 256, mResourceProxy);
        // Call this method to turn off hardware acceleration at the View level.
        // setHardwareAccelerationOff();

        return mMapView;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setHardwareAccelerationOff() {
        // Turn off hardware acceleration here, or in manifest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mMapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Context context = this.getActivity();
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();
        // mResourceProxy = new ResourceProxyImpl(getActivity().getApplicationContext());

        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        this.mCompassOverlay = new CompassOverlay(
                context,
                new InternalCompassOrientationProvider(context),
                mMapView
        );
//        this.mLocationOverlay = new MyLocationNewOverlay(
//                context,
//                new GpsMyLocationProvider(context),
//                mMapView
//        );

        mMinimapOverlay = new MinimapOverlay(context, mMapView.getTileRequestCompleteHandler());
        mMinimapOverlay.setWidth(dm.widthPixels / 5);
        mMinimapOverlay.setHeight(dm.heightPixels / 5);

        mScaleBarOverlay = new ScaleBarOverlay(context);
        mScaleBarOverlay.setCentred(true);
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);

//        mRotationGestureOverlay = new RotationGestureOverlay(context, mMapView);
//		mRotationGestureOverlay.setEnabled(false);

        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
//        mMapView.getOverlays().add(this.mLocationOverlay);
        mMapView.getOverlays().add(this.mCompassOverlay);
        mMapView.getOverlays().add(this.mMinimapOverlay);
        mMapView.getOverlays().add(this.mScaleBarOverlay);
//        mMapView.getOverlays().add(this.mRotationGestureOverlay);

        mMapView.getController().setZoom(mPrefs.getInt(PREFS_ZOOM_LEVEL, 1));
        mMapView.scrollTo(mPrefs.getInt(PREFS_SCROLL_X, 0), mPrefs.getInt(PREFS_SCROLL_Y, 0));

//        mLocationOverlay.enableMyLocation();
        mCompassOverlay.enableCompass();

        setHasOptionsMenu(true);
    }

    @Override
    public void onPause() {
        final SharedPreferences.Editor edit = mPrefs.edit();
        edit.putString(PREFS_TILE_SOURCE, mMapView.getTileProvider().getTileSource().name());
        edit.putInt(PREFS_SCROLL_X, mMapView.getScrollX());
        edit.putInt(PREFS_SCROLL_Y, mMapView.getScrollY());
        edit.putInt(PREFS_ZOOM_LEVEL, mMapView.getZoomLevel());
//        edit.putBoolean(PREFS_SHOW_LOCATION, mLocationOverlay.isMyLocationEnabled());
        edit.putBoolean(PREFS_SHOW_COMPASS, mCompassOverlay.isCompassEnabled());
        edit.commit();

//        this.mLocationOverlay.disableMyLocation();
        this.mCompassOverlay.disableCompass();

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        final String tileSourceName = mPrefs.getString(PREFS_TILE_SOURCE,
                TileSourceFactory.DEFAULT_TILE_SOURCE.name());
        try {
            final ITileSource tileSource = TileSourceFactory.getTileSource(tileSourceName);
            mMapView.setTileSource(tileSource);
        }
        catch (final IllegalArgumentException e) {
            mMapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        }
        if (mPrefs.getBoolean(PREFS_SHOW_LOCATION, false)) {
//            this.mLocationOverlay.enableMyLocation();
        }
        if (mPrefs.getBoolean(PREFS_SHOW_COMPASS, false)) {
            this.mCompassOverlay.enableCompass();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Put overlay items first
        mMapView.getOverlayManager().onCreateOptionsMenu(menu, MENU_LAST_ID, mMapView);

//        // Put samples next
//		SubMenu samplesSubMenu = menu.addSubMenu(0, MENU_SAMPLES, Menu.NONE, R.string.samples)
//				.setIcon(android.R.drawable.ic_menu_gallery);
//		SampleFactory sampleFactory = SampleFactory.getInstance();
//		for (int a = 0; a < sampleFactory.count(); a++) {
//			final BaseSampleFragment f = sampleFactory.getSample(a);
//			samplesSubMenu.add(f.getSampleTitle()).setOnMenuItemClickListener(
//					new MenuItem.OnMenuItemClickListener() {
//						@Override
//						public boolean onMenuItemClick(MenuItem item) {
//							startSampleFragment(f);
//							return true;
//						}
//					});
//		}

//        // Put "About" menu item last
//        menu.add(0, MENU_ABOUT, Menu.CATEGORY_SECONDARY, R.string.about).setIcon(
//                android.R.drawable.ic_menu_info_details);

        super.onCreateOptionsMenu(menu, inflater);
    }

//	protected void startSampleFragment(Fragment fragment) {
//		FragmentManager fm = getFragmentManager();
//		fm.beginTransaction().hide(this).add(android.R.id.content, fragment, "SampleFragment")
//				.addToBackStack(null).commit();
//	}

    @Override
    public void onPrepareOptionsMenu(final Menu pMenu) {
        mMapView.getOverlayManager().onPrepareOptionsMenu(pMenu, MENU_LAST_ID, mMapView);
        super.onPrepareOptionsMenu(pMenu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mMapView.getOverlayManager().onOptionsItemSelected(item, MENU_LAST_ID, mMapView))
            return true;

        switch (item.getItemId()) {
            case MENU_ABOUT:
                getActivity().showDialog(DIALOG_ABOUT_ID);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Subscribe
    public void updateLocation(final LocationUpdatedEvent event) {
        mLocation = event.getLocation();

        setMaxZoom();
        centerOnLocation();
    }

    private void centerOnLocation() {
        mMapView.getController().setCenter(new GeoPoint(mLocation.getLatitude(), mLocation.getLongitude()));
    }

    private void setMaxZoom() {
        // http://wiki.openstreetmap.org/wiki/Zoom_levels
        int zoomLevel = 19;
        mMapView.getController().setZoom(zoomLevel);
    }


    // @Override
    // public boolean onTrackballEvent(final MotionEvent event) {
    // return this.mMapView.onTrackballEvent(event);
    // }

}