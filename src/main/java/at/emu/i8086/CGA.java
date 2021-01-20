package at.emu.i8086;

import java.util.concurrent.atomic.AtomicInteger;

public class CGA extends Cpu.PortHandler
{
    /*
     * Based on Motorola 6845 CRT Controller.
     *
     * Mode Set Register
     * Display Buffer at 0xB8000 / 16K
     * Character Generator
     *  - 8K
     *  - cannot be read or written
     *  - 3 character fonts, selected by jumper, single dot/double dot
     *
     * Alphanumeric Mode
     *  - 2 bytes scheme: character and attribute (BRGBIRGB, I - highlighted, B - blinking)
     *  - low-resolution 40x25, 8x8 character boxes, 2 fonts (jumper) 7x5 single dot and 7x7 double dot
     *  - high-resolution 80x25, 8x8 character boxes, the same fonts
     *  - colors    R G B I
     *              0 0 0 0   black
     *              0 0 1 0   blue
     *              0 1 0 0   green
     *              0 1 1 0   cyan
     *              1 0 0 0   red
     *              1 0 1 0   magenta
     *              1 1 0 0   brown
     *              1 1 1 0   white
     *              0 0 0 0   grey
     *              0 0 1 0   light blue
     *              0 1 0 0   light green
     *              0 1 1 0   light cyan
     *              1 0 0 0   light read
     *              1 0 1 0   light magenta
     *              1 1 0 0   yellow
     *              1 1 1 0   white (high intensity)
     *
     * Graphics Modes
     *  A. low resolution (not supported in rom)
     *      - 160x100,
     *      - 16 colors
     *  B. medium,
     *      - 320x200
     *      - 4 colors table (1 of 16 selectable bg and green/read/brown or cyan/magenta/white) [01, 10, 11],
     *      - palette preloaded via IO port
     *      - each byte contains 4 display PELs [c1 c0|c1 c0|c1 c0|c1 c0],
     *      - 2 banks in memory:
     *          - B8000..B9F3F ~ 8000 bytes, even scans
     *          - BA000..BBF3F ~ 8000 bytes, odd scans
     *                               
     *  C. high
     *      - 640x200
     *      - 2 colors (black and white)
     *      - each byte contains 8 pels
     */

    /*
     * Ports
     *  3D8     - mode control register (D0), write only
     *              [x x 5 4 3 2 1 0]
     *                                 [0]: 0 ->> 40x25, 1 ->> 80x25
     *                                 [1]: 0 ->> A/N,   1 ->> 320x200
     *                                 [2]: 0 ->> color mode, 1 ->> b/w mode
     *                                 [3]: 1 ->> enable video signal (disabled when changing modes)
     *                                 [4]: 1 ->> selects 640x200 b/w mode (1 of 8 colors could be selected via 3D9)
     *                                 [5]: 0 ->> no blinking, 1 ->> character bg intensity as blinking attribute
     *                  summary
     *                  [5 4 3 2 1 0]
     *                   1 0 1 1 0 0   40x25 A/N b/w
     *                   1 0 1 0 0 0   40x25 A/N color
     *                   1 0 1 1 0 1   80x25 A/N b/w
     *                   1 0 1 0 0 1   80x25 A/N color
     *                   x 0 1 1 1 0   320x200 b/w
     *                   x 0 1 0 1 0   320x200 color
     *                   x 1 1 1 1 0   640x200 b/w
     *
     *                   * 160x100 set up as 40x25 and requires  special programming
     */
    public static final int MASK_MODE_TEXT_80   = 0x01;
    public static final int MASK_MODE_GRAPHICS  = 0x02;
    public static final int MASK_MODE_BW        = 0x04;
    public static final int MASK_MODE_ENABLE    = 0x08;
    public static final int MASK_MODE_640       = 0x10;
    public static final int MASK_MODE_BLINK     = 0x20;

