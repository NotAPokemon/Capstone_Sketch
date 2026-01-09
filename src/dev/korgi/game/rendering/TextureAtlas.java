package dev.korgi.game.rendering;

import java.awt.image.BufferedImage;

public class TextureAtlas {

    private int[][][] atlas;
    private static final int height = 32;
    private static final int widthPerTexture = 192;

    public static final int MINECRAFT_GRASS_BLOCK = 0;

    public TextureAtlas(int size) {
        this.atlas = new int[size][widthPerTexture][height];
    }

    /**
     * [ ] - 32x32 square
     * 
     * Minecraft-style layout (common convention):
     *
     * [ ][T][ ][ ]
     * [W][F][E][B]
     * [ ][B][ ][ ]
     * [ ][ ][ ][ ]
     * 
     * Or
     * 
     * +X, -X, +Y, -Y, +Z, -Z
     * [W][E][T][B][F][B]
     */
    public void addTexture(int id, BufferedImage img) {
        BufferedImage src;

        if (img.getWidth() == 128 && img.getHeight() == 128) {
            src = new BufferedImage(widthPerTexture, height, BufferedImage.TYPE_INT_ARGB);

            int t = 32;

            BufferedImage[] faces = new BufferedImage[] {
                    img.getSubimage(64, 32, t, t),
                    img.getSubimage(0, 32, t, t),
                    img.getSubimage(32, 0, t, t),
                    img.getSubimage(32, 64, t, t),
                    img.getSubimage(32, 32, t, t),
                    img.getSubimage(96, 32, t, t)
            };

            for (int face = 0; face < 6; face++) {
                for (int y = 0; y < t; y++) {
                    for (int x = 0; x < t; x++) {
                        src.setRGB(face * t + x, y, faces[face].getRGB(x, y));
                    }
                }
            }
        } else {
            if (img.getWidth() != widthPerTexture || img.getHeight() != height) {
                throw new IllegalArgumentException(
                        "Texture must be 192x32 or 128x128, got "
                                + img.getWidth() + "x" + img.getHeight());
            }
            src = img;
        }

        int[][] target = atlas[id];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < widthPerTexture; x++) {
                target[x][y] = src.getRGB(x, y);
            }
        }
    }

    public int getTexture(int location, int face, int u, int v) {
        int offset = face * height;
        int[][] texture = atlas[location];
        return texture[offset + u][v];
    }

    public int size() {
        return atlas.length;
    }

    public int[] getAtlas() {
        int texSize = widthPerTexture * height;
        int[] flat = new int[atlas.length * texSize];

        int dst = 0;

        for (int t = 0; t < atlas.length; t++) {
            int[][] texture = atlas[t];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < widthPerTexture; x++) {
                    flat[dst++] = texture[x][y];
                }
            }
        }

        return flat;
    }

}
