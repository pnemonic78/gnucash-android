/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.transaction;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentScheduledEventsListBinding;
import org.gnucash.android.ui.common.Refreshable;

/**
 * Fragment which displays the scheduled actions in the system
 * <p>Currently, it handles the display of scheduled transactions and scheduled exports</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public abstract class ScheduledActionsListFragment extends MenuFragment implements Refreshable {

    private ScheduledAdapter<?> listAdapter;

    protected FragmentScheduledEventsListBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentScheduledEventsListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        binding.list.setEmptyView(binding.empty);
        binding.list.setLayoutManager(new LinearLayoutManager(view.getContext()));
        binding.list.setAdapter(listAdapter);
    }

    @Override
    public void refresh() {
        if (isDetached() || getFragmentManager() == null) return;
        listAdapter.loadAsync();
    }

    @Override
    public void refresh(String uid) {
        refresh();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listAdapter = createAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroy() {
        getLoaderManager().destroyLoader(0);
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            refresh();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected abstract ScheduledAdapter<?> createAdapter();
}

