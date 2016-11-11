/**
 * Copyright
 */
package fr.nelaupe.unicrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.callback.BitmapLoadCallback;
import com.yalantis.ucrop.model.CropParameters;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.model.ImageState;
import com.yalantis.ucrop.task.BitmapCropTask;
import com.yalantis.ucrop.util.BitmapLoadUtils;

import java.io.File;

import rx.AsyncEmitter;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import static android.content.ContentValues.TAG;
import static com.yalantis.ucrop.util.FileUtils.getPath;

/**
 * Created with IntelliJ
 * Created by lucas
 * Date 26/03/15
 */
public final class CropTask {

    public static Observable<File> crop(final Context context, final CropKitParams params, final CropImageView info) {
        return decodeExif(context, params.inputUri, params.outputUri).flatMap(new Func1<ExifInfo, Observable<File>>() {
            @Override public Observable<File> call(ExifInfo exifInfo) {
                final Drawable drawable = info.getDrawable();

                float w = drawable.getIntrinsicWidth();
                float h = drawable.getIntrinsicHeight();

                Log.d(TAG, String.format("Image size: [%d:%d]", (int) w, (int) h));

                RectF initialImageRect = new RectF(0, 0, w, h);
                return doCrop(context, params, info, initialImageRect, exifInfo);
            }
        });
    }

    public static Observable<Bitmap> decode(final Context context, final Uri imageUri) {
        return Observable.fromEmitter(new Action1<AsyncEmitter<Bitmap>>() {
            @Override public void call(final AsyncEmitter<Bitmap> decodeAsyncEmitter) {
                int maxBitmapSize = BitmapLoadUtils.calculateMaxBitmapSize(context);
                BitmapLoadUtils.decodeBitmapInBackground(context, imageUri, imageUri, maxBitmapSize, maxBitmapSize, new BitmapLoadCallback() {
                    @Override
                    public void onBitmapLoaded(@NonNull Bitmap bitmap, @NonNull ExifInfo exifInfo, @NonNull String imageInputPath, @Nullable String imageOutputPath) {
                        decodeAsyncEmitter.onNext(bitmap);
                        decodeAsyncEmitter.onCompleted();
                    }

                    @Override
                    public void onFailure(@NonNull Exception bitmapWorkerException) {
                        decodeAsyncEmitter.onError(bitmapWorkerException);
                    }
                });
            }
        },AsyncEmitter.BackpressureMode.NONE);
    }

    private static Observable<ExifInfo> decodeExif(final Context context, final Uri imageUri, final Uri outputUri) {
        return Observable.fromEmitter(new Action1<AsyncEmitter<ExifInfo>>() {
            @Override public void call(final AsyncEmitter<ExifInfo> exifInfoAsyncEmitter) {
                final int maxBitmapSize = BitmapLoadUtils.calculateMaxBitmapSize(context);
                BitmapLoadUtils.decodeBitmapInBackground(context, imageUri, outputUri, maxBitmapSize, maxBitmapSize, new BitmapLoadCallback() {
                    @Override public void onBitmapLoaded(@NonNull Bitmap bitmap, @NonNull ExifInfo exifInfo, @NonNull String imageInputPath, @Nullable String imageOutputPath) {
                        exifInfoAsyncEmitter.onNext(exifInfo);
                        exifInfoAsyncEmitter.onCompleted();
                    }

                    @Override public void onFailure(@NonNull Exception bitmapWorkerException) {
                        exifInfoAsyncEmitter.onError(bitmapWorkerException);
                    }
                });
            }
        },AsyncEmitter.BackpressureMode.NONE);
    }

    private static Observable<File> doCrop(final Context context, final CropKitParams params, final CropImageView info, final RectF currentImageRect, final ExifInfo exifInfo) {
        return Observable.fromEmitter(new Action1<AsyncEmitter<File>>() {
            @Override
            public void call(final AsyncEmitter<File> bitmapAsyncEmitter) {
                final ImageState imageState = new ImageState(info.getSelectedCropArea(), currentImageRect, 1, 0);
                final CropParameters cropParameters = new CropParameters(params.maxResultImageWidth, params.maxResultImageHeight, params.format, 90, getPath(context, params.inputUri), getPath(context, params.outputUri), exifInfo);
                new BitmapCropTask(context, info.getBaseBitmap(), imageState, cropParameters, new BitmapCropCallback() {
                    @Override public void onBitmapCropped(@NonNull Uri resultUri, int imageWidth, int imageHeight) {
                        bitmapAsyncEmitter.onNext(getFile(context, params.outputUri));
                        bitmapAsyncEmitter.onCompleted();
                    }

                    @Override public void onCropFailure(@NonNull Throwable t) {
                        bitmapAsyncEmitter.onError(t);
                    }
                }).execute();
            }
        }, AsyncEmitter.BackpressureMode.NONE);
    }

    public static File getFile(Context context, Uri uri) {
        if (uri != null) {
            String path = getPath(context, uri);
            if (path != null && isLocal(path)) {
                return new File(path);
            }
        }
        return null;
    }

    public static boolean isLocal(String url) {
        return url != null && !url.startsWith("http://") && !url.startsWith("https://");
    }

}
