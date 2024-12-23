package com.example.tripplanner.fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.tripplanner.R;
import com.example.tripplanner.adapter.DisablePastDatesDecorator;
import com.example.tripplanner.adapter.HighlightRangeDecorator;
import com.example.tripplanner.utils.OnFragmentInteractionListener;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.shawnlin.numberpicker.NumberPicker;

import org.json.JSONException;

import java.util.List;
import java.util.Locale;

public class PlanDurationFragment extends Fragment {

    public static final int DAYS = R.layout.plan_duration_fragment_days;
    public static final int CALENDAR = R.layout.plan_duration_fragment_calendar;
    public static final String LAYOUT_TYPE = "type";

    private int layout;
    private MaterialCalendarView materialCalendarView;
    private HighlightRangeDecorator rangeDecorator;

    private CalendarDay startDate = null;
    private CalendarDay endDate = null;
    private int defaultDays = 2;
    private OnFragmentInteractionListener mListener;
    private int pressCount = 0;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFragmentInteractionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    public void passDaysToActivity(String data) {
        if (mListener != null) {
            try {
                mListener.DaysInteraction(data);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void passDatesToActivity(CalendarDay startDate, CalendarDay endDate)  {
        if (startDate == null || endDate == null) {
            try {
                mListener.DatesInteraction(null, null);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        if (mListener != null) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
                String formattedStartDate = dateFormat.format(startDate.getDate());
                String formattedEndDate = dateFormat.format(endDate.getDate());

                mListener.DatesInteraction(formattedStartDate, formattedEndDate);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        layout = getArguments() != null ? getArguments().getInt(LAYOUT_TYPE, DAYS) : DAYS;
        View view = inflater.inflate(layout, container, false);

        if (layout == DAYS) {
            NumberPicker numberPicker = view.findViewById(R.id.numberPicker);
            setupNumberPicker(numberPicker);

        } else if (layout == CALENDAR) {
            materialCalendarView = view.findViewById(R.id.materialCalendarView);
            setupCalendarView();
        }
        return view;
    }

    //Number Picker Function
    private void setupNumberPicker(NumberPicker numberPicker) {
        numberPicker.setMaxValue(30);
        numberPicker.setMinValue(1);
        numberPicker.setValue(2);
        passDaysToActivity(Integer.toString(defaultDays));
        numberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                defaultDays = newVal;
                passDaysToActivity(Integer.toString(defaultDays));
            }
        });

    }

    //Calendar function
    private void setupCalendarView() {
        materialCalendarView.setSelectionMode(MaterialCalendarView.SELECTION_MODE_RANGE);

        DisablePastDatesDecorator disablePastDatesDecorator = new DisablePastDatesDecorator();
        materialCalendarView.addDecorator(disablePastDatesDecorator);

        rangeDecorator = new HighlightRangeDecorator(requireContext());
        materialCalendarView.addDecorator(rangeDecorator);

        // display the start date and end date on the calendar
        materialCalendarView.setOnDateChangedListener(new OnDateSelectedListener() {
            @Override
            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
                materialCalendarView.clearSelection();
                if (pressCount == 0) {
                    // choose start date
                    startDate = date;
                    rangeDecorator.setDateRange(Collections.singletonList(startDate));
                    endDate = date;
                    pressCount += 1;
                } else if (pressCount == 1) {
                    // choose end date
                    if (date.isBefore(startDate)) {
                        // if end date earlier than start date, replace start date to end date
                        startDate = date;
                        endDate = null;
                        rangeDecorator.setDateRange(Collections.singletonList(startDate));
                    } else {
                        endDate = date;
                        // set the date rage
                        List<CalendarDay> datesInRange = new ArrayList<>();
                        Calendar current = Calendar.getInstance();
                        current.setTime(startDate.getDate());

                        while (!current.getTime().after(endDate.getDate())) {
                            datesInRange.add(CalendarDay.from(current));
                            current.add(Calendar.DAY_OF_MONTH, 1); // add one day from start date
                        }
                        pressCount+=1;
                        rangeDecorator.setDateRange(datesInRange);
                        passDatesToActivity(startDate, endDate);
                    }
                } else if (pressCount == 2) {
                    // choose again
                    startDate = date;
                    endDate = null;
                    rangeDecorator.setDateRange(Collections.singletonList(startDate));
                    pressCount = 1;
                    passDatesToActivity(null, null);
                }
                widget.invalidateDecorators(); // refresh the date
            }
        });
    }

    public static Fragment newInstance(int layout) {
        Fragment fragment = new PlanDurationFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(LAYOUT_TYPE, layout);
        fragment.setArguments(bundle);
        return fragment;
    }

}
