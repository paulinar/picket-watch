package com.example.mari.picketwatch;

/* Followed tutorial: http://code.tutsplus.com/tutorials/android-sdk-create-a-drawing-app-touch-interaction--mobile-19202 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

public class DrawingView extends View {

    //drawing path
    private Path drawPath;

    //drawing and canvas are both Paint objects
    private Paint drawPaint, canvasPaint;

    //initial color
    private int paintColor = 0xFF660000;

    //canvas
    private Canvas drawCanvas;

    //canvas bitmap
    private Bitmap canvasBitmap;

    private float brushSize, lastBrushSize;
    private boolean erase=false;

    /* The user paths drawn with drawPaint will be drawn onto the canvas, which is drawn with canvasPaint. */

    // constructor method
    // we'll add an instance of the custom View to the XML layout file
    public DrawingView(Context context, AttributeSet attrs){
        super(context, attrs);
        setupDrawing();
    }

    // helper method to get drawing area setup for interaction
    private void setupDrawing(){

        brushSize = getResources().getInteger(R.integer.medium_size);
        lastBrushSize = brushSize;

        drawPath = new Path();
        drawPaint = new Paint();

        // initial color
        drawPaint.setColor(paintColor);

        // initial path properties (make drawings look smoother)
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(brushSize); // used to be hardcoded
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        // instantiate canvas Paint object
        canvasPaint = new Paint(Paint.DITHER_FLAG);

    }

    /* Size view */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        super.onSizeChanged(w, h, oldw, oldh);

        // instantiate drawing canvas and bitmap using width & height values
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
    }

    /* Draw view */
    @Override
    protected void onDraw(Canvas canvas) {

        // draw the canvas & drawing path
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }

    /* listen for touch events */
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: // user touches the view
                drawPath.moveTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_MOVE:
                drawPath.lineTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_UP: // user lifts finger off view
                drawCanvas.drawPath(drawPath, drawPaint);
                drawPath.reset();
                break;
            default:
                return false;
        }

        // each time user draws using touch interaction, invalidate the View to cause onDrawmethod to execute
        // i.e. re-renders entire view
        invalidate();
        return true;
    }

    public void setColor(String newColor){
        invalidate();

        // parse and set color for drawing
        paintColor = Color.parseColor(newColor);
        drawPaint.setColor(paintColor);
    }

    public void setBrushSize(float newSize){
        float pixelAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                newSize, getResources().getDisplayMetrics());
        brushSize=pixelAmount;
        drawPaint.setStrokeWidth(brushSize); // update Paint object to use new size
    }

    public void setLastBrushSize(float lastSize){
        lastBrushSize=lastSize;
    }

    public float getLastBrushSize(){
        return lastBrushSize;
    }

    // default is user is drawing
    public void setErase(boolean isErase){
        erase=isErase;
        if (erase) drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        else drawPaint.setXfermode(null);
    }

    // clears canvas and updates display
    public void startNew(){
        drawCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        invalidate();
    }

}