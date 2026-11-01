package dev.wander.android.opentagviewer.ui.family;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.data.model.BeaconInformation;

/** AirTags associated with the detail screen's local profile. */
public final class AssociatedAirTagAdapter
        extends RecyclerView.Adapter<AssociatedAirTagAdapter.Holder> {
    private final List<BeaconInformation> airTags = new ArrayList<>();
    private final String currentBeaconId;
    private final Consumer<String> onOpen;

    public AssociatedAirTagAdapter(
            final String currentBeaconId,
            final Consumer<String> onOpen) {
        this.currentBeaconId = currentBeaconId;
        this.onOpen = onOpen;
    }

    public void replaceWith(final List<BeaconInformation> replacement) {
        this.airTags.clear();
        this.airTags.addAll(replacement);
        this.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.associated_airtag_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, final int position) {
        final BeaconInformation airTag = this.airTags.get(position);
        final boolean current = airTag.getBeaconId().equals(this.currentBeaconId);
        holder.name.setText(airTag.getName());
        final String state = holder.itemView.getContext().getString(current
                ? R.string.detail_current_airtag : R.string.detail_view_airtag);
        holder.state.setText(state);
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                R.string.detail_associated_airtag_accessibility,
                airTag.getName(),
                state));
        holder.itemView.setOnClickListener(view -> {
            if (!current) {
                this.onOpen.accept(airTag.getBeaconId());
            }
        });
        holder.itemView.setClickable(!current);
        holder.itemView.setFocusable(true);
    }

    @Override
    public int getItemCount() {
        return this.airTags.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView state;

        Holder(final View view) {
            super(view);
            this.name = view.findViewById(R.id.associated_airtag_name);
            this.state = view.findViewById(R.id.associated_airtag_state);
        }
    }
}
