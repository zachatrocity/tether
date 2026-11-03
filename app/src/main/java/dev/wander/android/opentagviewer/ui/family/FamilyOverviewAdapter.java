package dev.wander.android.opentagviewer.ui.family;

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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButton;

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
            bindActionIcon(holder.edit, R.drawable.edit_24px);
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
        bindAirTagAvatar(holder.edit, airTag);
        holder.freshness.setVisibility(VISIBLE);
        holder.evidence.setVisibility(VISIBLE);
        holder.statusDot.setVisibility(VISIBLE);
        holder.chevron.setVisibility(VISIBLE);
        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);

        final long nowMs = System.currentTimeMillis();
        final FamilyOverviewEvidence.State state = FamilyOverviewEvidence.classify(
                row.getReport(), row.getLastSighting(), nowMs);
        final String freshness = this.freshness(holder, row, state, nowMs);
        final String evidence = this.evidence(holder, row, state);
        holder.freshness.setText(freshness);
        holder.evidence.setText(evidence);
        this.tintFreshness(holder, FamilyOverviewEvidence.ageTone(
                state, row.getReport(), row.getLastSighting(), nowMs));

        final String description = holder.itemView.getContext().getString(
                R.string.family_row_content_description,
                primaryLabel, secondaryLabel, freshness, evidence);
        holder.itemView.setContentDescription(description);
        holder.itemView.setOnClickListener(view -> this.onDetails.accept(airTag.getBeaconId()));

        holder.edit.setVisibility(VISIBLE);
        if (row.getProfileId() == null) {
            holder.edit.setContentDescription(holder.itemView.getContext().getString(
                    R.string.family_assign_profile_content_description, airTag.getName()));
        } else {
            holder.edit.setContentDescription(holder.itemView.getContext().getString(
                    R.string.family_edit_profile_content_description, row.getPersonName()));
        }
        holder.edit.setOnClickListener(view -> this.onEditProfile.accept(row));
    }

    /**
     * The AirTag's own emoji is its avatar on every other tag surface, so it wins here too.
     * The type-specific icon is the fallback for tags whose naming record has no emoji.
     */
    private static void bindAirTagAvatar(
            final MaterialButton avatar, final BeaconInformation airTag) {
        avatar.setText(null);
        if (airTag.isEmojiFilled()) {
            avatar.setIcon(null);
            avatar.setText(airTag.getEmoji());
            return;
        }
        BeaconIcon.applyTo(avatar, airTag);
    }

    /** Restore the profile action after a RecyclerView holder previously showed an AirTag. */
    private static void bindActionIcon(
            final MaterialButton action, final int iconResource) {
        action.setText(null);
        action.setIcon(AppCompatResources.getDrawable(action.getContext(), iconResource));
        action.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(
                action, com.google.android.material.R.attr.colorPrimary)));
    }

    private String freshness(
            final RowHolder holder,
            final FamilyOverviewRow row,
            final FamilyOverviewEvidence.State state,
            final long nowMs) {
        if (state == FamilyOverviewEvidence.State.NEARBY) {
            return holder.itemView.getContext().getString(R.string.family_nearby_now);
        }
        final BeaconLocationReport report = row.getReport();
        if (report == null) {
            return holder.itemView.getContext().getString(R.string.family_no_report_yet);
        }
        final String age = DateUtils.getRelativeTimeSpanString(
                report.getTimestamp(), nowMs, DateUtils.MINUTE_IN_MILLIS)
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

    private static void tintFreshness(
            final RowHolder holder, final FamilyOverviewEvidence.AgeTone tone) {
        final int statusAttribute;
        final int textAttribute;
        switch (tone) {
            case FRESH:
                statusAttribute = com.google.android.material.R.attr.colorPrimary;
                textAttribute = com.google.android.material.R.attr.colorPrimary;
                break;
            case WARNING:
                statusAttribute = com.google.android.material.R.attr.colorTertiary;
                textAttribute = com.google.android.material.R.attr.colorTertiary;
                break;
            case OLD:
                statusAttribute = com.google.android.material.R.attr.colorError;
                textAttribute = com.google.android.material.R.attr.colorError;
                break;
            case UNKNOWN:
            default:
                statusAttribute = com.google.android.material.R.attr.colorOutline;
                textAttribute = com.google.android.material.R.attr.colorOnSurface;
                break;
        }
        holder.statusDot.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(
                holder.itemView, statusAttribute)));
        holder.freshness.setTextColor(MaterialColors.getColor(holder.itemView, textAttribute));
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
        private final MaterialButton edit;
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
