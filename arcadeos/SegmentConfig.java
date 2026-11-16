package arcadeos;

import java.awt.Color;

/**
 * FAULT DEFENDER - one segment in a level.
 *
 * A segment is a contiguous run of physical memory described by a base address
 * and a size limit. A logical address is a pair (segment number, offset); it is
 * valid only while {@code offset < limit}, and then the physical address is
 * {@code base + offset}. {@code readOnly} raises a protection fault on a write.
 *
 * Pure data holder: both the simulation and the renderer read from it.
 */
public class SegmentConfig {

    public final int number;
    public final String name;
    public final int base;
    public final int limit;
    public final boolean readOnly;
    public final Color color;

    public SegmentConfig(int number, String name, int base, int limit, boolean readOnly, Color color) {
        this.number = number;
        this.name = (name == null || name.trim().isEmpty()) ? ("Seg_" + number) : name.trim();
        this.base = Math.max(0, base);
        this.limit = Math.max(1, limit);
        this.readOnly = readOnly;
        this.color = color == null ? new Color(140, 140, 170) : color;
    }

    /** Exclusive end of the segment's physical range. */
    public int end() {
        return base + limit;
    }

    /** True when this segment's [base, base+limit) range intersects another's. */
    public boolean overlaps(SegmentConfig o) {
        return o != null && base < o.end() && o.base < end();
    }

    public SegmentConfig withBase(int newBase) {
        return new SegmentConfig(number, name, newBase, limit, readOnly, color);
    }

    @Override
    public String toString() {
        return number + ":" + name;
    }
}
