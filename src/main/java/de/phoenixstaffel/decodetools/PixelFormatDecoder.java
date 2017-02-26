package de.phoenixstaffel.decodetools;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PixelFormatDecoder {
    /**
     * Static Color offset table as defined in the OpenGL standard. See
     * {@link}https://www.khronos.org/registry/gles/extensions/OES/OES_compressed_ETC1_RGB8_texture.txt}
     */
    private static final int[][] OFFSET_TABLE = new int[][] { new int[] { -8, -2, 2, 8 }, new int[] { -17, -5, 5, 17 },
            new int[] { -29, -9, 9, 29 }, new int[] { -42, -13, 13, 42 }, new int[] { -60, -18, 18, 60 },
            new int[] { -80, -24, 24, 80 }, new int[] { -106, -33, 33, 106 }, new int[] { -183, -47, 47, 183 } };
    
    private PixelFormatDecoder() {
    }
    
    public static int[] convertFromETC1A4(byte[] input, int width, int height) {
        return calculate(input, width, height, true);
    }
    
    public static int[] convertFromETC1(byte[] input, int width, int height) {
        return calculate(input, width, height, false);
    }
    
    public static int[] calculate(byte[] input, int width, int height, boolean alpha) {
        int[] output = new int[(input.length / 8) * 16];
        
        ByteBuffer inbuff = ByteBuffer.wrap(input);
        inbuff.order(ByteOrder.LITTLE_ENDIAN);
        
        int x = 0;
        int y = 0;
        while (inbuff.hasRemaining()) {
            int[][] value00 = getPixelsForBlock(alpha ? inbuff.getLong() : 0xFFFFFFFFFFFFFFFFL, inbuff.getLong());
            int[][] value10 = getPixelsForBlock(alpha ? inbuff.getLong() : 0xFFFFFFFFFFFFFFFFL, inbuff.getLong());
            int[][] value01 = getPixelsForBlock(alpha ? inbuff.getLong() : 0xFFFFFFFFFFFFFFFFL, inbuff.getLong());
            int[][] value11 = getPixelsForBlock(alpha ? inbuff.getLong() : 0xFFFFFFFFFFFFFFFFL, inbuff.getLong());
            
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 4; j++) {
                    output[(y + 0) * 4 * width + (x + 0) * 4 + j * width + i] = value00[i][j];
                    output[(y + 1) * 4 * width + (x + 0) * 4 + j * width + i] = value01[i][j];
                    output[(y + 0) * 4 * width + (x + 1) * 4 + j * width + i] = value10[i][j];
                    output[(y + 1) * 4 * width + (x + 1) * 4 + j * width + i] = value11[i][j];
                }
            
            x += 2;
            if (x * 4 >= width) {
                x = 0;
                y += 2;
            }
        }
        
        return output;
    }
    
    private static int[][] getPixelsForBlock(long alpha, long value) {
        int[][] data = new int[4][4];
        
        boolean isDifferential = getBitValue(value, 33);
        boolean isFlip = getBitValue(value, 32);
        
        int tableCW2 = (int) getSubInteger(value, 34, 3);
        int tableCW1 = (int) getSubInteger(value, 37, 3);
        
        long r1;
        long g1;
        long b1;
        
        long r2;
        long g2;
        long b2;
        
        if (isDifferential) {
            long baseR = getSubInteger(value, 59, 5);
            long baseG = getSubInteger(value, 51, 5);
            long baseB = getSubInteger(value, 43, 5);
            
            long diffR = getSubInteger(value, 56, 3);
            long diffG = getSubInteger(value, 48, 3);
            long diffB = getSubInteger(value, 40, 3);
            
            r1 = extend5To8(baseR);
            g1 = extend5To8(baseG);
            b1 = extend5To8(baseB);
            
            r2 = extend5To8(add3BitSigned(baseR, diffR));
            g2 = extend5To8(add3BitSigned(baseG, diffG));
            b2 = extend5To8(add3BitSigned(baseB, diffB));
            
        }
        else {
            r1 = extend4To8(getSubInteger(value, 60, 4));
            g1 = extend4To8(getSubInteger(value, 52, 4));
            b1 = extend4To8(getSubInteger(value, 44, 4));
            
            r2 = extend4To8(getSubInteger(value, 56, 4));
            g2 = extend4To8(getSubInteger(value, 48, 4));
            b2 = extend4To8(getSubInteger(value, 40, 4));
        }
        
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 4; y++) {
                int id = x * 4 + y;
                
                boolean msb = getBitValue(value, 16 + id);
                boolean lsb = getBitValue(value, id);
                int offset = (!msb ? 0b10 : 0) + ((lsb ^ msb) ? 0b1 : 0);
                long a = extend4To8(getSubInteger(alpha, id * 4, 4));
                
                long r;
                long g;
                long b;
                if (isFlip && y >= 2 || !isFlip && x >= 2) {
                    r = crop(r2 + OFFSET_TABLE[tableCW2][offset], 0, 255);
                    g = crop(g2 + OFFSET_TABLE[tableCW2][offset], 0, 255);
                    b = crop(b2 + OFFSET_TABLE[tableCW2][offset], 0, 255);
                }
                else {
                    r = crop(r1 + OFFSET_TABLE[tableCW1][offset], 0, 255);
                    g = crop(g1 + OFFSET_TABLE[tableCW1][offset], 0, 255);
                    b = crop(b1 + OFFSET_TABLE[tableCW1][offset], 0, 255);
                }
                
                data[x][y] = buildRGBA8(r, g, b, a);
            }
        
        return data;
    }
    
    public static int[] convertFromRGBA8(byte[] a, int width, int height) {
        int[] data = new int[a.length / 4];
        for (int i = 0; i < data.length; i++) {
            data[i] += (Byte.toUnsignedInt(a[i * 4]) << 24);
            data[i] += (Byte.toUnsignedInt(a[i * 4 + 1]));
            data[i] += (Byte.toUnsignedInt(a[i * 4 + 2]) << 8);
            data[i] += (Byte.toUnsignedInt(a[i * 4 + 3]) << 16);
        }
        return data;
    }
    
    public static int[] convertFromRGB8(byte[] a, int width, int height) {
        int[] data = new int[a.length / 3];
        for (int i = 0; i < data.length; i++) {
            data[i] += 255 << 24;
            data[i] += (Byte.toUnsignedInt(a[i * 3]));
            data[i] += (Byte.toUnsignedInt(a[i * 3 + 1]) << 8);
            data[i] += (Byte.toUnsignedInt(a[i * 3 + 2]) << 16);
        }
        return data;
    }
    
    public static int[] convertFromRGBA5551(byte[] a, int width, int height) {
        int[] data = new int[a.length / 2];
        for (int i = 0; i < data.length; i++) {
            int value = (Byte.toUnsignedInt(a[i * 2 + 1]) << 8) + Byte.toUnsignedInt(a[i * 2]);
            data[i] += ((value & 0x01) != 0 ? 255 : 0) << 24;
            data[i] += PixelFormatDecoder.extend5To8((value >>> 1) & 0x1F);
            data[i] += PixelFormatDecoder.extend5To8((value >>> 6) & 0x1F) << 8;
            data[i] += PixelFormatDecoder.extend5To8((value >>> 11) & 0x1F) << 16;
        }
        return data;
    }
    
    public static int[] convertFromRGBA4(byte[] a, int width, int height) {
        int[] data = new int[a.length / 2];
        for (int i = 0; i < data.length; i++) {
            int value = (Byte.toUnsignedInt(a[i * 2 + 1]) << 8) + Byte.toUnsignedInt(a[i * 2]);
            data[i] += PixelFormatDecoder.extend4To8(value & 0xF) << 24;
            data[i] += PixelFormatDecoder.extend4To8((value >>> 4) & 0x1F);
            data[i] += PixelFormatDecoder.extend4To8((value >>> 8) & 0xF) << 8;
            data[i] += PixelFormatDecoder.extend4To8((value >>> 12) & 0xF) << 16;
        }
        return data;
    }
    
    public static int[] convertFromRGB565(byte[] a, int width, int height) {
        int[] data = new int[a.length / 2];
        for (int i = 0; i < data.length; i++) {
            int value = (Byte.toUnsignedInt(a[i * 2 + 1]) << 8) + Byte.toUnsignedInt(a[i * 2]);
            data[i] += 255 << 24;
            data[i] += PixelFormatDecoder.extend5To8(value & 0x1F);
            data[i] += PixelFormatDecoder.extend6To8((value & 0x7E0) >>> 5) << 8;
            data[i] += PixelFormatDecoder.extend5To8((value & 0xF800) >>> 11) << 16;
        }
        return data;
    }
    
    public static int[] convertFromLA4(byte[] a, int width, int height) {
        int[] data = new int[a.length];
        
        for (int i = 0; i < data.length; i++) {
            data[i] += PixelFormatDecoder.extend4To8(a[i] & 0xF) << 24;
            data[i] += PixelFormatDecoder.extend4To8((a[i] >>> 4) & 0xF);
            data[i] += PixelFormatDecoder.extend4To8((a[i] >>> 4) & 0xF) << 8;
            data[i] += PixelFormatDecoder.extend4To8((a[i] >>> 4) & 0xF) << 16;
        }
        
        return data;
    }
    
    public static int[] convertFromLA8(byte[] a, int width, int height) {
        int[] data = new int[a.length / 2];
        
        for (int i = 0; i < data.length; i++) {
            data[i] += (Byte.toUnsignedInt(a[i * 2 + 1])) << 24;
            data[i] += (Byte.toUnsignedInt(a[i * 2]));
            data[i] += (Byte.toUnsignedInt(a[i * 2])) << 8;
            data[i] += (Byte.toUnsignedInt(a[i * 2])) << 16;
        }
        
        return data;
    }
    
    public static int[] convertFromA8(byte[] a, int width, int height) {
        int[] data = new int[a.length];
        
        for (int i = 0; i < data.length; i++) {
            data[i] += Byte.toUnsignedInt(a[i]) << 24;
            data[i] += 255;
            data[i] += 255 << 8;
            data[i] += 255 << 16;
        }
        
        return data;
    }
    
    public static int[] convertFromL8(byte[] a, int width, int height) {
        int[] data = new int[a.length];
        
        for (int i = 0; i < data.length; i++) {
            data[i] += 255 << 24;
            data[i] += Byte.toUnsignedInt(a[i]);
            data[i] += Byte.toUnsignedInt(a[i]) << 8;
            data[i] += Byte.toUnsignedInt(a[i]) << 16;
        }
        
        return data;
    }
    
    public static int[] convertFromL4(byte[] a, int width, int height) {
        int[] data = new int[a.length * 2];
        
        for (int i = 0; i < a.length; i++) {
            data[i * 2] += 255 << 24;
            data[i * 2] += PixelFormatDecoder.extend4To8(a[i] >>> 4);
            data[i * 2] += PixelFormatDecoder.extend4To8(a[i] >>> 4) << 8;
            data[i * 2] += PixelFormatDecoder.extend4To8(a[i] >>> 4) << 16;
            
            data[i * 2 + 1] += 255 << 24;
            data[i * 2 + 1] += PixelFormatDecoder.extend4To8(a[i] & 0xF);
            data[i * 2 + 1] += PixelFormatDecoder.extend4To8(a[i] & 0xF) << 8;
            data[i * 2 + 1] += PixelFormatDecoder.extend4To8(a[i] & 0xF) << 16;
        }
        
        return data;
    }
    
    public static int[] convertFromA4(byte[] a, int width, int height) {
        int[] data = new int[a.length * 2];
        
        for (int i = 0; i < a.length; i++) {
            data[i * 2] += PixelFormatDecoder.extend4To8(a[i] >>> 4) << 24;
            data[i * 2] += 255;
            data[i * 2] += 255 << 8;
            data[i * 2] += 255 << 16;
            
            data[i * 2 + 1] += PixelFormatDecoder.extend4To8(a[i] & 0xF) << 24;
            data[i * 2 + 1] += 255;
            data[i * 2 + 1] += 255 << 8;
            data[i * 2 + 1] += 255 << 16;
        }
        
        return data;
    }
    
    private static int buildRGBA8(long r, long g, long b, long a) {
        int data = 0;
        data += (a << 24);
        data += (r << 16);
        data += (g << 8);
        data += b;
        
        return data & 0xFFFFFFFF;
    }
    
    public static long crop(long value, long min, long max) {
        return Math.min(Math.max(min, value), max);
    }
    
    public static long add3BitSigned(long base, long toAdd) {
        if (toAdd < 0 || toAdd > 7)
            throw new IllegalArgumentException("Second argument must be between 0 and 7 (inclusive), but was " + toAdd);
        
        if ((toAdd & 0x4) == 0)
            return base + toAdd;
        
        return base - ((~toAdd & 0x3) + 1);
    }
    
    public static boolean getBitValue(long value, int bit) {
        if (bit >= Long.SIZE || bit < 0)
            throw new IllegalArgumentException("Can't get the " + bit + " bit of a 64bit number.");
        
        return (value >>> bit & 0x1) != 0;
    }
    
    public static long getSubInteger(long value, int bit, int length) {
        if (bit < 0 || length <= 0 || Long.SIZE - bit < length)
            throw new IllegalArgumentException("Can't get bits " + bit + " to " + (bit + length) + " of a long int.");
        
        return (value << Long.SIZE - length - bit) >>> (Long.SIZE - length);
    }
    
    public static long extend4To8(long value) {
        long tmp = value & 0xF;
        
        return (tmp << 4) + tmp;
    }
    
    public static long extend5To8(long value) {
        long tmp = value & 0x1F;
        
        return (tmp << 3) + (tmp >>> 2);
        
    }
    
    public static long extend6To8(long value) {
        long tmp = value & 0x3F;
        
        return (tmp << 2) + (tmp >>> 4);
    }
    
}