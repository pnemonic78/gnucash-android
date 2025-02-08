package org.gnucash.android.ui.transaction;

import android.app.Activity;
import android.content.Context;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.app.ActivityExtKt;
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.DateExtKt;

public abstract class ScheduledViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {

    protected final Refreshable refreshable;
    protected final ScheduledActionDbAdapter scheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();

    protected final ListItemScheduledTrxnBinding binding;
    protected final TextView primaryTextView;
    protected final TextView descriptionTextView;
    protected final TextView amountTextView;
    protected final View menuView;

    private ScheduledAction scheduledAction;

    public ScheduledViewHolder(@NonNull ListItemScheduledTrxnBinding binding, @NonNull Refreshable refreshable) {
        super(binding.getRoot());
        this.binding = binding;
        this.refreshable = refreshable;
        primaryTextView = binding.primaryText;
        descriptionTextView = binding.secondaryText;
        amountTextView = binding.rightText;
        menuView = binding.optionsMenu;

        menuView.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.setOnMenuItemClickListener(ScheduledViewHolder.this);
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.schedxactions_context_menu, popup.getMenu());
            popup.show();
        });
    }

    protected void bind(@NonNull ScheduledAction scheduledAction) {
        this.scheduledAction = scheduledAction;
    }

    @Nullable
    protected String formatSchedule(@Nullable ScheduledAction scheduledAction) {
        if (scheduledAction == null) return null;

        Context context = itemView.getContext();
        long lastTime = scheduledAction.getLastRunTime();
        if (lastTime > 0) {
            long endTime = scheduledAction.getEndTime();
            final String period;
            if (endTime > 0 && endTime < System.currentTimeMillis()) {
                period = context.getString(R.string.label_scheduled_action_ended);
            } else {
                period = scheduledAction.getRepeatString(context);
            }
            return context.getString(R.string.label_scheduled_action,
                    period,
                    DateExtKt.formatMediumDateTime(lastTime));
        }
        return scheduledAction.getRepeatString(context);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.menu_delete) {
            if (scheduledAction != null) {
                final Activity activity = ActivityExtKt.findActivity(itemView.getContext());
                BackupManager.backupActiveBookAsync(activity, result -> {
                    deleteSchedule(scheduledAction);
                    refreshable.refresh();
                    return null;
                });
                return true;
            }
            return false;
        }
        return false;
    }

    protected abstract void deleteSchedule(@NonNull ScheduledAction scheduledAction);
}