    /*
     *  3D9     - color select (D0), write only
     *              [x x 5 4 3 2 1 0]
     *                                 [0]: selects (blue border in 40x25 | blue bg color in 320x200 | blue fg color in 640x200)
     *                                 [1]: selects (green --""--)
     *                                 [2]: selects (red --""--)
     *                                 [3]: selects intensified (border ... | bg  ... | fg ... )
     *                                 [4]: selects (alternate intensified set of colors in gr mode | bg color in A/N mode)
     *                                 [5]: selects active color set in 320x200 (0 ->> green/red/brown, 1 ->> c/m/w)
     */
    public static final int MASK_COLOR_BLUE     = 0x01;
    public static final int MASK_COLOR_GREEN    = 0x02;
    public static final int MASK_COLOR_RED      = 0x04;
    public static final int MASK_COLOR_INTENSE  = 0x08;
    public static final int MASK_COLOR_ALT      = 0x10;
    public static final int MASK_COLOR_320      = 0x20;

    /*
     *  3DA     - Status (D1), read only
     *              [x x x x 3 2 1 0]
     *                                 [0]: 1 ->> regen-buffer memory access can be made without interfering with display (no snow)
     *                                 [1]: 1 ->> positive-going edge from light pen has set light pen's trigger.
     *                                              trigger is reset on power on and may be cleared on outp[3DB]
     *                                 [2]: reflects light pen switch, 0 ->> switch is on
     *                                 [3]: 1 ->> raster is in a vertical retrace mode, screen buffer updating can be performed
     */
    public static final int MASK_STATUS_REGEN   = 0x01;
    public static final int MASK_STATUS_LP_PGE  = 0x02;
    public static final int MASK_STATUS_LP_SW   = 0x04;
    public static final int MASK_STATUS_VERTICAL= 0x08;

    /*
     *  3DB     - Clear Light Pen Latch
     *  3DC     - Preset Light Pen Latch
     *  3D4     - 6845 Index Register, the same ports [11_1101_0xx0]
     *  3D5     - 6845 Data Register,  the same ports [11_1101_0xx1]
     *
     * 6845 CRT Controller
     *  - contains 19 registers
     *  - index register is used to point to other (18) registers, [write only, 3D4, 5 low bits are loaded into index]
     *  - data register [3D5, writes data to *indexed* register]
     *
     *  Address   Register  Register                                             40x25   80x25 Graphic
     * Register     Number  Type                        Units           I/0        A/N     A/N   Modes
     *      0           RO  Horizontal Total            Character       Write       38      71      38     e4
     *      1           R1  Horizontal Displayed        Character       Write       28      50      28     60
     *      2           R2  Horizontal Sync Position    Character       Write       2D      5A      2D     8a
     *      3           R3  Horizontal Sync Width       Character       Write       OA      OA      OA     d8
     *      4           R4  Vertical Total              Character row   Write       1F      1F      7F     b0
     *      5           R5  Vertical Total Adjust       Scan Line       Write       06      06      06     c8
     *      6           R6  Vertical Displayed          Character row   Write       19      19      64     e6
     *      7           R7  Vertical Sync Position      Character row   Write       1C      1C      70     61
     *      8           R8  Interlace Mode              -               Write       02      02      02     c3
     *      9           R9  Maximum Scan Line Address   Scan Line       Write       07      07      01     50
     *      A           R10 Cursor Start                Scan Write      Write       06      06      06      b8
     *      B           R11 Cursor End                  Scan Line       Write       07      07      07      40
     *      C           R12 Start Address (H)           -               Write       00      00      00      0
     *      D           R13 Start Address (L)           -               Write       00      00      00      8e
     *      E           R14 Cursor Address (H)          -               R/W         XX      XX      XX      d8
     *      F           R15 Cursor Address (L)          -               R/W         XX      XX      XX      58
     *     10           R16 Light Pen (H)               -               Read        XX      XX      XX      29
     *     11           R17 Light Pen (L)               -               Read        XX      XX      XX      30
     */
    public static final int REG_MODE        = 0x3D8;
    public static final int REG_COLOR_SEL   = 0x3D9;
    public static final int REG_STATUS      = 0x3DA;
    public static final int REG_CLEAR_LPL   = 0x3DB;
    public static final int REG_PRESET_LPL  = 0x3DC;
    public static final int REG_6845_INDEX  = 0x3D4;
    public static final int REG_6845_DATA   = 0x3D5;

    public static final int REG_6845_MASK   = 0b0011_1111_1001;
    public static final int REG_6845_INDEXM = 0x3D0;
    public static final int REG_6845_DATAM  = 0x3D1;


