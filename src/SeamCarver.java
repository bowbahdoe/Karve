/*
 * SeamCarver
 * Alex Eidt
 * Implements the SeamCarver class.
 */

import java.util.*;

/*
 * The SeamCarver class implements the Seam Carving algorithm and allows the
 * user to easily add/remove seams.
 */
public class SeamCarver {

    // Stores the indices of the seams that were removed from the image.
    private Stack<List<Integer>> seams;
    // Stores the values of the seams that were removed from the image.
    // The indices of the values in these lists corresponds to those in "seams".
    private Stack<List<Integer>> values;
    // Stores the values of the seams that were removed from the internal gradient image.
    private Stack<List<Byte>> edgeValues;
    // The current image as a flattened array.
    private int[] data;
    // The actual image.
    private List<List<Integer>> image;
    // The energy map used to quickly compute new seams.
    private int[][] map;
    // The gradient of the input image (done via sobel).
    private List<List<Byte>> edges;
    // Height of the image.
    private int height;
    // Width of the image.
    private int width;

    /*
     *
     */
    public SeamCarver(int[][] image) {
        this.height = image.length;
        this.width = image[0].length;
        this.seams = new Stack<>();
        this.values = new Stack<>();
        this.edgeValues = new Stack<>();
        this.image = new ArrayList<>(this.height);
        this.edges = Utils.sobel(image);
        this.data = new int[this.height * this.width];

        energyMap(); // Create Energy Map.
        // Copy the "image" into "this.image" and "this.data".
        int index = 0;
        for (int h = 0; h < this.height; h++) {
            List<Integer> imageRow = new ArrayList<>(this.width);
            for (int w = 0; w < this.width; w++) {
                imageRow.add(image[h][w]);
                this.data[index++] = image[h][w];
            }
            this.image.add(imageRow);
        }
    }

    // Returns the height of the image.
    public int getHeight() {
        return this.height;
    }

    // Returns the width of the image.
    public int getWidth() {
        return this.width;
    }

    // Returns the current state of the image as a flattened array.
    public int[] getImage() {
        return this.data;
    }

    /*
     * Adds the most recently removed seam back onto the image.
     *
     * @param highlight If true, highlight the added seam.
     * @param color     The color of the highlighted seam.
     * @return          true if seam could be added, false otherwise.
     */
    public boolean add(boolean highlight, int color) {
        if (this.seams.isEmpty()) {
            return false;
        }
        List<Integer> path = this.seams.pop();
        List<Integer> values = this.values.pop();
        List<Byte> edges = this.edgeValues.pop();

        // Go through all indices of the most recently removed
        // seam and add the corresponding values back into the
        // images.
        for (int i = 0; i < path.size(); i++) {
            this.image.get(i).add(path.get(i), values.get(i));
            this.edges.get(i).add(path.get(i), edges.get(i));
        }
        this.width += 1;
        if (highlight) {
            updateImage(path, color);
        } else {
            updateImage();
        }
        energyMap();
        return true;
    }

    /*
     * Removes the next seam from the image.
     *
     * @param highlight If true, highlight the added seam.
     * @param color     The color of the highlighted seam.
     * @return          true if seam could be removed, false otherwise.
     */
    public boolean remove(boolean highlight, int color) {
        if (this.width == 2) {
            return false;
        }
        List<Integer> path = new ArrayList<>(this.height);
        List<Integer> values = new ArrayList<>(this.height);
        List<Byte> edgeValues = new ArrayList<>(this.height);

        // Find the minimum value in the first row of the energy map.
        int minIndex = Utils.minIndex(this.map[0]);
        path.add(minIndex);
        values.add(this.image.get(0).remove(minIndex));
        edgeValues.add(this.edges.get(0).remove(minIndex));
        // After finding the minimum value in the first row of the energy
        // map, move through all rows of the image to find a seam. Seams must
        // be connected, therefore only the three pixels directly below the current
        // index will be considered as the next part of the seam.
        for (int h = 1; h < this.height; h++) {
            int[] row = this.map[h];
            if (minIndex == 0) {
                minIndex = Utils.min(row[0], row[1]) == row[0] ? 0 : 1;
            } else if (minIndex == this.width - 1) {
                int minValue = Utils.min(row[this.width - 2], row[this.width - 1]);
                minIndex = row[this.width - 2] == minValue ? this.width - 2 : this.width - 1;
            } else {
                int minValue = Utils.min(row[minIndex - 1], row[minIndex], row[minIndex + 1]);
                if (row[minIndex - 1] == minValue) minIndex = minIndex - 1;
                else if (row[minIndex + 1] == minValue) minIndex = minIndex + 1;
            }
            path.add(minIndex);
            values.add(this.image.get(h).remove(minIndex));
            edgeValues.add(this.edges.get(h).remove(minIndex));
        }
        this.width -= 1;
        if (highlight) {
            updateImage(path, color);
        } else {
            updateImage();
        }
        energyMap();
        this.seams.push(path);
        this.values.push(values);
        this.edgeValues.push(edgeValues);
        return true;
    }

    /*
     * Sets the edge to the maximum value at the given coordinates.
     * 
     * @param x     X Coordinate in columns of image.
     * @param y     Y Coordinate in rows of image.
     */
    public void setEdge(int x, int y) {
        this.edges.get(y).set(x, (byte) 255);
    }

    /*
     * Creates the energy map from the gradient image.
     * Learn more: https://www.youtube.com/watch?v=rpB6zQNsbQU
     */
    private void energyMap() {
        // Create Energy Map to find least paths through the image.
        int[][] map = new int[this.height][this.width];
        // Copy last row of image into energy map.
        for (int w = 0; w < this.width; w++) {
            map[this.height - 1][w] = this.edges.get(this.height - 1).get(w);
        }
        // Create energy map.
        for (int h = this.height - 2; h >= 0; h--) {
            List<Byte> row = this.edges.get(h);
            map[h][0] = row.get(0) + Utils.min(map[h + 1][0], map[h + 1][1]);
            int w;
            for (w = 1; w < this.width - 1; w++) {
                map[h][w] = row.get(w) + Utils.min(map[h + 1][w - 1], map[h + 1][w], map[h + 1][w + 1]);
            }
            map[h][w] = row.get(w) + Utils.min(map[h + 1][w - 1], map[h + 1][w]);
        }
        this.map = map;
    }

    // Updates the current flattened image to match the current state of the image.
    private void updateImage() {
        int[] data = new int[this.height * this.width];
        int index = 0;
        for (int h = 0; h < this.height; h++) {
            List<Integer> row = this.image.get(h);
            for (int w = 0; w < this.width; w++) {
                data[index++] = row.get(w);
            }
        }
        this.data = data;
    }

    /*
     * Updates the current flattened image to match the current state of the image.
     * Adds the highlighted seam to the flattened image as well.
     *
     * @param path      List of indices of each seam value.
     * @param color     The seam color to use.
     */
    private void updateImage(List<Integer> path, int color) {
        int[] data = new int[this.height * this.width];
        int index = 0;
        for (int h = 0; h < this.height; h++) {
            List<Integer> row = this.image.get(h);
            for (int w = 0; w < this.width; w++) {
                data[index++] = row.get(w);
            }
            int pathIndex = path.get(h);
            for (int i = pathIndex - 1; i <= pathIndex + 1; i++) {
                if (i < 0 || i >= this.width) continue;
                data[h * this.width + i] = color;
            }
        }
        this.data = data;
    }
}