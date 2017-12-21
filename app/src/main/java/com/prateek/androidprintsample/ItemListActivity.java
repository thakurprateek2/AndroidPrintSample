package com.prateek.androidprintsample;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.prateek.androidprintsample.dummy.DummyContent;
import com.prateek.androidprintsample.utils.AdapterForPint;
import com.prateek.androidprintsample.utils.PrintUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ItemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class ItemListActivity extends AppCompatActivity {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

//                doPrint();
                ItemPrintAdapter itemPrintAdapter = new ItemPrintAdapter(ItemListActivity.this, R.layout.item_list_content, (ArrayList<DummyContent.DummyItem>) DummyContent.ITEMS);
                PrintUtils.print(ItemListActivity.this, itemPrintAdapter ,new PrintUtils.FetchAdapterCopy<DummyContent.DummyItem>() {
                    @Override
                    public AdapterForPint<DummyContent.DummyItem> createAdapterCopy() {
                        return new ItemPrintAdapter(ItemListActivity.this, R.layout.item_list_content, (ArrayList<DummyContent.DummyItem>) DummyContent.ITEMS);
                    }
                });
            }
        });

        if (findViewById(R.id.item_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        View recyclerView = findViewById(R.id.item_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);
    }

    public  class ItemPrintAdapter extends AdapterForPint<DummyContent.DummyItem>{

        private ArrayList<DummyContent.DummyItem> objects;

        public ItemPrintAdapter(Context context, int resource, ArrayList<DummyContent.DummyItem> objects) {
            super(context, resource, objects);
            this.objects = objects;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            Log.d("Tag", "We have parent " + parent);
            convertView = getLayoutInflater().inflate(R.layout.item_list_content, parent);
//            ((TextView)convertView.findViewById(R.id.id_text)).setText(objects.get(position).id );
//            ((TextView)convertView.findViewById(R.id.content)).setText(objects.get(position).details);

            convertView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return convertView;
        }
    }

    private void doPrint() {
        // Get a PrintManager instance
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);

        // Set job name, which will be displayed in the print queue
        String jobName = getString(R.string.app_name) + " Document";

        // Start a print job, passing in a PrintDocumentAdapter implementation
        // to handle the generation of a print document
        printManager.print(jobName, new MyPrintDocumentAdapter(this),
                null); //
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(this, DummyContent.ITEMS, mTwoPane));
    }

    public static class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final ItemListActivity mParentActivity;
        private final List<DummyContent.DummyItem> mValues;
        private final boolean mTwoPane;
        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DummyContent.DummyItem item = (DummyContent.DummyItem) view.getTag();
                if (mTwoPane) {
                    Bundle arguments = new Bundle();
                    arguments.putString(ItemDetailFragment.ARG_ITEM_ID, item.id);
                    ItemDetailFragment fragment = new ItemDetailFragment();
                    fragment.setArguments(arguments);
                    mParentActivity.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.item_detail_container, fragment)
                            .commit();
                } else {
                    Context context = view.getContext();
                    Intent intent = new Intent(context, ItemDetailActivity.class);
                    intent.putExtra(ItemDetailFragment.ARG_ITEM_ID, item.id);

                    context.startActivity(intent);
                }
            }
        };

        SimpleItemRecyclerViewAdapter(ItemListActivity parent,
                                      List<DummyContent.DummyItem> items,
                                      boolean twoPane) {
            mValues = items;
            mParentActivity = parent;
            mTwoPane = twoPane;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mIdView.setText(mValues.get(position).id);
            holder.mContentView.setText(mValues.get(position).content);

            holder.itemView.setTag(mValues.get(position));
            holder.itemView.setOnClickListener(mOnClickListener);
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView mIdView;
            final TextView mContentView;

            ViewHolder(View view) {
                super(view);
                mIdView = (TextView) view.findViewById(R.id.id_text);
                mContentView = (TextView) view.findViewById(R.id.content);
            }
        }
    }

    private class MyPrintDocumentAdapter extends PrintDocumentAdapter {
        private PrintedPdfDocument mPdfDocument;

        public MyPrintDocumentAdapter(ItemListActivity itemListActivity) {
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes,
                             PrintAttributes newAttributes,
                             CancellationSignal cancellationSignal,
                             LayoutResultCallback callback,
                             Bundle metadata) {
            // Create a new PdfDocument with the requested page attributes
            mPdfDocument = new PrintedPdfDocument(ItemListActivity.this, newAttributes);

            // Respond to cancellation request
            if (cancellationSignal.isCanceled() ) {
                callback.onLayoutCancelled();
                return;
            }

            // Compute the expected number of printed pages
            int pages = computePageCount(newAttributes);

            if (pages > 0) {
                // Return print information to print framework
                PrintDocumentInfo.Builder info = new PrintDocumentInfo
                        .Builder("print_output.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(pages);

                // Content layout reflow is complete
                callback.onLayoutFinished(info.build(), true);
            } else {
                // Otherwise report an error to the print framework
                callback.onLayoutFailed("Page count calculation failed.");
            }
        }

        @Override
        public void onWrite(final PageRange[] pageRanges,
                            final ParcelFileDescriptor destination,
                            final CancellationSignal cancellationSignal,
                            final WriteResultCallback callback) {
            // Iterate over each page of the document,
            // check if it's in the output range.
            for (int i = 0; i < 3/*totalPageCount*/; i++) {
                // Check to see if this page is in the output range.
                if (containsPage(pageRanges, i)) {
                    // If so, add it to writtenPagesArray. writtenPagesArray.size()
                    // is used to compute the next output page index.
//                    writtenPagesArray.append(writtenPagesArray.size(), i);
                    PdfDocument.Page page = mPdfDocument.startPage(i);

                    // check for cancellation
                    if (cancellationSignal.isCanceled()) {
                        callback.onWriteCancelled();
                        mPdfDocument.close();
                        mPdfDocument = null;
                        return;
                    }

                    // Draw page content for printing
                    drawPage(page);

                    // Rendering is complete, so page can be finalized.
                    mPdfDocument.finishPage(page);
                }
            }

            // Write PDF document to file
            try {
                mPdfDocument.writeTo(new FileOutputStream(
                        destination.getFileDescriptor()));
            } catch (IOException e) {
                callback.onWriteFailed(e.toString());
                return;
            } finally {
                mPdfDocument.close();
                mPdfDocument = null;
            }
            PageRange[] writtenPages = computeWrittenPages();
            // Signal the print framework the document is complete
            callback.onWriteFinished(writtenPages);


        }

        private void drawPage(PdfDocument.Page page) {
            Canvas canvas = page.getCanvas();

            // units are in points (1/72 of an inch)
            int titleBaseLine = 72;
            int leftMargin = 54;

            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(36);
            canvas.drawText("Test Title", leftMargin, titleBaseLine, paint);

            paint.setTextSize(11);
            canvas.drawText("Test paragraph", leftMargin, titleBaseLine + 25, paint);

            paint.setColor(Color.BLUE);
            canvas.drawRect(100, 100, 172, 172, paint);
        }

        private boolean containsPage(PageRange[] pageRanges, int i) {
            return true;
        }

        private int computePageCount(PrintAttributes printAttributes) {
            int itemsPerPage = 4; // default item count for portrait mode

            PrintAttributes.MediaSize pageSize = printAttributes.getMediaSize();
            if (!pageSize.isPortrait()) {
                // Six items per page in landscape orientation
                itemsPerPage = 6;
            }

            // Determine number of print items
            int printItemCount = 10;//getPrintItemCount();

            return (int) Math.ceil(printItemCount / itemsPerPage);
        }
    }

    private PageRange[] computeWrittenPages() {
        return new PageRange[0];
    }
}
