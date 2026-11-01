package dev.wander.android.opentagviewer.ui.history;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.ui.evidence.AirTagEvidence;

/** Renders only source evidence and actual failures; it has no inferred movement vocabulary. */
public final class ReportHistoryAdapter
        extends RecyclerView.Adapter<ReportHistoryAdapter.Holder> {
    private final List<ReportHistoryEvent> events = new ArrayList<>();
    private final Map<String, String> places = new HashMap<>();
    private final Consumer<String> onFullHistory;

    public ReportHistoryAdapter(final Consumer<String> onFullHistory) {
        this.onFullHistory = onFullHistory;
    }

    public void replaceWith(final List<ReportHistoryEvent> replacement) {
        this.events.clear();
        this.events.addAll(replacement);
        this.notifyDataSetChanged();
    }

    public void setPlaceName(
            final String beaconId,
            final long timestampMs,
            final String placeName) {
        this.places.put(key(beaconId, timestampMs), placeName);
        for (int index = 0; index < this.events.size(); index++) {
            final ReportHistoryEvent event = this.events.get(index);
            if (beaconId.equals(event.getBeaconId())
                    && timestampMs == event.getTimestampMs()) {
                this.notifyItemChanged(index);
                return;
            }
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.report_history_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, final int position) {
        final ReportHistoryEvent event = this.events.get(position);
        final String age = DateUtils.getRelativeTimeSpanString(
                event.getTimestampMs(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString();
        holder.age.setText(age);
        holder.title.setText(this.title(holder, event));
        holder.body.setText(this.body(holder, event));
        this.tint(holder, event.getType());

        final boolean hasAirTag = event.getBeaconId() != null;
        holder.fullHistory.setVisibility(hasAirTag ? VISIBLE : GONE);
        holder.fullHistory.setOnClickListener(hasAirTag
                ? view -> this.onFullHistory.accept(event.getBeaconId()) : null);
        if (hasAirTag) {
            holder.fullHistory.setContentDescription(holder.itemView.getContext().getString(
                    R.string.report_history_action_accessibility, event.getAirTagName()));
        }

        final String evidenceType = this.evidenceType(holder, event.getType());
        final String tag = hasAirTag
                ? event.getAirTagName()
                : holder.itemView.getContext().getString(R.string.report_history_tether_event);
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                R.string.report_history_event_accessibility,
                evidenceType,
                age,
                tag,
                holder.body.getText()));
        holder.itemView.setFocusable(true);
        holder.line.setVisibility(position == this.events.size() - 1 ? GONE : VISIBLE);
    }

    private String title(final Holder holder, final ReportHistoryEvent event) {
        switch (event.getType()) {
            case LOCAL_SIGHTING:
                return holder.itemView.getContext().getString(
                        R.string.report_history_local_title, event.getAirTagName());
            case APPLE_REPORT:
                return holder.itemView.getContext().getString(
                        R.string.report_history_apple_title, event.getAirTagName());
            case STALE_REPORT:
                return holder.itemView.getContext().getString(
                        R.string.report_history_stale_title, event.getAirTagName());
            case REFRESH_FAILURE:
            default:
                return holder.itemView.getContext().getString(
                        R.string.report_history_failure_title);
        }
    }

    private String body(final Holder holder, final ReportHistoryEvent event) {
        if (event.getType() == ReportHistoryEvent.Type.LOCAL_SIGHTING) {
            final BeaconLocationReport position = event.getReport();
            if (position == null) {
                return holder.itemView.getContext().getString(
                        R.string.report_history_local_body, event.getAirTagName());
            }
            final String place = this.places.getOrDefault(
                    key(event.getBeaconId(), event.getTimestampMs()),
                    AirTagEvidence.formatCoordinates(
                            position.getLatitude(), position.getLongitude()));
            final String accuracy = AirTagEvidence.formatAccuracy(
                    position.getHorizontalAccuracy(),
                    meters -> holder.itemView.getContext().getString(
                            R.string.evidence_accuracy_meters, meters));
            if (accuracy == null) {
                return holder.itemView.getContext().getString(
                        R.string.report_history_local_body_with_position,
                        event.getAirTagName(),
                        place);
            }
            return holder.itemView.getContext().getString(
                    R.string.report_history_local_body_with_position_accuracy,
                    event.getAirTagName(),
                    place,
                    accuracy);
        }
        if (event.getType() == ReportHistoryEvent.Type.REFRESH_FAILURE) {
            return event.getFailureMessage();
        }

        final BeaconLocationReport report = event.getReport();
        final String place = this.places.getOrDefault(
                key(event.getBeaconId(), event.getTimestampMs()),
                AirTagEvidence.formatCoordinates(report.getLatitude(), report.getLongitude()));
        final String accuracy = AirTagEvidence.formatAccuracy(
                report.getHorizontalAccuracy(),
                meters -> holder.itemView.getContext().getString(
                        R.string.evidence_accuracy_meters, meters));
        final String reportBody = accuracy == null
                ? holder.itemView.getContext().getString(
                        R.string.report_history_apple_body_without_accuracy,
                        event.getAirTagName(), place)
                : holder.itemView.getContext().getString(
                        R.string.report_history_apple_body,
                        event.getAirTagName(), place, accuracy);
        return event.getType() == ReportHistoryEvent.Type.STALE_REPORT
                ? holder.itemView.getContext().getString(
                        R.string.report_history_stale_body, reportBody)
                : reportBody;
    }

    private String evidenceType(
            final Holder holder,
            final ReportHistoryEvent.Type type) {
        switch (type) {
            case LOCAL_SIGHTING:
                return holder.itemView.getContext().getString(
                        R.string.report_history_local_evidence);
            case APPLE_REPORT:
                return holder.itemView.getContext().getString(
                        R.string.report_history_apple_evidence);
            case STALE_REPORT:
                return holder.itemView.getContext().getString(
                        R.string.report_history_stale_evidence);
            case REFRESH_FAILURE:
            default:
                return holder.itemView.getContext().getString(
                        R.string.report_history_failure_evidence);
        }
    }

    private static void tint(
            final Holder holder,
            final ReportHistoryEvent.Type type) {
        final int attribute;
        switch (type) {
            case LOCAL_SIGHTING:
                attribute = com.google.android.material.R.attr.colorPrimary;
                break;
            case APPLE_REPORT:
                attribute = com.google.android.material.R.attr.colorSecondary;
                break;
            case STALE_REPORT:
                attribute = com.google.android.material.R.attr.colorTertiary;
                break;
            case REFRESH_FAILURE:
            default:
                attribute = com.google.android.material.R.attr.colorError;
                break;
        }
        holder.dot.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(
                holder.itemView, attribute)));
    }

    private static String key(final String beaconId, final long timestampMs) {
        return beaconId + ':' + timestampMs;
    }

    @Override
    public int getItemCount() {
        return this.events.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ImageView dot;
        private final View line;
        private final TextView age;
        private final TextView title;
        private final TextView body;
        private final MaterialButton fullHistory;

        Holder(final View view) {
            super(view);
            this.dot = view.findViewById(R.id.report_event_dot);
            this.line = view.findViewById(R.id.report_event_line);
            this.age = view.findViewById(R.id.report_event_age);
            this.title = view.findViewById(R.id.report_event_title);
            this.body = view.findViewById(R.id.report_event_body);
            this.fullHistory = view.findViewById(R.id.report_event_full_history);
        }
    }
}
