package de.stephanlindauer.criticalmaps.fragments;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import butterknife.BindDrawable;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import com.squareup.otto.Subscribe;
import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.R;
import de.stephanlindauer.criticalmaps.events.NewLocationEvent;
import de.stephanlindauer.criticalmaps.events.NewServerResponseEvent;
import de.stephanlindauer.criticalmaps.model.OtherUsersLocationModel;
import de.stephanlindauer.criticalmaps.model.OwnLocationModel;
import de.stephanlindauer.criticalmaps.overlays.LocationMarker;
import de.stephanlindauer.criticalmaps.provider.EventBusProvider;
import de.stephanlindauer.criticalmaps.utils.MapViewUtils;
import javax.inject.Inject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MapFragment extends Fragment {

    // constants
    private final static String KEY_MAP_ZOOMLEVEL = "map_zoomlevel";
    private final static String KEY_MAP_POSITION = "map_position";
    private final static String KEY_INITIAL_LOCATION_SET = "initial_location_set";

    //dependencies
    @Inject
    OwnLocationModel ownLocationModel;

    @Inject
    OtherUsersLocationModel otherUsersLocationModel;

    @Inject
    EventBusProvider eventService;

    //view
    private MapView mapView;

    @BindView(R.id.set_current_location_center)
    ImageButton setCurrentLocationCenter;

    @BindView(R.id.map_container)
    RelativeLayout mapContainer;

    @BindView(R.id.searching_for_location_overlay_map)
    RelativeLayout searchingForLocationOverlay;

    @BindView(R.id.map_osm_notice)
    TextView osmNoticeOverlay;

    //misc
    private boolean isInitialLocationSet = false;

    //cache drawables
    @BindDrawable(R.drawable.map_marker)
    Drawable locationIcon;

    @BindDrawable(R.drawable.map_marker_own)
    Drawable ownLocationIcon;
    private Unbinder unbinder;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_map, container, false);
        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(final Bundle savedState) {
        super.onActivityCreated(savedState);

        App.components().inject(this);

        osmNoticeOverlay.setMovementMethod(LinkMovementMethod.getInstance());

        mapView = MapViewUtils.createMapView(getActivity());
        mapContainer.addView(mapView);

        setCurrentLocationCenter.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ownLocationModel.ownLocation != null)
                    animateToLocation(ownLocationModel.ownLocation);
            }
        });

        if (savedState != null) {
            Integer zoomLevel = (Integer) savedState.get(KEY_MAP_ZOOMLEVEL);
            GeoPoint position = savedState.getParcelable(KEY_MAP_POSITION);

            if (zoomLevel != null && position != null) {
                mapView.getController().setZoom(zoomLevel);
                setToLocation(position);
            }

            isInitialLocationSet = savedState.getBoolean(KEY_INITIAL_LOCATION_SET, false);
        }
    }

    private void refreshView() {
        mapView.getOverlays().clear();

        for (GeoPoint currentOtherUsersLocation : otherUsersLocationModel.getOtherUsersLocations()) {
            LocationMarker otherPeoplesMarker = new LocationMarker(mapView);
            otherPeoplesMarker.setPosition(currentOtherUsersLocation);
            otherPeoplesMarker.setIcon(locationIcon);
            mapView.getOverlays().add(otherPeoplesMarker);
        }

        if (ownLocationModel.ownLocation != null) {
            GeoPoint currentUserLocation = ownLocationModel.ownLocation;
            LocationMarker ownMarker = new LocationMarker(mapView);
            ownMarker.setPosition(currentUserLocation);
            ownMarker.setIcon(ownLocationIcon);
            mapView.getOverlays().add(ownMarker);
        }

        mapView.invalidate();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (ownLocationModel.ownLocation != null) {
            if (!isInitialLocationSet) {
                handleFirstLocationUpdate();
            } else {
                searchingForLocationOverlay.setVisibility(View.GONE);
            }
        }

        eventService.register(this);

        refreshView();
    }

    private void handleFirstLocationUpdate() {
        searchingForLocationOverlay.setVisibility(View.GONE);
        animateToLocation(ownLocationModel.ownLocation);
        isInitialLocationSet = true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_MAP_ZOOMLEVEL, mapView.getZoomLevel());
        outState.putParcelable(KEY_MAP_POSITION, (GeoPoint) mapView.getMapCenter());
        outState.putBoolean(KEY_INITIAL_LOCATION_SET, isInitialLocationSet);
    }

    @Override
    public void onPause() {
        super.onPause();
        eventService.unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mapView = null;
        unbinder.unbind();
    }

    @Subscribe
    public void handleNewServerData(NewServerResponseEvent e) {
        refreshView();
    }

    @Subscribe
    public void handleNewLocation(NewLocationEvent e) {
        // if this is the first location update handle it accordingly
        if (!isInitialLocationSet) {
            handleFirstLocationUpdate();
        }

        refreshView();
    }

    private void animateToLocation(final GeoPoint location) {
        mapView.getController().animateTo(location);
    }

    private void setToLocation(final GeoPoint location) {
        mapView.getController().setCenter(location);
    }
}
