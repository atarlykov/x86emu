package at.emu.i8086.simple;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

/**
 * Some kind of visualization
 */
public class Display extends JFrame {

    public void BaseGraphics()
    {

        // init window
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(getDimension());
        setVisible(true);

        // set up buffering
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        createBufferStrategy(2);
        BufferStrategy strategy = getBufferStrategy();

        // Main loop
        while (true) {

            // Prepare for rendering the next frame

            // Render single frame
            do {
                // The following loop ensures that the contents of the drawing buffer
                // are consistent in case the underlying surface was recreated
                do {
                    // Get a new graphics context every time through the loop
                    // to make sure the strategy is validated
                    Graphics graphics = strategy.getDrawGraphics();

                    // Render to graphics
                    draw(graphics);

                    // Dispose the graphics
                    graphics.dispose();

                    // Repeat the rendering if the drawing buffer contents
                    // were restored
                } while (strategy.contentsRestored());

                // Display the buffer
                strategy.show();

                // Repeat the rendering if the drawing buffer was lost
            } while (strategy.contentsLost());
        }

        // Dispose the window
        //setVisible(false);
        //dispose();
    }

    public static int BUFFER_WIDTH = 800;
    public static int BUFFER_HEIGHT = 600;

    /**
     * @return buffer dimensions for the underlying impl
     */
    protected Dimension getDimension() {
        return new Dimension(BUFFER_WIDTH, BUFFER_HEIGHT);
    }

    /**
     *
     * called to render the frame
     * @param g graphics
     */
    protected void draw(Graphics g) {

        // draw points to buffer
        // draw buffer
        BufferedImage img = new BufferedImage(colorModel, raster, false, null);
        g.drawImage(img, 0, 0, null);

        // draw fps
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.drawString("fps: "  + 1000,10,40);
    }

    /**
     * internal array for direct drawing,
     * app will draw points here
     */
    int[] buffer;

    /**
     * magic classes to perform quick buffer drawing
     */
    ColorModel colorModel;
    SampleModel sm;
    DataBuffer dBuffer;
    WritableRaster raster;
    /**
     * cached reference to font
     */
    Font font;


    /**
     * called to initialize model state,
     * sets up buffer and other 2d stuff
     */
    protected void initModelState() {

        // allocate buffer for direct drawing
        buffer = new int[BUFFER_WIDTH * BUFFER_HEIGHT];

        // init *magic* stuff
        colorModel = new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF);
        dBuffer = new DataBufferInt(buffer, buffer.length);
        sm = colorModel.createCompatibleSampleModel(BUFFER_WIDTH, BUFFER_HEIGHT);
        raster = Raster.createWritableRaster(sm, dBuffer, null);
        font = Font.decode("Arial");



/*
            GraphicsConfiguration gfxConfig = GraphicsEnvironment.
                    getLocalGraphicsEnvironment().getDefaultScreenDevice().
                    getDefaultConfiguration();

            BufferedImage newImage = gfxConfig.createCompatibleImage(
                    image.getWidth(), image.getHeight(), image.getTransparency());

            Graphics2D g2d = newImage.createGraphics();

            // actually draw the image and dispose of context no longer needed
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
*/

    }

}
