/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.printspooler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintManager;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintServiceInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a fragment for selecting a printer.
 */
public final class SelectPrinterFragment extends ListFragment {

    private static final String LOG_TAG = "SelectPrinterFragment";

    private static final int LOADER_ID_PRINTERS_LOADER = 1;

    private static final String FRAGMRNT_TAG_ADD_PRINTER_DIALOG =
            "FRAGMRNT_TAG_ADD_PRINTER_DIALOG";

    private static final String FRAGMRNT_ARGUMENT_PRINT_SERVICE_INFOS =
            "FRAGMRNT_ARGUMENT_PRINT_SERVICE_INFOS";

    private final ArrayList<PrintServiceInfo> mAddPrinterServices =
            new ArrayList<PrintServiceInfo>();

    public static interface OnPrinterSelectedListener {
        public void onPrinterSelected(PrinterId printerId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(new DestinationAdapter());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.select_printer_activity, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String searchString) {
                ((DestinationAdapter) getListAdapter()).getFilter().filter(searchString);
                return true;
            }
        });

        if (mAddPrinterServices.isEmpty()) {
            menu.removeItem(R.id.action_add_printer);
        }
    }

    @Override
    public void onResume() {
        updateAddPrintersAdapter();
        getActivity().invalidateOptionsMenu();
        super.onResume();
    }

    @Override
    public void onListItemClick(ListView list, View view, int position, long id) {
        PrinterInfo printer = (PrinterInfo) list.getAdapter().getItem(position);
        Activity activity = getActivity();
        if (activity instanceof OnPrinterSelectedListener) {
            ((OnPrinterSelectedListener) activity).onPrinterSelected(printer.getId());
        } else {
            throw new IllegalStateException("the host activity must implement"
                    + " OnPrinterSelectedListener");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_printer) {
            showAddPrinterSelectionDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateAddPrintersAdapter() {
        mAddPrinterServices.clear();

        // Get all enabled print services.
        PrintManager printManager = (PrintManager) getActivity()
                .getSystemService(Context.PRINT_SERVICE);
        List<PrintServiceInfo> enabledServices = printManager.getEnabledPrintServices();

        // No enabled print services - done.
        if (enabledServices.isEmpty()) {
            return;
        }

        // Find the services with valid add printers activities.
        final int enabledServiceCount = enabledServices.size();
        for (int i = 0; i < enabledServiceCount; i++) {
            PrintServiceInfo enabledService = enabledServices.get(i);

            // No add printers activity declared - done.
            if (TextUtils.isEmpty(enabledService.getAddPrintersActivityName())) {
                continue;
            }

            ServiceInfo serviceInfo = enabledService.getResolveInfo().serviceInfo;
            ComponentName addPrintersComponentName = new ComponentName(
                    serviceInfo.packageName, enabledService.getAddPrintersActivityName());
            Intent addPritnersIntent = new Intent()
                .setComponent(addPrintersComponentName);

            // The add printers activity is valid - add it.
            PackageManager pm = getActivity().getPackageManager();
            List<ResolveInfo> resolvedActivities = pm.queryIntentActivities(addPritnersIntent, 0);
            if (!resolvedActivities.isEmpty()) {
                // The activity is a component name, therefore it is one or none.
                ActivityInfo activityInfo = resolvedActivities.get(0).activityInfo;
                if (activityInfo.exported
                        && (activityInfo.permission == null
                                || pm.checkPermission(activityInfo.permission,
                                        getActivity().getPackageName())
                                        == PackageManager.PERMISSION_GRANTED)) {
                    mAddPrinterServices.add(enabledService);
                }
            }
        }
    }

    private void showAddPrinterSelectionDialog() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment oldFragment = getFragmentManager().findFragmentByTag(
                FRAGMRNT_TAG_ADD_PRINTER_DIALOG);
        if (oldFragment != null) {
            transaction.remove(oldFragment);
        }
        AddPrinterAlertDialogFragment newFragment = new AddPrinterAlertDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putParcelableArrayList(FRAGMRNT_ARGUMENT_PRINT_SERVICE_INFOS,
                mAddPrinterServices);
        newFragment.setArguments(arguments);
        transaction.add(newFragment, FRAGMRNT_TAG_ADD_PRINTER_DIALOG);
        transaction.commit();
    }

    public static class AddPrinterAlertDialogFragment extends DialogFragment {

        private static final String DEFAULT_MARKET_QUERY_STRING =
                "market://search?q=print";

        @Override
        @SuppressWarnings("unchecked")
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.choose_print_service);

            final List<PrintServiceInfo> printServices = (List<PrintServiceInfo>) (List<?>)
                    getArguments().getParcelableArrayList(FRAGMRNT_ARGUMENT_PRINT_SERVICE_INFOS);

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                    android.R.layout.simple_list_item_1);
            final int printServiceCount = printServices.size();
            for (int i = 0; i < printServiceCount; i++) {
                PrintServiceInfo printService = printServices.get(i);
                adapter.add(printService.getResolveInfo().loadLabel(
                        getActivity().getPackageManager()).toString());
            }

            builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    PrintServiceInfo printService = printServices.get(which);
                    ComponentName componentName = new ComponentName(
                            printService.getResolveInfo().serviceInfo.packageName,
                            printService.getAddPrintersActivityName());
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setComponent(componentName);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException anfe) {
                        Log.w(LOG_TAG, "Couldn't start settings activity", anfe);
                    }
                }
            });

            Uri marketUri = Uri.parse(DEFAULT_MARKET_QUERY_STRING);
            final Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
            if (getActivity().getPackageManager().resolveActivity(marketIntent, 0) != null) {
                builder.setPositiveButton(R.string.search_play_store,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            try {
                                startActivity(marketIntent);
                            } catch (ActivityNotFoundException anfe) {
                                Log.w(LOG_TAG, "Couldn't start add printer activity", anfe);
                            }
                        }
                    });
            }

            return builder.create();
        }
    }

    private final class DestinationAdapter extends BaseAdapter
            implements LoaderManager.LoaderCallbacks<List<PrinterInfo>>, Filterable {

        private final Object mLock = new Object();

        private final List<PrinterInfo> mPrinters = new ArrayList<PrinterInfo>();

        private final List<PrinterInfo> mFilteredPrinters = new ArrayList<PrinterInfo>();

        private CharSequence mLastSearchString;

        public DestinationAdapter() {
            getLoaderManager().initLoader(LOADER_ID_PRINTERS_LOADER, null, this);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    synchronized (mLock) {
                        if (TextUtils.isEmpty(constraint)) {
                            return null;
                        }
                        FilterResults results = new FilterResults();
                        List<PrinterInfo> filteredPrinters = new ArrayList<PrinterInfo>();
                        String constraintLowerCase = constraint.toString().toLowerCase();
                        final int printerCount = mPrinters.size();
                        for (int i = 0; i < printerCount; i++) {
                            PrinterInfo printer = mPrinters.get(i);
                            if (printer.getName().toLowerCase().contains(constraintLowerCase)) {
                                filteredPrinters.add(printer);
                            }
                        }
                        results.values = filteredPrinters;
                        results.count = filteredPrinters.size();
                        return results;
                    }
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    synchronized (mLock) {
                        mLastSearchString = constraint;
                        mFilteredPrinters.clear();
                        if (results == null) {
                            mFilteredPrinters.addAll(mPrinters);
                        } else {
                            List<PrinterInfo> printers = (List<PrinterInfo>) results.values;
                            mFilteredPrinters.addAll(printers);
                        }
                    }
                    notifyDataSetChanged();
                }
            };
        }

        @Override
        public int getCount() {
            synchronized (mLock) {
                return mFilteredPrinters.size();
            }
        }

        @Override
        public Object getItem(int position) {
            synchronized (mLock) {
                return mFilteredPrinters.get(position);
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getDropDownView(int position, View convertView,
                ViewGroup parent) {
            return getView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(
                        R.layout.spinner_dropdown_item, parent, false);
            }

            CharSequence title = null;
            CharSequence subtitle = null;

            PrinterInfo printer = (PrinterInfo) getItem(position);
            title = printer.getName();
            try {
                PackageManager pm = getActivity().getPackageManager();
                PackageInfo packageInfo = pm.getPackageInfo(printer.getId()
                        .getServiceName().getPackageName(), 0);
                subtitle = packageInfo.applicationInfo.loadLabel(pm);
            } catch (NameNotFoundException nnfe) {
                /* ignore */
            }

            TextView titleView = (TextView) convertView.findViewById(R.id.title);
            titleView.setText(title);

            TextView subtitleView = (TextView) convertView.findViewById(R.id.subtitle);
            if (!TextUtils.isEmpty(subtitle)) {
                subtitleView.setText(subtitle);
                subtitleView.setVisibility(View.VISIBLE);
            } else {
                subtitleView.setText(null);
                subtitleView.setVisibility(View.GONE);
            }

            return convertView;
        }

        @Override
        public Loader<List<PrinterInfo>> onCreateLoader(int id, Bundle args) {
            if (id == LOADER_ID_PRINTERS_LOADER) {
                return new FusedPrintersProvider(getActivity());
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<List<PrinterInfo>> loader,
                List<PrinterInfo> printers) {
            synchronized (mLock) {
                mPrinters.clear();
                mPrinters.addAll(printers);
                mFilteredPrinters.clear();
                mFilteredPrinters.addAll(printers);
                if (!TextUtils.isEmpty(mLastSearchString)) {
                    getFilter().filter(mLastSearchString);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public void onLoaderReset(Loader<List<PrinterInfo>> loader) {
            synchronized (mLock) {
                mPrinters.clear();
                mFilteredPrinters.clear();
            }
            notifyDataSetInvalidated();
        }
    }
}