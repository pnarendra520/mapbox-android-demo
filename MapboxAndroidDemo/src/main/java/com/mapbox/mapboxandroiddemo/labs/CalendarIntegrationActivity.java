package com.mapbox.mapboxandroiddemo.labs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.api.geocoding.v5.GeocodingCriteria;
import com.mapbox.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.core.exceptions.ServicesException;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxandroiddemo.R;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize;

/**
 * Use the Android system's Content Provider retrieve information about a user's upcoming calendar events.
 * Then use the event location eventTitle with Mapbox geocoding to show the event's location on the map.
 */
public class CalendarIntegrationActivity extends AppCompatActivity implements
  OnMapReadyCallback, PermissionsListener {

  private static final int MY_CAL_REQ = 0;
  private String tag = "CalendarIntegrationActivity";
  private MapView mapView;
  public MapboxMap mapboxMap;
  private RecyclerView recyclerView;
  private CalendarEventRecyclerViewAdapter locationAdapter;
  private List<SingleCalendarEvent> listOfCalendarEvents;
  private List<Feature> featureList;
  private FeatureCollection featureCollection;

  private static final int TITLE_INDEX = 1;
  private static final int EVENT_LOCATION_INDEX = 2;
  private static final int DTSTART_INDEX = 3;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Mapbox access token is configured here. This needs to be called either in your application
    // object or in the same activity which contains the mapview.
    Mapbox.getInstance(this, getString(R.string.access_token));

    // This contains the MapView in XML and needs to be called after the access token is configured.
    setContentView(R.layout.activity_calendar_content_provider);

    recyclerView = findViewById(R.id.calendar_rv_on_top_of_map);

    mapView = findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);
  }

  @Override
  public void onMapReady(MapboxMap mapboxMap) {
    CalendarIntegrationActivity.this.mapboxMap = mapboxMap;

    initEventIconSymbolLayer();
    printDataFromEventTable();
  }

  // Add the mapView lifecycle to the activity's lifecycle methods
  @Override
  public void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
  }

  @Override
  public void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  @Override
  public void onExplanationNeeded(List<String> permissionsToExplain) {

  }

  @Override
  public void onPermissionResult(boolean granted) {

  }

  private void initEventIconSymbolLayer() {
    Bitmap icon = BitmapFactory.decodeResource(
      this.getResources(), R.drawable.ic_event);
    mapboxMap.addImage("icon-id", icon);
    featureCollection = FeatureCollection.fromFeatures(new Feature[] {});
    GeoJsonSource geoJsonSource = new GeoJsonSource("source", featureCollection);
    mapboxMap.addSource(geoJsonSource);
    SymbolLayer eventSymbolLayer = new SymbolLayer("symbolLayer", "source");
    eventSymbolLayer.withProperties(
      iconImage("icon-id"),
      iconSize(1.8f),
      iconAllowOverlap(true),
      iconIgnorePlacement(true)
    );
    mapboxMap.addLayer(eventSymbolLayer);
  }

  /**
   * POJO model class for a single location in the recyclerview
   */
  class SingleCalendarEvent {

    private String eventTitle;
    private String eventLocation;
    private LatLng locationCoordinates;

    public SingleCalendarEvent() {

    }

    public SingleCalendarEvent(String eventTitle, String eventDescription, String eventLocation) {
      this.eventTitle = eventTitle;
      this.eventLocation = eventLocation;
    }

    public String getEventTitle() {
      return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
      this.eventTitle = eventTitle;
    }

    public String getEventLocation() {
      return eventLocation;
    }

    public void setEventLocation(String eventLocation) {
      this.eventLocation = eventLocation;
    }

    public LatLng getLocationCoordinates() {
      return locationCoordinates;
    }

    public void setLocationCoordinates(LatLng locationCoordinates) {
      this.locationCoordinates = locationCoordinates;
    }
  }

  static class CalendarEventRecyclerViewAdapter extends
    RecyclerView.Adapter<CalendarEventRecyclerViewAdapter.MyViewHolder> {

    private List<SingleCalendarEvent> locationList;
    private MapboxMap map;

    public CalendarEventRecyclerViewAdapter(List<SingleCalendarEvent> locationList, MapboxMap mapBoxMap) {
      this.locationList = locationList;
      this.map = mapBoxMap;
    }

    @Override
    public CalendarEventRecyclerViewAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View itemView = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.calendar_rv_card, parent, false);
      return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(CalendarEventRecyclerViewAdapter.MyViewHolder holder, int position) {
      SingleCalendarEvent singleCalendarEvent = locationList.get(position);
      holder.title.setText(singleCalendarEvent.getEventTitle());
      holder.setClickListener(new ItemClickListener() {
        @Override
        public void onClick(View view, int position) {
          LatLng selectedLocationLatLng = locationList.get(position).getLocationCoordinates();
          CameraPosition newCameraPosition = new CameraPosition.Builder()
            .target(selectedLocationLatLng)
            .build();

          map.easeCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition));
        }
      });
    }


    @Override
    public int getItemCount() {
      return locationList.size();
    }

    static class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
      TextView title;
      CardView singleCard;
      ItemClickListener clickListener;

      MyViewHolder(View view) {
        super(view);
        title = view.findViewById(R.id.calendar_event_title);
        singleCard = view.findViewById(R.id.single_calendar_event_cardview);
        singleCard.setOnClickListener(this);
      }

      public void setClickListener(ItemClickListener itemClickListener) {
        this.clickListener = itemClickListener;
      }

      @Override
      public void onClick(View view) {
        clickListener.onClick(view, getLayoutPosition());
      }
    }
  }

  public interface ItemClickListener {
    void onClick(View view, int position);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    switch (requestCode) {
      case MY_CAL_REQ: {
        if (grantResults.length > 0
          && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.d(tag, "onRequestPermissionsResult: calendar granted");
          printDataFromEventTable();
        } else {
          Toast.makeText(this, R.string.user_calendar_permission_explanation, Toast.LENGTH_LONG).show();
        }
        return;
      }
      default:
        return;
    }
  }

  public void printDataFromEventTable() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
      != PackageManager.PERMISSION_GRANTED) {

      ActivityCompat.requestPermissions(this, new String[]
        {Manifest.permission.READ_CALENDAR}, MY_CAL_REQ);
    } else {
      Uri calendarUri;

      if (Integer.parseInt(Build.VERSION.SDK) >= 8 || Integer.parseInt(Build.VERSION.SDK) <= 13) {
        calendarUri = Uri.parse("content://com.android.calendar/events");
      } else if (Integer.parseInt(Build.VERSION.SDK) >= 14) {
        calendarUri = CalendarContract.Events.CONTENT_URI;
      } else {
        calendarUri = Uri.parse("content://calendar/events");
      }

      Calendar startTime = Calendar.getInstance();
      startTime.set(2018, 2, 1, 0, 0);

      Calendar endTime = Calendar.getInstance();
      endTime.set(2018, 6, 1, 0, 0);

      String selection = "(( " + CalendarContract.Events.DTSTART + " >= "
        + startTime.getTimeInMillis() + " )" + " AND ( " + CalendarContract.Events.DTSTART
        + " <= " + endTime.getTimeInMillis() + " ))";

      String[] projection = new String[] {CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART};

      Cursor cur = null;

      cur = this.getContentResolver().query(calendarUri, projection, selection, null, null);

      listOfCalendarEvents = new ArrayList<>();
      featureList = new ArrayList<>();
      int index = 0;
      if (cur != null) {
        while (cur.moveToNext()) {
          if (index <= 80) {
            String location = null;
            String title = null;

            title = cur.getString(TITLE_INDEX);
            location = cur.getString(EVENT_LOCATION_INDEX);

            if (!location.isEmpty()) {
              Log.d(tag, "printDataFromEventTable: title = " + title + " AND location = " + location);

              SingleCalendarEvent singleCalendarEvent = new SingleCalendarEvent();
              singleCalendarEvent.setEventTitle(title);
              makeMapboxGeocodingRequest(title, location, singleCalendarEvent);
            }
            index++;
          }
        }
        initRecyclerView();
      }
    }
  }

  private void makeMapboxGeocodingRequest(String eventTitle,
                                          String eventLocation, SingleCalendarEvent singleCalendarEvent) {
    try {
      // Build a Mapbox geocoding request
      MapboxGeocoding client = MapboxGeocoding.builder()
        .accessToken(getString(R.string.access_token))
        .query(eventLocation)
        .geocodingTypes(GeocodingCriteria.TYPE_ADDRESS)
        .mode(GeocodingCriteria.MODE_PLACES)
        .build();
      client.enqueueCall(new Callback<GeocodingResponse>() {
        @Override
        public void onResponse(Call<GeocodingResponse> call,
                               Response<GeocodingResponse> response) {
          List<CarmenFeature> results = response.body().features();
          if (results.size() > 0) {
            // Get the first Feature from the successful geocoding response
            CarmenFeature feature = results.get(0);
            if (feature != null) {

              Log.d(tag, "onResponse: feature = " + feature);

              LatLng featureLatLng = new LatLng(feature.center().latitude(), feature.center().longitude());

              if (featureLatLng != null) {
                singleCalendarEvent.setLocationCoordinates(featureLatLng);
                if (listOfCalendarEvents != null) {
                  listOfCalendarEvents.add(singleCalendarEvent);

                  Feature singleFeature = Feature.fromGeometry(Point.fromLngLat(featureLatLng.getLongitude(),
                    featureLatLng.getLatitude()));
                  singleFeature.addStringProperty("title", eventTitle);
                  featureList.add(singleFeature);

                  featureCollection = FeatureCollection.fromFeatures(featureList);
                  GeoJsonSource source = mapboxMap.getSourceAs("source");
                  if (source != null) {
                    source.setGeoJson(featureCollection);
                  }
                } else {
                  Log.d(tag, "onResponse: listOfCalendarEvents == null");
                }

              }
            }

          } else {
            Toast.makeText(CalendarIntegrationActivity.this, R.string.no_results,
              Toast.LENGTH_SHORT).show();
          }
        }

        @Override
        public void onFailure(Call<GeocodingResponse> call, Throwable throwable) {
          Timber.e("Geocoding Failure: " + throwable.getMessage());
        }
      });
    } catch (ServicesException servicesException) {
      Timber.e("Error geocoding: " + servicesException.toString());
      servicesException.printStackTrace();
    }
  }

  private void initRecyclerView() {
    // Set up the recyclerView
    if (listOfCalendarEvents.size() > 0) {
      Log.d(tag, "initRecyclerView: listOfCalendarEvents.size() > 0");
      locationAdapter = new CalendarEventRecyclerViewAdapter(listOfCalendarEvents, mapboxMap);
      recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext(),
        LinearLayoutManager.HORIZONTAL, true));
      recyclerView.setItemAnimator(new DefaultItemAnimator());
      recyclerView.setAdapter(locationAdapter);
      SnapHelper snapHelper = new LinearSnapHelper();
      snapHelper.attachToRecyclerView(recyclerView);
    }
  }
}