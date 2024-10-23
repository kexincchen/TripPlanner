package com.example.tripplanner.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.codebyashish.googledirectionapi.AbstractRouting;
import com.codebyashish.googledirectionapi.ErrorHandling;
import com.codebyashish.googledirectionapi.RouteDrawing;
import com.codebyashish.googledirectionapi.RouteInfoModel;
import com.codebyashish.googledirectionapi.RouteListener;
import com.example.tripplanner.MapActivity;

import com.example.tripplanner.adapter.DistanceMatrixCallback;
import com.example.tripplanner.adapter.RecommentActivityAdapter;

import com.example.tripplanner.adapter.WeatherAdapter;
import com.example.tripplanner.db.FirestoreDB;
import com.example.tripplanner.entity.ActivityItem;
import com.example.tripplanner.R;
import com.example.tripplanner.adapter.ActivityItemAdapter;
import com.example.tripplanner.entity.DistanceMatrixEntry;
import com.example.tripplanner.entity.Location;

import com.example.tripplanner.entity.User;

import com.example.tripplanner.entity.PlanItem;
import com.example.tripplanner.entity.RouteInfo;

import com.example.tripplanner.utils.GptApiClient;
import com.example.tripplanner.utils.PlacesClientProvider;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.utils.RoutePlanner;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import com.example.tripplanner.adapter.AutocompleteAdapter;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.libraries.places.api.model.AddressComponent;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import com.example.tripplanner.entity.Weather;
import com.example.tripplanner.utils.WeatherAPIClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class PlanFragment extends Fragment
        implements OnMapReadyCallback, ActivityItemAdapter.OnStartDragListener, RouteListener {

    public static final int OVERVIEW = 0;
    public static final int PLAN_SPECIFIC_DAY = 1;
    static final String LAYOUT_TYPE = "type";
    private int layout = OVERVIEW;
    private static Timestamp startDate;
    private int dayIndex = -1;
    private GoogleMap mMap;

    public static List<Location> locationList;
    public static Timestamp endDate;
    private String startDay;
    private int lastingDays;

    private PlacesClient placesClient;
    private AutocompleteAdapter autocompleteAdapter;

    private PlanViewModel viewModel;
    public static Trip trip;
    public static User user;

    // For specific day plan
    private FloatingActionButton addActivityLocation;
    private RecyclerView activityLocationRecyclerView;
    private ArrayList<ActivityItem> activityItemArray;
    private List<PlanItem> planItems;
    private ActivityItemAdapter adapter;
    private AtomicReference<Location> activityLocation = new AtomicReference<>();

    private ItemTouchHelper itemTouchHelper;

    // For weather forecast
    private ArrayList<Map<Integer, Weather>> allWeatherData = new ArrayList<>();
    private WeatherAdapter weatherAdapter;
    private WeatherAPIClient weatherAPIClient;
    private List<Polyline> polylines = new ArrayList<>();
    private Random random = new Random(123);

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnPlaceFetchedListener {
        void onPlaceFetched(Location location);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        placesClient = PlacesClientProvider.getPlacesClient();

        // Get ViewModel instance
        viewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);

        // Observe tripLiveData
        viewModel.getTripLiveData().observe(this, new Observer<Trip>() {
            @Override
            public void onChanged(Trip trip) {
                if (trip != null) {
                    PlanFragment.trip = trip;
                    PlanFragment.locationList = trip.getLocations();
                    PlanFragment.startDate = trip.getStartDate();
                    PlanFragment.endDate = trip.getEndDate();
                }
            }
        });
        if (this.getArguments() != null) {
            this.layout = getArguments().getInt(LAYOUT_TYPE, OVERVIEW);
            if (this.layout == PLAN_SPECIFIC_DAY) {
                this.dayIndex = getArguments().getInt("dayIndex", -1);
                long startDateMillis = getArguments().getLong("startDateMillis");
                startDate = new Timestamp(new Date(startDateMillis));
            }
        }
    }

    public static PlanFragment newInstance(int layoutType, Timestamp startDate, int dayIndex) {
        PlanFragment fragment = new PlanFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(LAYOUT_TYPE, layoutType);
        bundle.putInt("dayIndex", dayIndex);
        bundle.putLong("startDateMillis", startDate.toDate().getTime());
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView;
        if (this.layout == PLAN_SPECIFIC_DAY) {
            rootView = inflater.inflate(R.layout.plan_specific_day, container, false);

            if (endDate != null && endDate.compareTo(Timestamp.now()) < 0) {
                ImageButton planSuggest = rootView.findViewById(R.id.planSuggest);
                planSuggest.setVisibility(View.GONE);
            }

            addActivityLocation = rootView.findViewById(R.id.addActivityLocation);
            activityLocationRecyclerView = rootView.findViewById(R.id.activityLocationRecyclerView);

            // Get the activity items list for this day from the ViewModel
            activityItemArray = viewModel.getActivityItemArray(dayIndex);
            planItems = new ArrayList<>();
            preparePlanItems();
            adapter = new ActivityItemAdapter(getContext(), planItems);

            ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {

                @Override
                public boolean isLongPressDragEnabled() {
                    return false;
                }

                @Override
                public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder) {
                    int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                    int swipeFlags = 0;
                    return makeMovementFlags(dragFlags, swipeFlags);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder viewHolder,
                                      @NonNull RecyclerView.ViewHolder target) {
                    int fromPosition = viewHolder.getAdapterPosition();
                    int toPosition = target.getAdapterPosition();

                    PlanItem fromItem = planItems.get(fromPosition);
                    if (fromItem.getType() == PlanItem.TYPE_ACTIVITY) {
                        int fromActivityIndex = getActivityItemIndex(fromPosition);
                        int toActivityIndex = getActivityItemIndexForMove(toPosition);

                        if (toActivityIndex == -1) {
                            toActivityIndex = 0;
                        }
                        ActivityItem movedActivityItem = activityItemArray.remove(fromActivityIndex);
                        activityItemArray.add(toActivityIndex, movedActivityItem);
                        PlanItem movedPlanItem = planItems.remove(fromPosition);
                        planItems.add(toPosition, movedPlanItem);
                        adapter.notifyItemMoved(fromPosition, toPosition);
                        return true;
                    }
                    return false;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }

                @Override
                public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    preparePlanItems();
                    adapter.notifyDataSetChanged();
                    viewModel.updateActivityList(dayIndex, activityItemArray);
                    viewModel.saveTripToDatabase();
                }

            };

            itemTouchHelper = new ItemTouchHelper(callback);
            itemTouchHelper.attachToRecyclerView(activityLocationRecyclerView);

            adapter.setOnStartDragListener(this);

            activityLocationRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            activityLocationRecyclerView.setAdapter(adapter);

            adapter.setOnItemClickListener(new ActivityItemAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(int position, PlanItem planItem) {
                    if (planItem.getType() == PlanItem.TYPE_ACTIVITY) {
                        int activityIndex = getActivityItemIndex(position);
                        showEditActivityDialog(activityIndex);
                    }
                }
            });

            addActivityLocation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showAddActivityDialog();
                }
            });

            // Find the planSuggest button
            ImageButton planSuggestButton = rootView.findViewById(R.id.planSuggest);

            // Set an OnClickListener for the planSuggest button
            planSuggestButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {

                    String destination = "Unknown Destination";
                    if (locationList != null && !locationList.isEmpty()) {
                        destination = locationList.get(0).getName();
                    }

                    Log.d("PlanFragment", "Destination: " + destination);

                    // Get the weather forecast for the destination
                    final String[] weatherForecast = {"Sunny with a high of 25°C"};
                    // if (allWeatherData != null && !allWeatherData.isEmpty()) {
                    // // Get the weather for the first day (assuming day index 0)
                    // Weather weather = allWeatherData.get(0);
                    // if (weather != null) {
                    // weatherForecast = String.format("Weather is %s with a high of %.1f°C",
                    // weather.getDescription(), weather.getTemperature());
                    // }
                    // }

                    // Fetch user preference
                    AtomicReference<String> userPreferences = new AtomicReference<>("Enjoys coffee shops and outdoor activities");
                    if (user == null) {
                        FirestoreDB.getInstance().getUserById(FirestoreDB.getCurrentUserId(), returnedUser -> {
                            user = returnedUser;
                            userPreferences.set(user.getPreference());
                            Log.d("PlanFragment", "Get user: "+ user);
                        }, e -> {
                            Log.d("PlanFragment", "Error in fetching user: " + e);
                        });
                    } else {
                        userPreferences.set(user.getPreference());
                    }


                    fetchWeatherDataForDate(startDate, dayIndex, new WeatherDataCallback() {
                        @Override
                        public void onSuccess(Map<Integer, Weather> weatherData) {
                            // Handle the successful retrieval of weather data
                            Log.d("PlanFragment", "Weather data retrieved: " + weatherData);
                            Weather weather = weatherData.get(dayIndex);
                            weatherForecast[0] = String.format("Weather is %s with max %.1f°C and min %.1f°C",
                                    weather.getDescription(), weather.getMaxTemp(), weather.getMinTemp());
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            Log.d("PlanFragment", "Failed to fetch weather");
                        }


                    });

                    GptApiClient.recommendTripPlan(destination, weatherForecast[0], userPreferences.get(), trip, new GptApiClient.GptApiCallback() {
                        @Override
                        public void onSuccess(String response) {
                            // Handle the successful response here
                            Log.d("PlanFragment", "Trip plan recommended: " + response);

                            // Parse the JSON response into a list of ActivityItem objects
                            GptApiClient.parseActivityItemsFromJson(response, placesClient, new GptApiClient.OnActivityItemsParsedListener() {
                                @Override
                                public void onActivityItemsParsed(List<ActivityItem> recommendedActivities) {
                                    Log.d("PlanFragment", "RecommendActivities: " + recommendedActivities);

//                                    // Update the activityItemArray with the new recommended activities
//                                    activityItemArray.clear();
//                                    activityItemArray.addAll(recommendedActivities);


                                    // Show a popup window to let users select which activities to add to plan
                                    ListView listView = new ListView(getContext());
                                    RecommentActivityAdapter recommentActivityAdapter = new RecommentActivityAdapter(getContext(), recommendedActivities);
                                    listView.setAdapter(recommentActivityAdapter);

                                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                    builder.setTitle("Recommendations");
                                    builder.setView(listView);

                                    builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            // Get only the selected items
                                            List<ActivityItem> selectedItems = recommentActivityAdapter.getSelectedItems();
                                            // Handle the selected items here
                                            for (ActivityItem selectedItem : selectedItems) {
                                                Log.d("Selected Activity", selectedItem.toString());
                                            }

                                            // Update in ViewModel and save
                                            for (ActivityItem activityItem : selectedItems) {
                                                viewModel.addActivity(dayIndex, activityItem);
                                            }

                                            viewModel.saveTripToDatabase();

                                            preparePlanItems();
                                            // Notify the adapter that the data has changed
                                            adapter.notifyDataSetChanged();
                                            viewModel.updateActivityList(dayIndex, activityItemArray);
                                        }
                                    });
                                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            dialogInterface.dismiss();
                                        }
                                    });

                                    builder.create().show();
                                }
                            });

                        }
                        @Override
                        public void onFailure(String errorMessage) {
                            // Handle the failure to retrieve weather data
                            Log.e("PlanFragment", "Error fetching weather data: " + errorMessage);
                        }
                    });

                }
            });



        } else {
            rootView = inflater.inflate(R.layout.plan_overview, container, false);
            weatherAPIClient = new WeatherAPIClient();
            fetchAndDisplayWeatherData(rootView);
            showTripNote(rootView);

        }

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        return rootView;
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(viewHolder);
        } else {
            Log.e("PlanFragment", "itemTouchHelper is null");
        }
    }

    private void showAddActivityDialog() {
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_activity);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        dialog.getWindow().setAttributes(lp);

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextInputEditText activityNameEditText = dialog.findViewById(R.id.activityNameEditText);
        MaterialButton addButton = dialog.findViewById(R.id.addButton);
        MaterialButton cancelButton = dialog.findViewById(R.id.cancelButton);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String activityName = activityNameEditText.getText().toString().trim();
                if (!activityName.isEmpty()) {
                    ActivityItem activityItem = new ActivityItem(activityName);
                    // Update in ViewModel and save
                    viewModel.addActivity(dayIndex, activityItem);
                    viewModel.saveTripToDatabase();
                    preparePlanItems();
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                    showEditActivityDialog(activityItemArray.size() - 1);
                } else {
                    activityNameEditText.setError("Please enter activity name");
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void showEditActivityDialog(int position) {
        ActivityItem activityItem = activityItemArray.get(position);

        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_activity);

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        dialog.getWindow().setAttributes(lp);

        TextInputEditText activityName = dialog.findViewById(R.id.activityName);
        TextInputEditText startTime = dialog.findViewById(R.id.startTime);
        TextInputEditText endTime = dialog.findViewById(R.id.endTime);
        TextInputEditText inputLocation = dialog.findViewById(R.id.inputLocation);
        TextInputEditText inputNotes = dialog.findViewById(R.id.inputNotes);
        ListView autocompleteListView = dialog.findViewById(R.id.autocompleteListView);
        Button saveButton = dialog.findViewById(R.id.saveButton);
        Button cancelButton = dialog.findViewById(R.id.cancelButton);
        Button deleteButton = dialog.findViewById(R.id.deleteButton);

        autocompleteAdapter = new AutocompleteAdapter(getContext(), new ArrayList<>());
        autocompleteListView.setAdapter(autocompleteAdapter);

        final int[] startHour = new int[1];
        final int[] startMinute = new int[1];
        final int[] endHour = new int[1];
        final int[] endMinute = new int[1];
        final boolean[] isStartTimeSelected = { false };
        final boolean[] isEndTimeSelected = { false };

        activityLocation = new AtomicReference<>();

        activityName.setText(activityItem.getName());
        if (activityItem.getLocation() != null) {
            activityLocation.set(activityItem.getLocation());
            inputLocation.setText(activityItem.getLocationString());
        }

        if (activityItem.getStartTime() != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(activityItem.getStartTime().toDate());
            startHour[0] = calendar.get(Calendar.HOUR_OF_DAY);
            startMinute[0] = calendar.get(Calendar.MINUTE);
            startTime.setText(String.format("%02d:%02d", startHour[0], startMinute[0]));
        }

        if (activityItem.getEndTime() != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(activityItem.getEndTime().toDate());
            endHour[0] = calendar.get(Calendar.HOUR_OF_DAY);
            endMinute[0] = calendar.get(Calendar.MINUTE);
            endTime.setText(String.format("%02d:%02d", endHour[0], endMinute[0]));
        }
        inputNotes.setText(activityItem.getNotes());

        startTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hour = startHour[0] != 0 ? startHour[0] : Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                int minute = startMinute[0] != 0 ? startMinute[0] : Calendar.getInstance().get(Calendar.MINUTE);

                TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                startHour[0] = hourOfDay;
                                startMinute[0] = minute;
                                startTime.setText(String.format("%02d:%02d", hourOfDay, minute));
                                isStartTimeSelected[0] = true;
                            }
                        }, hour, minute, true);

                timePickerDialog.show();
            }
        });

        endTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hour = endHour[0] != 0 ? endHour[0] : Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                int minute = endMinute[0] != 0 ? endMinute[0] : Calendar.getInstance().get(Calendar.MINUTE);

                TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                endHour[0] = hourOfDay;
                                endMinute[0] = minute;
                                endTime.setText(String.format("%02d:%02d", hourOfDay, minute));
                                isEndTimeSelected[0] = true;
                            }
                        }, hour, minute, true);

                timePickerDialog.show();
            }
        });

        inputLocation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().isEmpty()) {
                    PlacesClientProvider.performAutocomplete(s.toString(), placesClient, autocompleteAdapter);
                    autocompleteListView.setVisibility(View.VISIBLE);
                } else {
                    autocompleteAdapter.clear();
                    autocompleteAdapter.notifyDataSetChanged();
                    autocompleteListView.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        autocompleteListView.setOnItemClickListener((parent, view1, pos, id) -> {
            AutocompletePrediction item = autocompleteAdapter.getItem(pos);
            String placeId = item.getPlaceId();
            fetchPlaceFromPlaceId(placeId, inputLocation, autocompleteListView, new OnPlaceFetchedListener() {
                @Override
                public void onPlaceFetched(Location location) {
                    // Set the activityLocation to the fetched location
                    activityLocation.set(location);
                }
            });
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newActivityName = activityName.getText().toString().trim();
                activityItem.setName(newActivityName);
                if (isStartTimeSelected[0]) {
                    Timestamp startTimestamp = buildTimestamp(startDate, dayIndex, startHour[0], startMinute[0]);
                    activityItem.setStartTime(startTimestamp);
                }
                if (isEndTimeSelected[0]) {
                    Timestamp endTimestamp = buildTimestamp(startDate, dayIndex, endHour[0], endMinute[0]);
                    activityItem.setEndTime(endTimestamp);
                }
                String locationText = inputLocation.getText().toString().trim();
                if (!locationText.isEmpty()) {
                    if (activityLocation.get() != null) {
                        activityItem.setLocation(activityLocation.get());
                    } else {
                        activityItem.setLocation(new Location("", locationText, "", 0, 0, ""));
                    }
                } else {
                    activityItem.setLocation(null);
                }
                activityItem.setNotes(inputNotes.getText().toString());
                preparePlanItems();
                adapter.notifyDataSetChanged();

                // Update in ViewModel and save
                viewModel.updateActivity(dayIndex, position, activityItem);
                viewModel.saveTripToDatabase();

                dialog.dismiss();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show a confirmation dialog
                new AlertDialog.Builder(getContext())
                        .setTitle("Delete Activity")
                        .setMessage("Are you sure you want to delete this activity?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface confirmDialog, int whichButton) {
                                // Update in ViewModel and save
                                viewModel.removeActivity(dayIndex, position);
                                viewModel.saveTripToDatabase();
                                preparePlanItems();
                                adapter.notifyDataSetChanged();

                                confirmDialog.dismiss();
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        dialog.show();
    }

    private void fetchPlaceFromPlaceId(String placeId, EditText inputLocation, ListView autocompleteListView,
            OnPlaceFetchedListener listener) {
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS_COMPONENTS,
                Place.Field.TYPES,
                Place.Field.LAT_LNG);

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, placeFields)
                .build();

        placesClient.fetchPlace(request).addOnSuccessListener((response) -> {
            Place place = response.getPlace();
            String country = null;
            if (place.getAddressComponents() != null) {
                for (AddressComponent component : place.getAddressComponents().asList()) {
                    if (component.getTypes().contains("country")) {
                        country = component.getName();
                        break;
                    }
                }
            }
            if (place != null) {
                Location loc = new Location(
                        place.getId(),
                        place.getName(),
                        place.getPlaceTypes().get(0),
                        place.getLatLng().latitude,
                        place.getLatLng().longitude,
                        country);
                inputLocation.setText(place.getName());
                autocompleteListView.setVisibility(View.GONE);
                listener.onPlaceFetched(loc);
            }
        }).addOnFailureListener((exception) -> {
            if (exception instanceof ApiException) {
                ApiException apiException = (ApiException) exception;
                int statusCode = apiException.getStatusCode();
                Toast.makeText(getContext(), "Place not found: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchAndDisplayWeatherData(View rootView) {
        // bind the adapter
        RecyclerView recyclerView = rootView.findViewById(R.id.weatherRecyclerView);
        recyclerView.setLayoutManager(
                new LinearLayoutManager(rootView.getContext(), LinearLayoutManager.HORIZONTAL, false));
        weatherAdapter = new WeatherAdapter(rootView.getContext(), allWeatherData);
        recyclerView.setAdapter(weatherAdapter);

        if (locationList == null) {
            return;
        }

        if (endDate.compareTo(Timestamp.now()) < 0) {
            TextView weatherForecastTitle = rootView.findViewById(R.id.weatherForecastTitle);
            RecyclerView weatherRecyclerView = rootView.findViewById(R.id.weatherRecyclerView);
            weatherForecastTitle.setVisibility(View.GONE);
            weatherRecyclerView.setVisibility(View.GONE);
            return;
        }

        // request weather data for all locations in the trip
        for (Location location : locationList) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            // Calculate start index and end index
            Calendar calendar = Calendar.getInstance();
            Date date = calendar.getTime();
            // Timestamp currentTime = new Timestamp(date);
            LocalDateTime start = LocalDateTime
                    .ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.DAYS);

            LocalDateTime stop = LocalDateTime
                    .ofInstant(Instant.ofEpochMilli(startDate.getSeconds() * 1000), ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.DAYS);

            Duration duration = Duration.between(start, stop);

            int startDateIndex = (int) duration.toDays();
            // int startDateIndex = (int) TimeUnit.SECONDS.toDays(startDate.getSeconds() -
            // currentTime.getSeconds());
            Log.d("start date index", String.valueOf(startDateIndex));
            int endDateIndex = startDateIndex + lastingDays;

            // Adjust start index if today is after start date
            if (startDateIndex < 0) {
                startDateIndex = 0;
                if (endDateIndex > 5) {
                    endDateIndex = 5;
                }
            } else if (startDateIndex >= 16) {
                startDateIndex = 0;
                endDateIndex = 5;
            } else {
                if (endDateIndex - startDateIndex > 5) {
                    endDateIndex = startDateIndex + 5;
                }
                if (endDateIndex > 16) {
                    endDateIndex = 16;
                }
            }

            int finalStartDateIndex = startDateIndex;
            int finalEndDateIndex = endDateIndex;

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                Map<Integer, Weather> weatherData = weatherAPIClient.getWeatherForecast(location.getName(), latitude,
                        longitude, finalStartDateIndex, finalEndDateIndex);
                handler.post(() -> {
                    if (!isAdded()) {
                        // Fragment is not attached to the activity anymore, so we can't proceed.
                        return;
                    }
                    if (weatherData != null && !weatherData.isEmpty()) {
                        allWeatherData.add(weatherData);
                        // Notify the adapter that the data has changed
                        weatherAdapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(getContext(), "Failed to fetch weather data", Toast.LENGTH_SHORT).show();
                    }
                    executor.shutdown(); // Shut down the executor
                });
            });
        }
    }

    // Define a callback interface for weather data retrieval
    public interface WeatherDataCallback {
        void onSuccess(Map<Integer, Weather> weatherData);
        void onFailure(String errorMessage);
    }

    // Modified method to fetch weather data for a specific date with a callback
    private void fetchWeatherDataForDate(Timestamp startDate, int dayIndex, WeatherDataCallback callback) {
        if (locationList == null) {
            callback.onFailure("Location list is null");
            return;
        }

        // Calculate the target date based on startDate and dayIndex
        LocalDate targetDate = startDate.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                .plusDays(dayIndex);

        // Calculate the index for the target date
        LocalDate startLocalDate = startDate.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        long daysBetween = ChronoUnit.DAYS.between(startLocalDate, targetDate);
        int targetDateIndex = (int) daysBetween;

        // Request weather data for all locations in the trip
        for (Location location : locationList) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                Map<Integer, Weather> weatherData = WeatherAPIClient.getWeatherForecast(location.getName(), latitude,
                        longitude, targetDateIndex, targetDateIndex + 1);
                handler.post(() -> {
                    if (!isAdded()) {
                        // Fragment is not attached to the activity anymore, so we can't proceed.
                        return;
                    }
                    if (weatherData != null && !weatherData.isEmpty()) {
                        callback.onSuccess(weatherData);
                    } else {
                        callback.onFailure("Failed to fetch weather data for the specified date");
                    }
                    executor.shutdown();
                });
            });
        }
    }

    // can not solve the bug
    private void showTripNote(View rootView) {
        // Handle noteinput
        EditText noteInput = rootView.findViewById(R.id.noteInput);

        if (trip != null) {
            // Load saved note if exists
            String savedNote = trip.getNote();
            if (savedNote != null) {
                noteInput.setText(savedNote);
            }
        } else {
            Log.d("noteinput", "showTripNote: trip is null");
            return;
        }

        FirestoreDB firestoreDB = FirestoreDB.getInstance();

        // Save note input when the user types
        noteInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing before text changes
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.d("NoteInput", "User input: " + s.toString());
                trip.setNote(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Log.d("trip note saved", "new trip: " + trip.toString());
                Log.d("GPT", "ActivityItem: " + trip.getPlans());
                firestoreDB.updateTrip(trip.getId(), trip, success -> {
                    if (success) {
                        Log.d("trip saved", "saveTripToDatabase: success");
                    } else {
                        Log.e("trip saved", "saveTripToDatabase: fail");
                    }
                });
            }
        });
    }

    private Timestamp buildTimestamp(Timestamp startDate, int dayIndex, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate.toDate());
        calendar.add(Calendar.DAY_OF_YEAR, dayIndex);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Date date = calendar.getTime();
        return new Timestamp(date);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasPoints = false; // Variable to track if points are included

        HashMap<String, List<Double[]>> daysAndLocationsMap = getDaysAndLocations();

        HashMap<String, List<String>> locationNames = getLocationNames();

        if (daysAndLocationsMap == null) {
            return;
        }

        for (String key : daysAndLocationsMap.keySet()) {
            List<Double[]> latLngList = daysAndLocationsMap.get(key);
            String days = String.valueOf((Integer.parseInt(key) + 1));

            if (latLngList != null && !latLngList.isEmpty()) {
                for (Double[] coords : latLngList) {
                    if (coords != null && coords.length >= 2) {
                        LatLng point = new LatLng(coords[0], coords[1]);
                        mMap.addMarker(new MarkerOptions().position(point).title("DAY" + days));
                        boundsBuilder.include(point); // Include point in bounds
                        hasPoints = true; // Set to true since we've added a point
                    }
                }
                // Add route for all days
                getRoutePoints(latLngList);
            }
        }

        int padding = 100;
        if (hasPoints) {
            final LatLngBounds bounds = boundsBuilder.build();
            final View mapView = getView().findViewById(R.id.map); // Ensure this is the correct ID

            if (mapView.getViewTreeObserver().isAlive()) {
                mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @SuppressWarnings("deprecation")
                    @Override
                    public void onGlobalLayout() {
                        // Remove the listener to prevent multiple calls
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }

                        // Now that the layout has happened, move the camera
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                    }
                });
            }
        } else {
            // Handle the case where no points are included
            LatLng defaultLocation = new LatLng(0, 0); // Replace with a meaningful default location
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 1));
        }
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                Intent intent = new Intent(getActivity(), MapActivity.class);
                intent.putExtra("daysAndLocationsMap", daysAndLocationsMap);
                intent.putExtra("locationNames", locationNames);
                intent.putExtra("numDays",viewModel.getTrip().getNumDays());
                startActivity(intent);
            }
        });
    }

    private void getRoutePoints(List<Double[]> latLngList) {
        if (latLngList == null || latLngList.size() < 2) {
            Log.d("MapActivity", "Not enough waypoints to draw route");
            return;
        }

        List<LatLng> waypoints = new ArrayList<>();
        for (Double[] location : latLngList) {
            waypoints.add(new LatLng(location[0], location[1]));
            Log.d("location", "lat: " + location[0] + " lon: " + location[1]);
        }

        Log.d("MapActivity", "Waypoints: " + waypoints);

        try {
            RouteDrawing routeDrawing = new RouteDrawing.Builder()
                    .context(PlanFragment.this.getContext())
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(PlanFragment.this)
                    .alternativeRoutes(true)
                    .waypoints(waypoints)
                    .build();
            Log.d("MapActivity", "Executing RouteDrawing");
            routeDrawing.execute();
        } catch (Exception e) {
            Log.e("MapActivity", "Error in RouteDrawing setup", e);
        }
    }

    @Override
    public void onRouteFailure(ErrorHandling e) {
        Log.e("MapActivity", "Route calculation failed: " + e.getMessage());
    }

    @Override
    public void onRouteStart() {
        Log.d("TAG", "yes started");
    }

    @Override
    public void onRouteSuccess(ArrayList<RouteInfoModel> routeInfoModelArrayList, int routeIndexing) {
        Log.d("MapActivity", "onRouteSuccess called. Routes: " + routeInfoModelArrayList.size());

        // if ( polylines != null) {
        // for (Polyline line : polylines) {
        // line.remove();
        // }
        // polylines.clear();
        // }
        PolylineOptions polylineOptions = new PolylineOptions();
        // Generate a random color

        int randomColor = Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
        for (int i = 0; i < routeInfoModelArrayList.size(); i++) {
            if (i == routeIndexing) {
                Log.e("TAG", "onRoutingSuccess: routeIndexing" + routeIndexing);
                polylineOptions.color(randomColor);
                polylineOptions.width(12);
                polylineOptions.addAll(routeInfoModelArrayList.get(routeIndexing).getPoints());
                polylineOptions.startCap(new RoundCap());
                polylineOptions.endCap(new RoundCap());
                Polyline polyline = mMap.addPolyline(polylineOptions);
                polylines.add(polyline);
            }
        }
    }

    @Override
    public void onRouteCancelled() {
        Log.d("TAG", "route canceled");
        // restart your route drawing
    }

    private void addMarkersForLatLngList(List<Double[]> latLngList, String title, LatLngBounds.Builder boundsBuilder) {
        for (Double[] latLng : latLngList) {
            double latitude = latLng[0];
            double longitude = latLng[1];
            LatLng location = new LatLng(latitude, longitude);

            mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .icon(BitmapDescriptorFactory.fromBitmap(createCustomMarker(title))));
            boundsBuilder.include(location);
        }
    }

    private Bitmap createCustomMarker(String text) {

        Bitmap bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // background
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.LTGRAY);
        backgroundPaint.setStyle(Paint.Style.FILL);

        // background size
        canvas.drawRect(0, 0, 200, 100, backgroundPaint);

        // text
        Paint textPaint = new Paint();
        textPaint.setTextSize(40);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText(text, 100, 60, textPaint);

        return bitmap;
    }

    public void setLastingDays(int lastingDays) {
        this.lastingDays = lastingDays;
    }

    public void setLocationList(List<Location> locationList) {
        this.locationList = locationList;
    }

    public void setStartDate(String startDay) {
        this.startDay = startDay;
    }

    public HashMap<String, List<Double[]>> getDaysAndLocations() {
        HashMap<String, List<Double[]>> locationMap = new HashMap<>();

        if (trip == null) {
            return null;
        }

        Set<String> keys = trip.getPlans().keySet();

        for (String key : keys) {
            List<ActivityItem> activityItems = trip.getPlans().get(key);
            List<Double[]> latLngList = new ArrayList<>();

            for (ActivityItem item : activityItems) {
                Location location = item.getLocation();
                if (location != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    latLngList.add(new Double[] { latitude, longitude });
                }
            }
            locationMap.put(key, latLngList);
        }
        return locationMap;
    }


    public HashMap<String, List<String>> getLocationNames() {
        HashMap<String, List<String>> locationName = new HashMap<>();

        if (trip == null) {
            return null;
        }

        Set<String> keys = trip.getPlans().keySet();

        for (String key : keys) {
            List<ActivityItem> activityItems = trip.getPlans().get(key);
            List<String> nameList = new ArrayList<>();

            for (ActivityItem item : activityItems) {
                String name = item.getName();
                if (name != null && !name.isEmpty()) {
                    nameList.add(name);
                }
            }
            locationName.put(key, nameList);
        }
        return locationName;
    }

    private void preparePlanItems() {
        if (planItems == null) {
            planItems = new ArrayList<>();
        } else {
            planItems.clear();
        }

        for (int i = 0; i < activityItemArray.size(); i++) {
            planItems.add(new PlanItem(activityItemArray.get(i)));

            if (i < activityItemArray.size() - 1) {
                ActivityItem currentItem = activityItemArray.get(i);
                ActivityItem nextItem = activityItemArray.get(i + 1);

                if (currentItem.getLocation() != null && nextItem.getLocation() != null) {
                    planItems.add(new PlanItem((RouteInfo) null));
                    int routeInfoPosition = planItems.size() - 1;
                    fetchRouteInfo(currentItem.getLocation(), nextItem.getLocation(), routeInfoPosition);
                }
            }
        }
    }

    private void fetchRouteInfo(Location origin, Location destination, int routeInfoPosition) {
        List<ActivityItem> activityItems = new ArrayList<>();
        ActivityItem originItem = new ActivityItem("Origin");
        originItem.setLocation(origin);
        ActivityItem destinationItem = new ActivityItem("Destination");
        destinationItem.setLocation(destination);
        activityItems.add(originItem);
        activityItems.add(destinationItem);
        RoutePlanner.fetchDistanceMatrix(activityItems, "driving", new DistanceMatrixCallback() {
            @Override
            public void onSuccess(List<DistanceMatrixEntry> distanceMatrix) {
                DistanceMatrixEntry entry = RoutePlanner.getDistanceMatrixEntry(distanceMatrix,
                        origin.getNonNullIdOrName(),
                        destination.getNonNullIdOrName());

                if (entry != null && entry.getDuration() != null && entry.getDistance() != null) {
                    RouteInfo routeInfo = new RouteInfo(entry.getDuration(), entry.getDistance());
                    PlanItem routePlanItem = planItems.get(routeInfoPosition);
                    routePlanItem = new PlanItem(routeInfo);
                    planItems.set(routeInfoPosition, routePlanItem);

                    mainHandler.post(() -> {
                        if (isAdded()) {
                            adapter.notifyItemChanged(routeInfoPosition);
                        } else {
                            Log.d("PlanFragment", "Fragment not attached, cannot update UI");
                        }
                    });
                } else {
                    Log.d("PlanFragment", "No route information available between " + origin.getNonNullIdOrName() + " and " + destination.getNonNullIdOrName());
                    RouteInfo routeInfo = new RouteInfo("No route available", "");
                    PlanItem routePlanItem = planItems.get(routeInfoPosition);
                    routePlanItem = new PlanItem(routeInfo);
                    planItems.set(routeInfoPosition, routePlanItem);

                    mainHandler.post(() -> {
                        if (isAdded()) {
                            adapter.notifyItemChanged(routeInfoPosition);
                        } else {
                            Log.d("PlanFragment", "Fragment not attached, cannot update UI");
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.d("RoutePlannerUtil", "Failed to fetch Distance Matrix: " + e.getMessage());
            }
        });
    }

    private int getActivityItemIndex(int planItemPosition) {
        int activityIndex = -1;
        for (int i = 0; i <= planItemPosition; i++) {
            PlanItem item = planItems.get(i);
            if (item.getType() == PlanItem.TYPE_ACTIVITY) {
                activityIndex++;
            }
        }
        return activityIndex;
    }

    private int getActivityItemIndexForMove(int planItemPosition) {
        int activityIndex = 0;
        for (int i = 0; i < planItemPosition; i++) {
            PlanItem item = planItems.get(i);
            if (item.getType() == PlanItem.TYPE_ACTIVITY) {
                activityIndex++;
            }
        }
        return activityIndex;
    }

    private void removeConnectedRouteItems(int activityPosition) {
        if (activityPosition > 0 && planItems.get(activityPosition - 1).getType() == PlanItem.TYPE_ROUTE_INFO) {
            planItems.remove(activityPosition - 1);
            activityPosition--;
        }
        if (activityPosition < planItems.size() - 1 && planItems.get(activityPosition + 1).getType() == PlanItem.TYPE_ROUTE_INFO) {
            planItems.remove(activityPosition + 1);
        }
    }

}
