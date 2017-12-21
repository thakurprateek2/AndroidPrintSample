package com.prateek.androidprintsample.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.pdf.PdfDocument;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by thakurprateek on 15-02-2016.
 */
public class PrintUtils {

    private static final int MILS_IN_INCH = 1000;

    public interface FetchAdapterCopy<T>{

        AdapterForPint<T> createAdapterCopy();

    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static <T> void print(final Context context, final AdapterForPint<T> listAdapter, final FetchAdapterCopy<T> fetchAdapterCopy) {
        PrintManager printManager = (PrintManager) context.getSystemService(
                Context.PRINT_SERVICE);
        printManager.print("report",
                new PrintDocumentAdapter() {
                    private int mRenderPageWidth;
                    private int mRenderPageHeight;
                    private PrintAttributes mPrintAttributes;
                    private PrintDocumentInfo mDocumentInfo;
                    private Context mPrintContext;
                    @Override
                    public void onLayout(final PrintAttributes oldAttributes,
                                         final PrintAttributes newAttributes,
                                         final CancellationSignal cancellationSignal,
                                         final LayoutResultCallback callback,
                                         final Bundle metadata) {
                        // If we are already cancelled, don't do any work.
                        if (cancellationSignal.isCanceled()) {
                            callback.onLayoutCancelled();
                            return;
                        }
                        // Now we determined if the print attributes changed in a way that
                        // would change the layout and if so we will do a layout pass.
                        boolean layoutNeeded = false;
                        final int density = Math.max(newAttributes.getResolution().getHorizontalDpi(),
                                newAttributes.getResolution().getVerticalDpi());
                        // Note that we are using the PrintedPdfDocument class which creates
                        // a PDF generating canvas whose size is in points (1/72") not screen
                        // pixels. Hence, this canvas is pretty small compared to the screen.
                        // The recommended way is to layout the content in the desired size,
                        // in this case as large as the printer can do, and set a translation
                        // to the PDF canvas to shrink in. Note that PDF is a vector format
                        // and you will not lose data during the transformation.
                        // The content width is equal to the page width minus the margins times
                        // the horizontal printer density. This way we get the maximal number
                        // of pixels the printer can put horizontally.
                        final int marginLeft = (int) (density * (float) newAttributes.getMinMargins()
                                .getLeftMils() / MILS_IN_INCH);
                        final int marginRight = (int) (density * (float) newAttributes.getMinMargins()
                                .getRightMils() / MILS_IN_INCH);
                        final int contentWidth = (int) (density * (float) newAttributes.getMediaSize()
                                .getWidthMils() / MILS_IN_INCH) - marginLeft - marginRight;
                        if (mRenderPageWidth != contentWidth) {
                            mRenderPageWidth = contentWidth;
                            layoutNeeded = true;
                        }
                        // The content height is equal to the page height minus the margins times
                        // the vertical printer resolution. This way we get the maximal number
                        // of pixels the printer can put vertically.
                        final int marginTop = (int) (density * (float) newAttributes.getMinMargins()
                                .getTopMils() / MILS_IN_INCH);
                        final int marginBottom = (int) (density * (float) newAttributes.getMinMargins()
                                .getBottomMils() / MILS_IN_INCH);
                        final int contentHeight = (int) (density * (float) newAttributes.getMediaSize()
                                .getHeightMils() / MILS_IN_INCH) - marginTop - marginBottom;
                        if (mRenderPageHeight != contentHeight) {
                            mRenderPageHeight = contentHeight;
                            layoutNeeded = true;
                        }
                        // Create a context for resources at printer density. We will
                        // be inflating views to render them and would like them to use
                        // resources for a density the printer supports.
                        if (mPrintContext == null || mPrintContext.getResources()
                                .getConfiguration().densityDpi != density) {
                            Configuration configuration = new Configuration();
                            configuration.densityDpi = density;
                            mPrintContext = context.createConfigurationContext(
                                    configuration);
                            mPrintContext.setTheme(android.R.style.Theme_Holo_Light);
                        }
                        // If no layout is needed that we did a layout at least once and
                        // the document info is not null, also the second argument is false
                        // to notify the system that the content did not change. This is
                        // important as if the system has some pages and the content didn't
                        // change the system will ask, the application to write them again.
                        if (!layoutNeeded) {
                            callback.onLayoutFinished(mDocumentInfo, false);
                            return;
                        }
                        // For demonstration purposes we will do the layout off the main
                        // thread but for small content sizes like this one it is OK to do
                        // that on the main thread.
                        // Store the data as we will layout off the main thread.
                        final List<T> items = listAdapter.cloneItems();
                        new AsyncTask<Void, Void, PrintDocumentInfo>() {
                            @Override
                            protected void onPreExecute() {
                                // First register for cancellation requests.
                                cancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
                                    @Override
                                    public void onCancel() {
                                        cancel(true);
                                    }
                                });
                                // Stash the attributes as we will need them for rendering.
                                mPrintAttributes = newAttributes;
                            }
                            @Override
                            protected PrintDocumentInfo doInBackground(Void... params) {
                                try {
                                    // Create an adapter with the stats and an inflater
                                    // to load resources for the printer density.
                                    ArrayAdapter<T> adapter = fetchAdapterCopy.createAdapterCopy();
                                    int currentPage = 0;
                                    int pageContentHeight = 0;
                                    int viewType = -1;
                                    View view = null;
                                    LinearLayout dummyParent = new LinearLayout(mPrintContext);
                                    dummyParent.setOrientation(LinearLayout.VERTICAL);
                                    final int itemCount = adapter.getCount();
                                    for (int i = 0; i < itemCount; i++) {
                                        // Be nice and respond to cancellation.
                                        if (isCancelled()) {
                                            return null;
                                        }
                                        // Get the next view.
                                        final int nextViewType = adapter.getItemViewType(i);
                                        if (viewType == nextViewType) {
                                            view = adapter.getView(i, view, dummyParent);
                                        } else {
                                            view = adapter.getView(i, null, dummyParent);
                                        }
                                        Log.d("TAG", "View is" + view);
                                        viewType = nextViewType;
                                        // Measure the next view
                                        measureView(view);
                                        // Add the height but if the view crosses the page
                                        // boundary we will put it to the next page.
                                        pageContentHeight += view.getMeasuredHeight();
                                        if (pageContentHeight > mRenderPageHeight) {
                                            pageContentHeight = view.getMeasuredHeight();
                                            currentPage++;
                                        }
                                    }
                                    // Create a document info describing the result.
                                    PrintDocumentInfo info = new PrintDocumentInfo
                                            .Builder("report.pdf")
                                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                            .setPageCount(currentPage + 1)
                                            .build();
                                    // We completed the layout as a result of print attributes
                                    // change. Hence, if we are here the content changed for
                                    // sure which is why we pass true as the second argument.
                                    callback.onLayoutFinished(info, true);
                                    return info;
                                } catch (Exception e) {
                                    // An unexpected error, report that we failed and
                                    // one may pass in a human readable localized text
                                    // for what the error is if known.
                                    callback.onLayoutFailed(null);
                                    throw new RuntimeException(e);
                                }
                            }
                            @Override
                            protected void onPostExecute(PrintDocumentInfo result) {
                                // Update the cached info to send it over if the next
                                // layout pass does not result in a content change.
                                mDocumentInfo = result;
                            }
                            @Override
                            protected void onCancelled(PrintDocumentInfo result) {
                                // Task was cancelled, report that.
                                callback.onLayoutCancelled();
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                    }
                    @Override
                    public void onWrite(final PageRange[] pages,
                                        final ParcelFileDescriptor destination,
                                        final CancellationSignal cancellationSignal,
                                        final WriteResultCallback callback) {
                        // If we are already cancelled, don't do any work.
                        if (cancellationSignal.isCanceled()) {
                            callback.onWriteCancelled();
                            return;
                        }
                        // Store the data as we will layout off the main thread.
                        final List<T> items = listAdapter.cloneItems();
                        new AsyncTask<Void, Void, Void>() {
                            private final SparseIntArray mWrittenPages = new SparseIntArray();
                            private final PrintedPdfDocument mPdfDocument = new PrintedPdfDocument(
                                    context, mPrintAttributes);
                            @Override
                            protected void onPreExecute() {
                                // First register for cancellation requests.
                                cancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
                                    @Override
                                    public void onCancel() {
                                        cancel(true);
                                    }
                                });
                            }
                            @Override
                            protected Void doInBackground(Void... params) {
                                // Go over all the pages and write only the requested ones.
                                // Create an adapter with the stats and an inflater
                                // to load resources for the printer density.
                                AdapterForPint<T> adapter = fetchAdapterCopy.createAdapterCopy();
                                int currentPage = -1;
                                int pageContentHeight = 0;
                                int viewType = -1;
                                View view = null;
                                PdfDocument.Page page = null;
                                LinearLayout dummyParent = new LinearLayout(mPrintContext);
                                dummyParent.setOrientation(LinearLayout.VERTICAL);
                                // The content is laid out and rendered in screen pixels with
                                // the width and height of the paper size times the print
                                // density but the PDF canvas size is in points which are 1/72",
                                // so we will scale down the content.
                                final float scale =  Math.min(
                                        (float) mPdfDocument.getPageContentRect().width()
                                                / mRenderPageWidth,
                                        (float) mPdfDocument.getPageContentRect().height()
                                                / mRenderPageHeight);
                                final int itemCount = adapter.getCount();
                                for (int i = 0; i < itemCount; i++) {
                                    // Be nice and respond to cancellation.
                                    if (isCancelled()) {
                                        return null;
                                    }
                                    // Get the next view.
                                    final int nextViewType = adapter.getItemViewType(i);
                                    if (viewType == nextViewType) {
                                        view = adapter.getView(i, view, dummyParent);
                                    } else {
                                        view = adapter.getView(i, null, dummyParent);
                                    }
                                    viewType = nextViewType;
                                    // Measure the next view
                                    measureView(view);
                                    // Add the height but if the view crosses the page
                                    // boundary we will put it to the next one.
                                    pageContentHeight += view.getMeasuredHeight();
                                    if (currentPage < 0 || pageContentHeight > mRenderPageHeight) {
                                        pageContentHeight = view.getMeasuredHeight();
                                        currentPage++;
                                        // Done with the current page - finish it.
                                        if (page != null) {
                                            mPdfDocument.finishPage(page);
                                        }
                                        // If the page is requested, render it.
                                        if (containsPage(pages, currentPage)) {
                                            page = mPdfDocument.startPage(currentPage);
                                            page.getCanvas().scale(scale, scale);
                                            // Keep track which pages are written.
                                            mWrittenPages.append(mWrittenPages.size(), currentPage);
                                        } else {
                                            page = null;
                                        }
                                    }
                                    // If the current view is on a requested page, render it.
                                    if (page != null) {
                                        // Layout an render the content.
                                        view.layout(0, 0, view.getMeasuredWidth(),
                                                view.getMeasuredHeight());
                                        view.draw(page.getCanvas());
                                        // Move the canvas for the next view.
                                        page.getCanvas().translate(0, view.getHeight());
                                    }
                                }
                                // Done with the last page.
                                if (page != null) {
                                    mPdfDocument.finishPage(page);
                                }
                                // Write the data and return success or failure.
                                try {
                                    mPdfDocument.writeTo(new FileOutputStream(
                                            destination.getFileDescriptor()));
                                    // Compute which page ranges were written based on
                                    // the bookkeeping we maintained.
                                    PageRange[] pageRanges = computeWrittenPageRanges(mWrittenPages);
                                    callback.onWriteFinished(pageRanges);
                                } catch (IOException ioe) {
                                    callback.onWriteFailed(null);
                                } finally {
                                    mPdfDocument.close();
                                }
                                return null;
                            }
                            @Override
                            protected void onCancelled(Void result) {
                                // Task was cancelled, report that.
                                callback.onWriteCancelled();
                                mPdfDocument.close();
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                    }
                    private void measureView(View view) {
                        final int widthMeasureSpec = ViewGroup.getChildMeasureSpec(
                                View.MeasureSpec.makeMeasureSpec(mRenderPageWidth,
                                        View.MeasureSpec.EXACTLY), 0, view.getLayoutParams().width);
                        final int heightMeasureSpec = ViewGroup.getChildMeasureSpec(
                                View.MeasureSpec.makeMeasureSpec(mRenderPageHeight,
                                        View.MeasureSpec.EXACTLY), 0, view.getLayoutParams().height);
                        view.measure(widthMeasureSpec, heightMeasureSpec);
                    }
                    private PageRange[] computeWrittenPageRanges(SparseIntArray writtenPages) {
                        List<PageRange> pageRanges = new ArrayList<PageRange>();
                        int start = -1;
                        int end = -1;
                        final int writtenPageCount = writtenPages.size();
                        for (int i = 0; i < writtenPageCount; i++) {
                            if (start < 0) {
                                start = writtenPages.valueAt(i);
                            }
                            int oldEnd = end = start;
                            while (i < writtenPageCount && (end - oldEnd) <= 1) {
                                oldEnd = end;
                                end = writtenPages.valueAt(i);
                                i++;
                            }
                            @SuppressLint("Range") PageRange pageRange = new PageRange(start, end);
                            pageRanges.add(pageRange);
                            start = end = -1;
                        }
                        PageRange[] pageRangesArray = new PageRange[pageRanges.size()];
                        pageRanges.toArray(pageRangesArray);
                        return pageRangesArray;
                    }
                    private boolean containsPage(PageRange[] pageRanges, int page) {
                        final int pageRangeCount = pageRanges.length;
                        for (int i = 0; i < pageRangeCount; i++) {
                            if (pageRanges[i].getStart() <= page
                                    && pageRanges[i].getEnd() >= page) {
                                return true;
                            }
                        }
                        return false;
                    }
                }, null);
    }

}
