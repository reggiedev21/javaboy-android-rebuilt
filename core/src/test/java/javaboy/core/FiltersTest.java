package javaboy.core;

import org.junit.jupiter.api.Test;
import java.util.function.IntUnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class FiltersTest {
    @Test void grayOfWhiteIsWhite() { assertEquals(0xFFFFFFFF, Filters.toGray(0xFFFFFFFF)); }
    @Test void grayOfRedIsLuminance() { assertEquals(0xFF4C4C4C, Filters.toGray(0xFFFF0000)); }
    @Test void invertTwiceIsIdentity() { assertEquals(0xFF123456, Filters.invert(Filters.invert(0xFF123456))); }
    @Test void pipelineComposesInOrder() {
        IntUnaryOperator p = Filters.pipeline(Filters::invert, Filters::toGray);
        assertEquals(Filters.toGray(Filters.invert(0xFF102030)), p.applyAsInt(0xFF102030));
    }
    @Test void brightnessClamps() { assertEquals(0xFFFFFFFF, Filters.brightness(300).applyAsInt(0xFF808080)); }
    @Test void applyIdentityCopies() {
        int[] src = {1, 2, 3}, dst = new int[3];
        Filters.apply(Filters.IDENTITY, src, dst);
        assertArrayEquals(src, dst);
    }
}
