package com.owl.downloader.core;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Used by tasks to store and represent the resource file information
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public class FileData implements Serializable {
    private static final long serialVersionUID = 7459823640104585550L;
    private final File file;
    private final List<Block> blocks = new LinkedList<>();

    public FileData(String path, int blockSize) {
        Objects.requireNonNull(path, "the path of file cannot be null");
        this.file = new File(path);
        split(blockSize);
    }

    public FileData(File file, int blockSize) {
        Objects.requireNonNull(file, "the file cannot be null");
        this.file = file;
        split(blockSize);
    }

    private void split(int blockSize) {
        int offset = 0;
        while (offset < file.length()) {
            blocks.add(new Block(offset, Math.min(blockSize, file.length() - offset)));
            offset += blockSize;
        }
    }

    /**
     * Get the actual file of the file data
     *
     * @return the actual file
     */
    public File getFile() {
        return file;
    }

    /**
     * Get the blocks of the file
     *
     * @return the blocks of the file
     */
    public List<Block> getBlocks() {
        return List.copyOf(blocks);
    }

    /**
     * Represent a piece of file
     *
     * @author Ricardo Evans
     * @version 1.0
     */
    public class Block implements Serializable {
        private static final long serialVersionUID = -2318415188916431522L;
        /**
         * The offset of this block, in bytes
         */
        public final long offset;
        /**
         * The length of this block, in bytes
         */
        public final long length;
        /**
         * Whether this block is available
         */
        public boolean available = true;

        public Block(long offset, long length) {
            this.offset = offset;
            this.length = length;
        }

        /**
         * Get the file data this block belongs to
         *
         * @return the file data this block belongs to
         */
        public FileData file() {
            return FileData.this;
        }
    }

    /**
     * Select which block to be downloaded/uploaded next.
     * <p>This is especially useful when the downloading/uploading resource contains metadata, such as audio/video, since the audio/video can be played while downloading/uploading once the metadata is available</p>
     *
     * @author Ricardo Evans
     * @version 1.0
     */
    @FunctionalInterface
    public interface BlockSelector {
        /**
         * The default block selector
         *
         * @return the default block selector
         */
        static BlockSelector getDefault() {
            BlockSelector blockSelector = (List<Block> blocks1) -> {
                return blocks1.stream().filter(block -> block.available).findAny().get();
            };
            return blockSelector;
        }

        /**
         * Select a block from the given available blocks
         *
         * @param availableBlocks the available block
         * @return the prefer block
         */
        Block select(List<Block> availableBlocks);
    }

}
