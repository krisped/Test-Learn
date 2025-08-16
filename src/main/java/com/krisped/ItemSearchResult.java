package com.krisped;

import java.awt.image.BufferedImage;
import java.util.List;

public class ItemSearchResult {
    private final int id;
    private final BufferedImage image;
    private final String name;
    private final List<String> actions;

    public ItemSearchResult(int id, BufferedImage image, String name, List<String> actions) {
        this.id = id;
        this.image = image;
        this.name = name;
        this.actions = actions;
    }

    public int getId() { return id; }
    public BufferedImage getImage() { return image; }
    public String getName() { return name; }
    public List<String> getActions() { return actions; }
}
