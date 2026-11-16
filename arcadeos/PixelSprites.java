package arcadeos;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Generates custom 8-bit / pixel art sprites using Java 2D Graphics.
 */
public class PixelSprites {

    /** Mascot Robot Icon (green winking box robot) */
    public static BufferedImage createRobotSprite(int size) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color green = new Color(57, 255, 140);
        Color greenDark = new Color(20, 180, 90);
        Color eyeBlack = new Color(10, 8, 20);
        Color blush = new Color(255, 100, 160);

        // Antenna
        g.setColor(greenDark);
        g.fillRect(14, 2, 4, 4);

        // Body
        g.setColor(green);
        g.fillRect(6, 6, 20, 18);
        g.setColor(greenDark);
        g.drawRect(6, 6, 19, 17);

        // Eyes: left normal eye, right winking or smile
        g.setColor(eyeBlack);
        g.fillRect(9, 12, 3, 3); // Left eye
        // Right winking eye: > shape
        g.fillRect(19, 12, 3, 1);
        g.fillRect(18, 13, 2, 1);
        g.fillRect(19, 14, 3, 1);

        // Cheeks blush
        g.setColor(blush);
        g.fillRect(8, 16, 2, 2);
        g.fillRect(21, 16, 2, 2);

        // Mouth (cute smile)
        g.setColor(eyeBlack);
        g.fillRect(14, 17, 3, 1);

        // Feet
        g.setColor(greenDark);
        g.fillRect(9, 24, 4, 4);
        g.fillRect(18, 24, 4, 4);

        g.dispose();

