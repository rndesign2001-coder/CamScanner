package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * WebView chop etish adapterini to'g'ridan-to'g'ri faylga yozish (tizim oynasisiz).
 * LayoutResultCallback/WriteResultCallback konstruktorlari faqat shu paket ichida ochiq,
 * shuning uchun klass android.print paketida joylashgan (keng tarqalgan usul).
 */
public class PdfPrint {
    public interface Done { void done(boolean ok); }

    private final PrintAttributes attributes;

    public PdfPrint(PrintAttributes attributes) { this.attributes = attributes; }

    public void print(final PrintDocumentAdapter adapter, final File out, final Done done) {
        adapter.onStart();
        adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                final ParcelFileDescriptor fd;
                try {
                    fd = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
                } catch (Exception e) {
                    done.done(false);
                    return;
                }
                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, fd, new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        close(fd);
                        adapter.onFinish();
                        done.done(out.length() > 0);
                    }

                    @Override
                    public void onWriteFailed(CharSequence error) {
                        close(fd);
                        adapter.onFinish();
                        done.done(false);
                    }

                    @Override
                    public void onWriteCancelled() {
                        close(fd);
                        adapter.onFinish();
                        done.done(false);
                    }
                });
            }

            @Override
            public void onLayoutFailed(CharSequence error) { done.done(false); }

            @Override
            public void onLayoutCancelled() { done.done(false); }
        }, null);
    }

    private static void close(ParcelFileDescriptor fd) {
        try { fd.close(); } catch (Exception ignored) { }
    }
}
