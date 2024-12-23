package com.example.tripplanner.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tripplanner.R;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.utils.TimeUtils;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SectionedPlanAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SECTION = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private final Context context;
    private static List<Object> items;
    private static SectionedPlanInterface sectionedPlanInterface;

    public SectionedPlanAdapter(Context context,  Map<String, List<Trip>> tripsByCountry, SectionedPlanInterface planInterface) {
        this.context = context;
        this.items = new ArrayList<>();
        this.sectionedPlanInterface = planInterface;
        prepareItems(tripsByCountry);
    }

    private void prepareItems(Map<String, List<Trip>> tripsByCountry) {
        for (Map.Entry<String, List<Trip>> entry : tripsByCountry.entrySet()) {
            String country = entry.getKey();
            List<Trip> trips = entry.getValue();
            items.add(country); // Add country as a section header
            items.addAll(trips); // Add trips under the country
        }
    }


    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? VIEW_TYPE_SECTION : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_SECTION) {
            View view = inflater.inflate(R.layout.section_header, parent, false);
            return new SectionViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.all_plan_row, parent, false);
            return new TripViewHolder(view);
        }
    }
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).sectionTitle.setText((String) items.get(position));
        } else if (holder instanceof TripViewHolder) {
            Trip trip = (Trip) items.get(position);
            ((TripViewHolder) holder).bind(trip);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView sectionTitle;

        public SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            sectionTitle = itemView.findViewById(R.id.sectionTitle);
        }
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        private ImageView img;
        TextView locations;
        private TextView duration;
        private TextView numActivity;
        private CardView plan;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.image);
            locations = itemView.findViewById(R.id.locations);
            duration = itemView.findViewById(R.id.duration);
            numActivity = itemView.findViewById(R.id.numActivity);
            plan = itemView.findViewById(R.id.plan);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (sectionedPlanInterface != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            Trip trip = (Trip) items.get(pos);
                            sectionedPlanInterface.onTripClick(trip);
                        }
                    }
                }
            });
        }

        public void bind(Trip trip) {
            if (trip == null){
                return;
            }
            // Bind data to views here (similar to AllPlanAdapter.ViewHolder.bind(trip))
            // Example:
            locations.setText(trip.getLocationsString());
            Date endDateMinusOneDay = new Date(trip.getEndDate().toDate().getTime() - TimeUnit.DAYS.toMillis(1));
            Timestamp adjustedEndDate = new Timestamp(endDateMinusOneDay);
            duration.setText(trip.getDurationString() + "\n" + TimeUtils.formatTimestampForUI(trip.getStartDate()) + " ~ " + TimeUtils.formatTimestampForUI(adjustedEndDate));
            numActivity.setText(trip.getActivityCountString());
            img.setImageResource(trip.getCityDrawable());
            plan.setCardBackgroundColor(itemView.getResources().getColor(getRandomColor(trip.getLocationsString().length())));

        }

        private int getRandomColor(int len) {
            List<Integer> colorCode = new ArrayList<>();
            colorCode.add(R.color.blue);
            colorCode.add(R.color.pink);
            colorCode.add(R.color.yellow);
            colorCode.add(R.color.orange);

            return colorCode.get(len % colorCode.size());
        }
    }

}

