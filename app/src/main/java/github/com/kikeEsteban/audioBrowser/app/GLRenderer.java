package github.com.kikeEsteban.audioBrowser.app;


import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.Matrix;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// TODO: Draw text
// TODO: Draw waveform

public class GLRenderer implements Renderer {

    // Our matrices
    private final float[] mtrxProjection = new float[16];
    private final float[] mtrxView = new float[16];
    private final float[] mtrxProjectionAndView = new float[16];

    // Geometric variables
    public static float vertices[];
    public static short indices[];
    public FloatBuffer vertexBuffer;
    public ShortBuffer drawListBuffer;

    // Our screenresolution
    float   mScreenWidth = 1280;
    float   mScreenHeight = 768;

    // Misc
    Context mContext;
    long mLastTime;
    int mProgram;

    public Rect image;
    int mNumofSamples = 10;

    public GLRenderer(Context c)
    {
        mContext = c;
        mLastTime = System.currentTimeMillis() + 100;
    }

    public void onPause()
    {
        /* Do stuff to pause the renderer */
    }

    public void onResume()
    {
        /* Do stuff to resume the renderer */
        mLastTime = System.currentTimeMillis();
    }

    // TODO: Update wavePosition
    @Override
    public void onDrawFrame(GL10 unused) {

        // Get the current time
        long now = System.currentTimeMillis();

        // We should make sure we are valid and sane
        if (mLastTime > now) return;

        // Get the amount of time the last frame took.
        long elapsed = now - mLastTime;
        // Update our example
        // Get the half of screen value
  /*      int screenhalf = (int) (mScreenWidth / 2);

        mNumofSamples = (int)mScreenWidth;
      //  synchronized (vertices){
            vertices = new float[mNumofSamples*3];
            for(int i = 0; i <vertices.length - 3; i += 3){
                vertices[i] = i/3;
                vertices[i+1] = mScreenHeight + screenhalf * (float)(Math.random()-0.5)*2;
                vertices[i+2] = 0.0f;
            }

            ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);
      //  }

        indices = new short[mNumofSamples];// {0, 1, 2, 3, 4, 5}; // loop in the android official tutorial opengles why different order.
        for(int i = 0 ; i < indices.length; i++){
            indices[i] = (short)(i+1);
        }
*/

        // Buffer initialization
        if(vertices==null){
            mNumofSamples = (int)mScreenWidth*2;
            vertices = new float[mNumofSamples*3];
            for(int i = 0; i <vertices.length - 3; i += 3){
                vertices[i] = i/3;
                vertices[i+1] = mScreenHeight;
                vertices[i+2] = 0.0f;
            }
            indices = new short[mNumofSamples];// {0, 1, 2, 3, 4, 5}; // loop in the android official tutorial opengles why different order.
            for(int i = 0 ; i < indices.length; i++){
                indices[i] = (short)(i+1);
            }
        }

        updateData();

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);
        // Render our example
        Render(mtrxProjectionAndView);

        // Save the current time to see how long it took <img src="http://androidblog.reindustries.com/wp-includes/images/smilies/icon_smile.gif" alt=":)" class="wp-smiley"> .
        mLastTime = now;
    }

    private void Render(float[] m) {
        // clear Screen and Depth Buffer, we have set the clear color as black.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // get handle to vertex shader's vPosition member
        int mPositionHandle = GLES20.glGetAttribLocation(riGraphicTools.sp_SolidColor, "vPosition");

        // Enable generic vertex attribute array
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(mPositionHandle, 3,
                GLES20.GL_FLOAT, false,
                0, vertexBuffer);

        // Get handle to shape's transformation matrix
        int mtrxhandle = GLES20.glGetUniformLocation(riGraphicTools.sp_SolidColor, "uMVPMatrix");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, m, 0);

        // Draw the triangle
       // GLES20.glDrawElements(GLES20.GL_LINE_STRIP, indices.length,
       //         GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, indices.length-1);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);

    }

    // TODO: Initialization of vertices
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

        // We need to know the current width and height.
        mScreenWidth = width;
        mScreenHeight = height;

        vertices=null;

        // Redo the Viewport, making it fullscreen.
        GLES20.glViewport(0, 0, (int)mScreenWidth, (int)mScreenHeight);

        // Clear our matrices
        for(int i=0;i<16;i++)
        {
            mtrxProjection[i] = 0.0f;
            mtrxView[i] = 0.0f;
            mtrxProjectionAndView[i] = 0.0f;
        }

        // Setup our screen width and height for normal sprite translation.
        Matrix.orthoM(mtrxProjection, 0, 0f, mScreenWidth, 0.0f, mScreenHeight, 0, 50);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mtrxView, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mtrxProjectionAndView, 0, mtrxProjection, 0, mtrxView, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        // Set the clear color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1);

        // Create the shaders
        int vertexShader = riGraphicTools.loadShader(GLES20.GL_VERTEX_SHADER, riGraphicTools.vs_SolidColor);
        int fragmentShader = riGraphicTools.loadShader(GLES20.GL_FRAGMENT_SHADER, riGraphicTools.fs_SolidColor);

        riGraphicTools.sp_SolidColor = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(riGraphicTools.sp_SolidColor, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(riGraphicTools.sp_SolidColor, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(riGraphicTools.sp_SolidColor);                  // creates OpenGL ES program executables

        // Set our shader programm
        GLES20.glUseProgram(riGraphicTools.sp_SolidColor);
    }

    // TODO: Calculate position and Zoom and update vertices
    public void processTouchEvent(MotionEvent event)
    {
        // Get the half of screen value
        int screenhalf = (int) (mScreenWidth / 2);
        if(event.getX()<screenhalf)
        {
            image.left -= 10;
            image.right -= 10;
        }
        else
        {
            image.left += 10;
            image.right += 10;
        }

        // Update the new data.
        //  TranslateSprite();
    }


    // TODO: Update waveform data

    static float[] mVerticesSynched;
    static short[] mIndicesSynched;
    static boolean dataChanged = false;

    private static final Object countLock = new Object();

    public synchronized void updateData(){
        if(dataChanged && mVerticesSynched != null){
            dataChanged = false;

            synchronized (countLock) {
                for (int i = 0; i < mVerticesSynched.length; i++)
                    if(i<vertices.length)
                        vertices[i]=mVerticesSynched[i];
                for (int i = 0; i < mIndicesSynched.length; i++) {
                    if(i<indices.length)
                        indices[i]=mIndicesSynched[i];
                }
            }
        }
    }


    public synchronized void setData(float[] newHeights, int start) {
        synchronized (countLock) {
            dataChanged = true;
            mVerticesSynched = new float[2*3*newHeights.length];
            mIndicesSynched = new short[2*newHeights.length];

            for(int i = 0; i < newHeights.length; i++){
                mVerticesSynched[6*i] = i;
                mVerticesSynched[6*i+1] = newHeights[i]+mScreenHeight/2;
                mVerticesSynched[6*i+2] = 0.0f;
                mVerticesSynched[6*i+3] = i;
                mVerticesSynched[6*i+4] = mScreenHeight/2-newHeights[i];
                mVerticesSynched[6*i+5] = 0.0f;
            }

            for(int i = 0; i < mIndicesSynched.length; i++){
                mIndicesSynched[i]=(short)i;
            }
        }
    }

}

