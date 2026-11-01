package dev.wander.android.opentagviewer.ui.family;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.room.entity.LocationReport;
import dev.wander.android.opentagviewer.ui.BeaconIcon;

/** Binds factual AirTag evidence; colour supplements the text and never carries state alone. */
public final class FamilyOverviewAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_ROW = 0;
    private static final int TYPE_SECTION = 1;

    private final List<FamilyOverviewRow> rows = new ArrayList<>();
    private final Consumer<String> onDetails;
    private final Consumer<FamilyOverviewRow> onEditProfile;

    public FamilyOverviewAdapter(
            final Consumer<String> onDetails,
            final Consumer<FamilyOverviewRow> onEditProfile) {
        this.onDetails = onDetails;
        this.onEditProfile = onEditProfile;
    }

    public void replaceWith(final List<FamilyOverviewRow> replacement) {
        this.rows.clear();
        this.rows.addAll(replacement);
        this.notifyDataSetChanged();
    }

    public boolean hasRows() {
        return !this.rows.isEmpty();
    }

    public void setPlaceName(final String beaconId, final String placeName) {
        for (int index = 0; index < this.rows.size(); index++) {
            final FamilyOverviewRow row = this.rows.get(index);
            final BeaconInformation airTag = row.getAirTag();
            if (airTag != null && airTag.getBeaconId().equals(beaconId)) {
                row.setPlaceName(placeName);
                this.notifyItemChanged(index);
                return;
            }
        }
    }

    @Override
    public int getItemViewType(final int position) {
        return this.rows.get(position).getKind() == FamilyOverviewRow.Kind.SECTION_HEADER
                ? TYPE_SECTION : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent, final int viewType) {
        final int layout = viewType == TYPE_SECTION
                ? R.layout.family_overview_section_header : R.layout.family_overview_row;
        final View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return viewType == TYPE_SECTION ? new SectionHolder(view) : new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
        final FamilyOverviewRow row = this.rows.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).title.setText(row.getPersonName());
            return;
        }
        this.bindRow((RowHolder) holder, row, this.isFollowedByAirTag(position));
    }

    private void bindRow(
            final RowHolder holder, final FamilyOverviewRow row, final boolean showDivider) {
        holder.divider.setVisibility(showDivider ? VISIBLE : GONE);
        if (row.getKind() == FamilyOverviewRow.Kind.EMPTY_PROFILE) {
            holder.person.setText(row.getPersonName());
            holder.airTag.setText(R.string.family_no_airtag_assigned);
            holder.freshness.setVisibility(GONE);
            holder.evidence.setVisibility(GONE);
            holder.statusDot.setVisibility(GONE);
            holder.chevron.setVisibility(GONE);
            holder.itemView.setClickable(false);
            holder.itemView.setFocusable(false);
            holder.itemView.setContentDescription(null);
            holder.edit.setVisibility(VISIBLE);
            holder.edit.setImageResource(R.drawable.edit_24px);
            holder.edit.setContentDescription(holder.itemView.getContext().getString(
                    R.string.family_edit_profile_content_description, row.getPersonName()));
            holder.edit.setOnClickListener(view -> this.onEditProfile.accept(row));
            return;
        }

        final BeaconInformation airTag = row.getAirTag();
        if (airTag == null) {
            throw new IllegalStateException("An AirTag row has no AirTag");
        }

        final boolean unassigned = row.getProfileId() == null;
        final String primaryLabel = unassigned ? airTag.getName() : row.getPersonName();
        final String secondaryLabel = unassigned
                ? holder.itemView.getContext().getString(R.string.family_unassigned)
                : airTag.getName();
        holder.person.setText(primaryLabel);
        holder.airTag.setText(secondaryLabel);
        holder.freshness.setVisibility(VISIBLE);
        holder.evidence.setVisibility(VISIBLE);
        holder.statusDot.setVisibility(VISIBLE);
        holder.chevron.setVisibility(VISIBLE);
        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);

        final FamilyOverviewEvidence.State state = FamilyOverviewEvidence.classify(
                row.getReport(), row.getLastSighting(), System.currentTimeMillis());
        final String freshness = this.freshness(holder, row, state);
        final String evidence = this.evidence(holder, row, state);
        holder.freshness.setText(freshness);
        holder.evidence.setText(evidence);
        this.tintStatus(holder, state);

        final String description = holder.itemView.getContext().getString(
                R.string.family_row_content_description,
                primaryLabel, secondaryLabel, freshness, evidence);
        holder.itemView.setContentDescription(description);
        holder.itemView.setOnClickListener(view -> this.onDetails.accept(airTag.getBeaconId()));

        holder.edit.setVisibility(VISIBLE);
        if (row.getProfileId() == null) {
            holder.edit.setImageResource(R.drawable.link_24);
            holder.edit.setContentDescription(holder.itemView.getContext().getString(
                    R.string.family_assign_profile_content_description, airTag.getName()));
        } else {
            holder.edit.setImageResource(R.drawable.edit_24px);
            holder.edit.setContentDescription(holder.itemView.getContext().getString(
                    R.string.family_edit_profile_content_description, row.getPersonName()));
        }
        holder.edit.setOnClickListener(view -> this.onEditProfile.accept(row));
    }

    private String freshness(
            final RowHolder holder,
            final FamilyOverviewRow row,
            final FamilyOverviewEvidence.State state) {
        if (state == FamilyOverviewEvidence.State.NEARBY) {
            return holder.itemView.getContext().getString(R.string.family_nearby_now);
        }
        final BeaconLocationReport report = row.getReport();
        if (report == null) {
            return holder.itemView.getContext().getString(R.string.family_no_report_yet);
        }
        final String age = DateUtils.getRelativeTimeSpanString(
                report.getTimestamp(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                .toString();
        final boolean local = LocationReport.PROVENANCE_LOCAL.equals(report.getProvenance());
        final String timed = holder.itemView.getContext().getString(
                local ? R.string.family_last_heard_age : R.string.family_last_report_age, age);
        return state == FamilyOverviewEvidence.State.STALE_REPORT
                ? holder.itemView.getContext().getString(R.string.family_not_current, timed)
                : timed;
    }

    private String evidence(
            final RowHolder holder,
            final FamilyOverviewRow row,
            final FamilyOverviewEvidence.State state) {
        if (state == FamilyOverviewEvidence.State.NEARBY) {
            return holder.itemView.getContext().getString(R.string.family_heard_by_this_phone);
        }
        final BeaconLocationReport report = row.getReport();
        if (report == null) {
            return holder.itemView.getContext().getString(R.string.family_no_saved_report);
        }

        final boolean local = LocationReport.PROVENANCE_LOCAL.equals(report.getProvenance());
        final String source = holder.itemView.getContext().getString(local
                ? R.string.family_local_airtag_sighting : R.string.family_apple_network_report);
        String result = row.getPlaceName() == null || row.getPlaceName().isBlank()
                ? source
                : holder.itemView.getContext().getString(
                        R.string.family_evidence_with_source, row.getPlaceName(), source);
        if (!local && report.getHorizontalAccuracy() > 0) {
            result = holder.itemView.getContext().getString(
                    R.string.family_evidence_with_accuracy, result, report.getHorizontalAccuracy());
        }
        return result;
    }

    private static void tintStatus(
            final RowHolder holder, final FamilyOverviewEvidence.State state) {
        final int attribute;
        switch (state) {
            case NEARBY:
                attribute = com.google.android.material.R.attr.colorPrimary;
                break;
            case CURRENT_REPORT:
                attribute = com.google.android.material.R.attr.colorSecondary;
                break;
            case STALE_REPORT:
                attribute = com.google.android.material.R.attr.colorTertiary;
                break;
            case UNKNOWN:
            default:
                attribute = com.google.android.material.R.attr.colorOutline;
                break;
        }
        holder.statusDot.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(
                holder.itemView, attribute)));
    }

    private boolean isFollowedByAirTag(final int position) {
        final FamilyOverviewRow next =
                position + 1 < this.rows.size() ? this.rows.get(position + 1) : null;
        return hasDividerBetween(this.rows.get(position), next);
    }

    static boolean hasDividerBetween(
            final FamilyOverviewRow current, final FamilyOverviewRow next) {
        return current.getKind() == FamilyOverviewRow.Kind.AIR_TAG
                && next != null
                && next.getKind() == FamilyOverviewRow.Kind.AIR_TAG;
    }

    @Override
    public int getItemCount() {
        return this.rows.size();
    }

    private static final class SectionHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        SectionHolder(final View view) {
            super(view);
            this.title = view.findViewById(R.id.family_section_title);
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        private final TextView person;
        private final TextView airTag;
        private final TextView freshness;
        private final TextView evidence;
        private final ImageView statusDot;
        private final ImageView chevron;
        private final ImageButton edit;
        private final View divider;

        RowHolder(final View view) {
            super(view);
            this.person = view.findViewById(R.id.family_row_person);
            this.airTag = view.findViewById(R.id.family_row_airtag);
            this.freshness = view.findViewById(R.id.family_row_freshness);
            this.evidence = view.findViewById(R.id.family_row_evidence);
            this.statusDot = view.findViewById(R.id.family_row_status_dot);
            this.chevron = view.findViewById(R.id.family_row_chevron);
            this.edit = view.findViewById(R.id.family_row_edit_profile);
            this.divider = view.findViewById(R.id.family_row_divider);
        }
    }
}