        if (size != 32) {
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(img, 0, 0, size, size, null);
            g2.dispose();
            return scaled;
        }
        return img;
    }

    /** 8-bit Space Invader / Alien sprite */
    public static BufferedImage createAlienSprite(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);

        int[][] pixels = {
                { 3, 1 }, { 12, 1 },
                { 4, 2 }, { 11, 2 },
                { 3, 3 }, { 4, 3 }, { 5, 3 }, { 6, 3 }, { 7, 3 }, { 8, 3 }, { 9, 3 }, { 10, 3 }, { 11, 3 }, { 12, 3 },
                { 2, 4 }, { 3, 4 }, { 5, 4 }, { 6, 4 }, { 7, 4 }, { 8, 4 }, { 9, 4 }, { 10, 4 }, { 12, 4 }, { 13, 4 },
                { 1, 5 }, { 2, 5 }, { 3, 5 }, { 4, 5 }, { 5, 5 }, { 6, 5 }, { 7, 5 }, { 8, 5 }, { 9, 5 }, { 10, 5 },
                { 11, 5 }, { 12, 5 }, { 13, 5 }, { 14, 5 },
                { 1, 6 }, { 3, 6 }, { 4, 6 }, { 5, 6 }, { 6, 6 }, { 7, 6 }, { 8, 6 }, { 9, 6 }, { 10, 6 }, { 11, 6 },
                { 12, 6 }, { 14, 6 },
                { 1, 7 }, { 3, 7 }, { 12, 7 }, { 14, 7 },
                { 4, 8 }, { 5, 8 }, { 10, 8 }, { 11, 8 },
                { 3, 9 }, { 4, 9 }, { 11, 9 }, { 12, 9 }
        };
        for (int[] p : pixels) {
            g.fillRect(p[0], p[1], 1, 1);
        }
        g.dispose();
        return scale(img, size);
    }

    /** Cute Ghost Sprite */
    public static BufferedImage createGhostSprite(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);

        // Body
        g.fillRect(4, 2, 8, 2);
        g.fillRect(2, 4, 12, 7);
        // Frills
        g.fillRect(2, 11, 2, 2);
        g.fillRect(6, 11, 4, 2);
        g.fillRect(12, 11, 2, 2);

        // Eyes
        g.setColor(Color.WHITE);
        g.fillRect(4, 5, 3, 3);
        g.fillRect(9, 5, 3, 3);
        g.setColor(Color.BLUE);
        g.fillRect(5, 6, 2, 2);
        g.fillRect(10, 6, 2, 2);

        g.dispose();
        return scale(img, size);
    }

    /** UFO / Spaceship */
    public static BufferedImage createUfoSprite(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Dome
        g.setColor(new Color(5, 217, 232));
        g.fillRect(6, 3, 4, 3);

        // Saucer
        g.setColor(c);
        g.fillRect(3, 6, 10, 3);
        g.fillRect(1, 8, 14, 2);

        // Lights
        g.setColor(Color.YELLOW);
        g.fillRect(3, 9, 2, 1);
        g.fillRect(7, 9, 2, 1);
        g.fillRect(11, 9, 2, 1);

        g.dispose();
        return scale(img, size);
    }

    /** Banker Can / Safe */
    public static BufferedImage createCanSprite(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(c);
        g.fillRect(4, 3, 8, 10);
        g.setColor(Color.WHITE);
        g.fillRect(5, 4, 6, 2);
        g.setColor(new Color(255, 211, 25));
        g.fillRect(6, 7, 4, 4);

        g.dispose();
        return scale(img, size);
    }

    /** Shield Icon */
    public static BufferedImage createShieldSprite(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(c);
        g.fillRect(3, 2, 10, 6);
        g.fillRect(4, 8, 8, 3);
        g.fillRect(6, 11, 4, 2);
        g.fillRect(7, 13, 2, 1);

        g.setColor(new Color(150, 230, 255));
        g.fillRect(5, 4, 3, 4);

        g.dispose();
        return scale(img, size);
    }

    /** Brick Wall Sprite */
    public static BufferedImage createBrickSprite(int size) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(new Color(210, 75, 40));
        g.fillRect(2, 2, 12, 12);

        g.setColor(new Color(255, 140, 90));
        g.fillRect(2, 2, 5, 3);
        g.fillRect(8, 2, 6, 3);
        g.fillRect(2, 6, 12, 1);
        g.fillRect(5, 7, 6, 3);
        g.fillRect(2, 11, 12, 1);

        g.dispose();
        return scale(img, size);
    }

    /** Disk / Platter */
    public static BufferedImage createDiskSprite(int size) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(new Color(220, 220, 240));
        g.fillOval(2, 2, 12, 12);
        g.setColor(new Color(50, 40, 80));
        g.fillOval(6, 6, 4, 4);

        g.dispose();
        return scale(img, size);
    }

    /** Lightning Bolt Sprite */
    public static BufferedImage createBoltSprite(int size) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(new Color(255, 211, 25));
        int[] x = { 9, 5, 8, 4, 12, 8 };
        int[] y = { 2, 7, 7, 14, 6, 6 };
        g.fillPolygon(x, y, 6);

        g.dispose();
        return scale(img, size);
    }

    /** Process / Alien Avatar by Index (0..6) */
    public static BufferedImage getJobAvatar(int idx, int size) {
        Color[] colors = {
                new Color(180, 70, 255), // purple alien
                new Color(5, 217, 232), // cyan ship
                new Color(255, 140, 200), // pink ghost
                new Color(255, 70, 70), // red crab
                new Color(255, 40, 100), // cherry
                new Color(255, 180, 50), // orange
                new Color(100, 255, 150) // green demon
        };
        Color c = colors[idx % colors.length];
        switch (idx % 4) {
            case 0:
                return createAlienSprite(size, c);
            case 1:
                return createUfoSprite(size, c);
            case 2:
                return createGhostSprite(size, c);
            default:
                return createShieldSprite(size, c);
        }
    }

    /** Little player character (yellow hair, pink shirt, lavender pants) */
    public static BufferedImage createPlayerSprite(int size) {
        BufferedImage img = new BufferedImage(16, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Head / Yellow hair
        g.setColor(new Color(255, 220, 30));
        g.fillRect(5, 2, 6, 6);

        // Eyes (black)
        g.setColor(Color.BLACK);
        g.fillRect(6, 4, 1, 2);
        g.fillRect(9, 4, 1, 2);

        // Pink Shirt
        g.setColor(new Color(255, 42, 141));
        g.fillRect(4, 8, 8, 6);

        // Pants / Lavender
        g.setColor(new Color(160, 140, 230));
        g.fillRect(5, 14, 3, 4);
        g.fillRect(8, 14, 3, 4);

        g.dispose();
        return scale(img, size);
    }

    /** 8-bit Cyber Frog / Hopper Packet Sprite */
    public static BufferedImage createFrogSprite(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        
        // Eyes
        g.setColor(c);
        g.fillRect(2, 1, 3, 3);
        g.fillRect(11, 1, 3, 3);
        // Pupils
        g.setColor(Color.WHITE);
        g.fillRect(3, 2, 1, 1);
        g.fillRect(12, 2, 1, 1);
        // Head & Body
        g.setColor(c);
        g.fillRect(4, 3, 8, 3);
        g.fillRect(3, 6, 10, 5);
        g.fillRect(4, 11, 8, 2);
        // Front feet
        g.fillRect(1, 4, 2, 3);
        g.fillRect(13, 4, 2, 3);
        // Back legs & feet
        g.fillRect(1, 9, 2, 4);
        g.fillRect(13, 9, 2, 4);
        g.fillRect(0, 13, 3, 2);
        g.fillRect(13, 13, 3, 2);
        // Neon cyber chest core
        g.setColor(new Color(5, 217, 232));
        g.fillRect(7, 7, 2, 3);

        g.dispose();
        return scale(img, size);
    }

    /** 8-bit IC Microchip / Interrupt Hazard Sprite */
    public static BufferedImage createChipHazardSprite(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Chip body
        g.setColor(c);
        g.fillRect(3, 3, 10, 10);
        // IC Pins on left & right
        g.setColor(new Color(220, 220, 240));
        g.fillRect(1, 4, 2, 1);
        g.fillRect(1, 7, 2, 1);
        g.fillRect(1, 10, 2, 1);
        g.fillRect(13, 4, 2, 1);
        g.fillRect(13, 7, 2, 1);
        g.fillRect(13, 10, 2, 1);
        // Pin 1 indicator dot
        g.setColor(Color.YELLOW);
        g.fillRect(4, 4, 2, 2);

        g.dispose();
        return scale(img, size);
    }

    /** 16-bit User Profile Icon (Pixel Art) */
    public static BufferedImage createUserIcon(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        // Head
        g.fillRect(6, 2, 4, 4);
        // Neck
        g.fillRect(7, 6, 2, 2);
        // Shoulders / Torso
        g.fillRect(3, 8, 10, 6);
        g.dispose();
        return scale(img, size);
    }

    /** 16-bit Trophy / Leaderboard Icon (Pixel Art) */
    public static BufferedImage createTrophyIcon(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        // Cup Rim & Body
        g.fillRect(4, 2, 8, 2);
        g.fillRect(5, 4, 6, 4);
        g.fillRect(6, 8, 4, 2);
        // Handles
        g.fillRect(2, 3, 2, 4);
        g.fillRect(12, 3, 2, 4);
        // Stem
        g.fillRect(7, 10, 2, 3);
        // Base
        g.fillRect(4, 13, 8, 2);
        g.dispose();
        return scale(img, size);
    }

    /** 16-bit Power / Logout Icon (Pixel Art) */
    public static BufferedImage createPowerIcon(int size, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        // Outer circular ring with top opening
        g.fillRect(4, 3, 2, 8);
        g.fillRect(10, 3, 2, 8);
        g.fillRect(4, 11, 8, 2);
        // Center vertical switch line
        g.setColor(Color.WHITE);
        g.fillRect(7, 2, 2, 6);
        g.dispose();
        return scale(img, size);
    }

    /** Voltage Glitch / Lightning Hazard Sprite */
    public static BufferedImage createGlitchHazardSprite(int width, int height, Color c) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        // Jagged lightning pulse
        int midY = height / 2;
        int[] xs = { 2, width / 3, width / 2 - 2, 2 * width / 3, width - 4, 2 * width / 3 - 2, width / 2 + 2, width / 3 - 2 };
        int[] ys = { midY, 2, midY + 2, 2, midY, height - 3, midY - 2, height - 3 };
        g.fillPolygon(xs, ys, 8);
        // White core highlight
        g.setColor(Color.WHITE);
        g.fillRect(width / 3, midY - 1, width / 3, 3);
        g.dispose();
        return img;
    }

    /** Heavy DMA / I-O Transport Truck Hazard Sprite */
    public static BufferedImage createTruckHazardSprite(int width, int height, Color c) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        // Main trailer body
        g.fillRect(2, 3, width - 14, height - 8);
        // Cab front
        g.setColor(new Color(255, 120, 60));
        g.fillRect(width - 12, 6, 10, height - 11);
        // Windshield
        g.setColor(new Color(180, 240, 255));
        g.fillRect(width - 8, 7, 5, 4);
        // Wheels
        g.setColor(new Color(220, 220, 230));
        g.fillRect(6, height - 4, 6, 4);
        g.fillRect(width - 12, height - 4, 6, 4);
        g.dispose();
        return img;
    }

    /** 8 Arcade Module Tab Icons (Clean Pixel Art) */
    public static BufferedImage getTabIcon(int idx, int size) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        switch (idx) {
            case 0: // 01 CPU: Chip with pins
                g.setColor(new Color(255, 211, 25));
                g.fillRect(3, 3, 10, 10);
                g.setColor(new Color(40, 26, 70));
                g.fillRect(5, 5, 6, 6);
                g.setColor(Color.WHITE);
                g.fillRect(1, 4, 2, 2); g.fillRect(1, 10, 2, 2);
                g.fillRect(13, 4, 2, 2); g.fillRect(13, 10, 2, 2);
                break;
            case 1: // 02 BANKER: Coin / Safe
                g.setColor(new Color(255, 211, 25));
                g.fillOval(3, 2, 10, 12);
                g.setColor(new Color(180, 140, 10));
                g.drawOval(3, 2, 9, 11);
                g.setColor(Color.BLACK);
                g.fillRect(7, 5, 2, 6);
                break;
            case 2: // 03 PAGING: Memory Map / Page Slots
                g.setColor(new Color(5, 217, 232));
                g.fillRect(2, 2, 12, 12);
                g.setColor(new Color(10, 14, 28));
                g.fillRect(4, 4, 3, 3); g.fillRect(9, 4, 3, 3);
                g.fillRect(4, 9, 3, 3); g.fillRect(9, 9, 3, 3);
                break;
            case 3: // 04 CACHE: Circular Swap Arrows
                g.setColor(new Color(180, 70, 255));
                g.drawArc(2, 2, 11, 11, 45, 270);
                g.fillRect(8, 1, 3, 3); g.fillRect(11, 11, 3, 3);
                break;
            case 4: // 05 SEGMENT: Segment brackets
                g.setColor(new Color(255, 42, 141));
                g.fillRect(2, 2, 4, 2); g.fillRect(2, 2, 2, 12); g.fillRect(2, 12, 4, 2);
                g.fillRect(10, 2, 4, 2); g.fillRect(12, 2, 2, 12); g.fillRect(10, 12, 4, 2);
                break;
            case 5: // 06 RAM: Memory Partition Bricks
                g.setColor(new Color(57, 255, 20));
                g.fillRect(2, 2, 12, 5);
                g.fillRect(2, 9, 12, 5);
                g.setColor(Color.BLACK);
                g.drawLine(7, 2, 7, 7); g.drawLine(9, 9, 9, 14);
                break;
            case 6: // 07 DISK: Platter disk
                g.setColor(new Color(220, 220, 240));
                g.fillOval(2, 2, 12, 12);
                g.setColor(new Color(40, 30, 70));
                g.fillOval(6, 6, 4, 4);
                break;
            case 7: // 08 MULTIPROG: Lightning concurrency
            default:
                g.setColor(new Color(255, 211, 25));
                int[] px = { 9, 5, 8, 4, 12, 8 };
                int[] py = { 2, 7, 7, 14, 6, 6 };
                g.fillPolygon(px, py, 6);
                break;
        }
        g.dispose();
        return scale(img, size);
    }

    private static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage dest = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return dest;
    }
}
