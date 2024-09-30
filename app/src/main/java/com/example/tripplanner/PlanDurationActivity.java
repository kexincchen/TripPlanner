package com.example.tripplanner;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.tripplanner.adapter.AutocompleteAdapter;
import com.example.tripplanner.adapter.ButtonDecorator;
import com.example.tripplanner.databinding.PlanDurationBinding;
import com.example.tripplanner.db.FirestoreDB;
import com.example.tripplanner.entity.Location;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.fragment.PlanDurationFragment;
import com.example.tripplanner.utils.OnFragmentInteractionListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import com.google.android.gms.common.api.ApiException;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PlanDurationActivity extends AppCompatActivity  implements OnFragmentInteractionListener {
    private PlanDurationBinding binding;
    private JSONObject planDetails =  new JSONObject();;
    private JSONArray locationList = new JSONArray();;
    private int receivedDays;
    private String receivedStartDate;
    private String receivedEndDate;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    final String apiKey = BuildConfig.PLACES_API_KEY;

    //Object 类型
//    {
//        "location":[],
//        "days": int,
//        "startdate": Date,
//        "enddate": Date;
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = PlanDurationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //2. add data into the JSONArray
        String location = getIntent().getStringExtra("selectedPlace");
        locationList.put(location);

        //Remove Button (Dynamic add based on the JSON Objects)
        //3. add the button based on the JSONARRAY
        ButtonDecorator buttonDecorator = new ButtonDecorator( findViewById(R.id.constraint_layout));
        buttonDecorator.addButtonsFromJson(locationList);

        //Back Button Functions
        Button backButton = findViewById(R.id.button_back);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //finish PlanDuration Activity
                finish();
            }
        });

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), apiKey);
        }

        //Button Add Location Functions
        //New location will be add into the JSONArray
        Button addLocationButton = findViewById(R.id.button_add_location);
        addLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Set BottomSheetDialog and get the xml view
                BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(PlanDurationActivity.this);
                View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_layout, null);
                bottomSheetDialog.setContentView(bottomSheetView);

                bottomSheetDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialogInterface) {
                        FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                        if (bottomSheet != null) {
                            // Set BottomSheet Height as 90%
                            ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                            layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.9);
                            bottomSheet.setLayoutParams(layoutParams);

                            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                            behavior.setSkipCollapsed(true);
                        }
                    }
                });
                PlacesClient placesClient = Places.createClient(PlanDurationActivity.this);

                SearchView searchViewLocation = bottomSheetView.findViewById(R.id.searchViewLocation);
                ListView listViewAutocomplete = bottomSheetView.findViewById(R.id.listViewAutocomplete);

                AutocompleteAdapter adapter = new AutocompleteAdapter(PlanDurationActivity.this, new ArrayList<>());
                listViewAutocomplete.setAdapter(adapter);

                searchViewLocation.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        if (!query.isEmpty()) {
                            bottomSheetDialog.dismiss();
                            String selectedPlace = query;
                        } else {
                            Toast.makeText(PlanDurationActivity.this, "Please enter a location", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (newText.length() > 0) {
                            performAutocomplete(newText, placesClient, adapter);
                            listViewAutocomplete.setVisibility(View.VISIBLE);
                        } else {
                            adapter.clear();
                            adapter.notifyDataSetChanged();
                            listViewAutocomplete.setVisibility(View.GONE);
                        }
                        return true;
                    }
                });

                listViewAutocomplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View itemView, int position, long id) {
                        AutocompletePrediction prediction = adapter.getItem(position);
                        String fullText = prediction.getFullText(null).toString();
                        searchViewLocation.setQuery(fullText, false);
                        bottomSheetDialog.dismiss();
                        String selectedPlace = prediction.getPrimaryText(null).toString();
                        locationList.put(selectedPlace);
                        buttonDecorator.addButtonsFromJson(locationList);
                    }
                });



                // Display BottomSheetDialog
                bottomSheetDialog.show();
            }
        });

        //TabLayout Functions
        TabLayout tabLayout = binding.tabLayout;
        tabLayout.addTab(tabLayout.newTab().setText("Days"));
        tabLayout.addTab(tabLayout.newTab().setText("Calendar"));
        loadFragment(PlanDurationFragment.newInstance(PlanDurationFragment.DAYS));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment selectedFragment;
                if (tab.getPosition() == 0) {
                    selectedFragment = PlanDurationFragment.newInstance(PlanDurationFragment.DAYS);
                } else {
                    selectedFragment = PlanDurationFragment.newInstance(PlanDurationFragment.CALENDAR);
                }
                loadFragment(selectedFragment);
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        //Button Done
        Button doneButton = findViewById(R.id.button_done);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    planDetails.put("location", locationList);
                    planDetails.put("days" ,receivedDays);
                    planDetails.put("startDate", receivedStartDate);
                    planDetails.put("endDate", receivedEndDate);

                    FirebaseAuth mAuth = FirebaseAuth.getInstance();
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null) {
                        String userId = currentUser.getUid();
                        // Create a new Trip object and upload it to the database
                        List<Location> locations = new ArrayList<>();
                        for (int i = 0; i < locationList.length(); i++) {
                            String loc = locationList.optString(i, null);
                            if (loc != null) {
                                locations.add(new Location(loc, 0.0, 0.0));
                            }
                        }

//                        Trip trip = new Trip("New Trip", LocalDate.parse(receivedStartDate), receivedDays,locations, userId);
//                        FirestoreDB firestore = new FirestoreDB();
//                        firestore.createTrip("userId", trip.convertTripToMap());

                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        Date parsedDate;
                        try {
                            parsedDate = dateFormat.parse(receivedStartDate);
                        } catch (ParseException e) {
                            e.printStackTrace();
                            return;
                        }
                        Timestamp startDate = new Timestamp(parsedDate);

                        // Create Trip object
                        Trip trip = new Trip("New Trip", startDate, receivedDays, locations, userId);

                        // Create FirestoreDB instance and add trip to Firestore
                        FirestoreDB firestore = new FirestoreDB();
                        firestore.createTrip(userId, trip.convertTripToMap());
                    } else {
                        Log.d("PLAN", "[PlanDurationActivity] No user is signed in.");
                    }

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                Intent intent = new Intent(PlanDurationActivity.this, EditPlanActivity.class);
                intent.putExtra("planDetails", planDetails.toString());
                startActivity(intent);
            }
        });

    }


    private void loadFragment(Fragment fragment) {

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }


    @Override
    public void DaysInteraction(String data) {


        receivedDays =  Integer.parseInt(data);

        Calendar calendar = Calendar.getInstance();
        receivedStartDate =  dateFormat.format(calendar.getTime());

        calendar.add(Calendar.DAY_OF_MONTH, receivedDays - 1);
        receivedEndDate=dateFormat.format(calendar.getTime());
    }

    @Override
    public void DatesInteraction(String startDate, String endDate){
        receivedStartDate = startDate;
        receivedEndDate = endDate;

        try {
            Date start = dateFormat.parse(startDate);
            Date end = dateFormat.parse(endDate);

            Calendar startCal = Calendar.getInstance();
            Calendar endCal = Calendar.getInstance();
            startCal.setTime(start);
            endCal.setTime(end);

            int daysDifference = 0;
            while (!startCal.after(endCal)) {
                startCal.add(Calendar.DAY_OF_MONTH, 1);
                daysDifference++;
            }
            receivedDays = daysDifference;

        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void performAutocomplete(String query, PlacesClient placesClient, AutocompleteAdapter adapter) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build();

        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            adapter.clear();
            adapter.addAll(response.getAutocompletePredictions());
            adapter.notifyDataSetChanged();
        }).addOnFailureListener(exception -> {
            if (exception instanceof ApiException) {
                ApiException apiException = (ApiException) exception;
                Toast.makeText(PlanDurationActivity.this, "Error fetching autocomplete predictions: " + apiException.getStatusCode(), Toast.LENGTH_LONG).show();
            }
        });
    }

}