    /*
     * Mode Changing Sequence
     * 1. determine the mode of operation
     * 2. reset video-enable bit in the mode-control reg
     * 3. program 6845 to select mode
     * 4. program the mode-control and color-select registers including re-enabling the video
     *
     * Full CGA 16-color palette
     * 0 	black   * #000000 	8 	dark gray    * #555555
     * 1 	blue    * #0000AA 	9 	light blue   * #5555FF
     * 2 	green   * #00AA00 	10 	light green  * #55FF55
     * 3 	cyan    * #00AAAA 	11 	light cyan   * #55FFFF
     * 4 	red     * #AA0000 	12 	light red    * #FF5555
     * 5 	magenta * #AA00AA 	13 	light magenta* #FF55FF
     * 6 	brown   * #AA5500 	14 	yellow       * #FFFF55
     * 7 	light gray * #AAAAAA 	15 	white    * #FFFFFF
     *
     * H-freq 	15699.8 Hz (14.318181 MHz/8/114)
     * V-freq 	   59.923 Hz (H-freq/262)
     */


    volatile int regMode;
    volatile int regColor;
    AtomicInteger regStatus = new AtomicInteger();
    int reg6845Index;

    int[] reg6845 = new int[18];

    void write6845Data(int value) {
        System.out.println("6845 [" + reg6845Index + "] = " + Integer.toHexString(value));
        reg6845[reg6845Index] = value;
    }

    int read6845Data() {
        return 0;
    }

    @Override
    void pout(boolean word, int port, int value)
    {
        if (port == REG_MODE)
        {
            System.out.println("CGA [" + Integer.toHexString(port) + "] = " + Integer.toHexString(value));
            regMode = value;
            statusCallCounter = 0;
        }
        else if (port == REG_COLOR_SEL)
        {
            System.out.println("CGA [" + Integer.toHexString(port) + "] = " + Integer.toHexString(value));
            regColor = value;
        }
        else if (port == REG_6845_INDEX)
        {
            reg6845Index = value;
        }
        else if (port == REG_6845_DATA)
        {
            write6845Data(value);
        }
        else {
            System.out.println("CGA: unhandled write [" + port + "] = " + Integer.toHexString(value));
        }
    }

    int statusCallCounter;

    @Override
    int pin(boolean word, int port)
    {
        if (port == REG_STATUS) {
            //System.out.println("CGA: unhandled status read");

            // after mode set bios checks for
            // bit 3 on, bit 3 off
            // bit 0 on, bit 3 off
            // hack that for starting 4 calls???
            if (statusCallCounter < 4) {
                int bit = (statusCallCounter + 1) & 0x01;
                statusCallCounter++;
                return (bit << 3) | bit;
            } else {
                return 0x09; // retrace
                //return regStatus.get();
            }
        }
        else if (port == REG_6845_DATA)
        {
            return read6845Data();
        }
        else {
            System.out.println("CGA: unhandled read [" + port + "]");
        }
        return 0;
    }

    Drawer drawer;
    public void start() {
        //drawer = new Drawer();
        //drawer.start();
    }

    class Drawer extends Thread {
        @Override
        public void run()
        {
            while (true) {
                try {
                    Thread.sleep(16);
                } catch (Exception e) {
                }
/*
 *                                 [0]: 1 ->> regen-buffer memory access can be made without interfering with display (no snow)
 *                                 [1]: 1 ->> positive-going edge from light pen has set light pen's trigger.
 *                                              trigger is reset on power on and may be cleared on outp[3DB]
 *                                 [2]: reflects light pen switch, 0 ->> switch is on
 *                                 [3]: 1 ->> raster is in a vertical retrace mode, screen buffer updating can be performed

 */
                boolean updated = false;
                do {
                    int tmp = regStatus.get();
                    updated = regStatus.compareAndSet(tmp, tmp & 0xF6);
                } while (!updated);

                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                }

                do {
                    int tmp = regStatus.get();
                    updated = regStatus.compareAndSet(tmp, tmp | 0x09);
                } while (!updated);
                System.out.println(Integer.toHexString(regStatus.get()));
            }


        }
    }


}
