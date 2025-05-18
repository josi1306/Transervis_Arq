// PlacesAutoCompleteAdapter.java - Crea este archivo nuevo
package com.example.transervis;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PlacesAutoCompleteAdapter extends ArrayAdapter<AutocompletePrediction> implements Filterable {
    private final List<AutocompletePrediction> mResultList = new ArrayList<>();
    private final PlacesClient placesClient;
    private AutocompleteSessionToken token;
    private LayoutInflater inflater;

    public PlacesAutoCompleteAdapter(Context context, PlacesClient placesClient) {
        super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        this.placesClient = placesClient;
        this.token = AutocompleteSessionToken.newInstance();
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
        }

        AutocompletePrediction item = getItem(position);
        if (item != null) {
            TextView textView = (TextView) convertView;
            textView.setText(item.getFullText(null).toString());
        }

        return convertView;
    }

    @Override
    public int getCount() {
        return mResultList.size();
    }

    @Override
    public AutocompletePrediction getItem(int position) {
        return mResultList.get(position);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();

                // Skip the autocomplete query if no constraints are given
                if (constraint == null || constraint.length() < 3) {
                    return results;
                }

                // Query the autocomplete API for the entered constraint
                mResultList.clear();

                // Submit the query to the autocomplete API
                FindAutocompletePredictionsRequest.Builder requestBuilder =
                        FindAutocompletePredictionsRequest.builder()
                                .setTypeFilter(TypeFilter.ADDRESS)
                                .setSessionToken(token)
                                .setQuery(constraint.toString());

                Task<FindAutocompletePredictionsResponse> responseTask =
                        placesClient.findAutocompletePredictions(requestBuilder.build());

                try {
                    // Wait for the task to complete
                    Tasks.await(responseTask, 60, TimeUnit.SECONDS);

                    if (responseTask.isSuccessful()) {
                        FindAutocompletePredictionsResponse response = responseTask.getResult();
                        if (response != null) {
                            mResultList.addAll(response.getAutocompletePredictions());
                        }
                    }
                } catch (ExecutionException | InterruptedException | TimeoutException e) {
                    e.printStackTrace();
                }

                results.values = mResultList;
                results.count = mResultList.size();

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                if (resultValue instanceof AutocompletePrediction) {
                    return ((AutocompletePrediction) resultValue).getFullText(null);
                } else {
                    return super.convertResultToString(resultValue);
                }
            }
        };
    }

    public void refreshToken() {
        this.token = AutocompleteSessionToken.newInstance();
    }
}