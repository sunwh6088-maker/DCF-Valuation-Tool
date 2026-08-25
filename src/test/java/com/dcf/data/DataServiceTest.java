package com.dcf.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** DataService.normalizeCode 代码归一化与友好提示测试。 */
class DataServiceTest {

    @Test
    void normalizesValidACodes() {
        assertEquals("600519", DataService.normalizeCode("600519"));
        assertEquals("600519", DataService.normalizeCode("sh600519"));
        assertEquals("600519", DataService.normalizeCode("SH600519"));
        assertEquals("000858", DataService.normalizeCode("sz000858"));
        assertEquals("600519", DataService.normalizeCode("６００５１９")); // 全角
        assertEquals("600519", DataService.normalizeCode(" 600519 "));
    }

    @Test
    void rejectsHongKongCodeWithFriendlyMessage() {
        IllegalArgumentException h = assertThrows(IllegalArgumentException.class,
                () -> DataService.normalizeCode("H00700"));
        assertEquals(true, h.getMessage().contains("港股"));
        assertEquals(true, h.getMessage().contains("美股手动输入"));

        IllegalArgumentException hk = assertThrows(IllegalArgumentException.class,
                () -> DataService.normalizeCode("hk00700"));
        assertEquals(true, hk.getMessage().contains("港股"));
    }

    @Test
    void rejectsOtherInvalidCodes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataService.normalizeCode("00700"));
        assertEquals(true, e.getMessage().contains("6 位数字"));

        assertThrows(IllegalArgumentException.class, () -> DataService.normalizeCode("AAPL"));
        assertThrows(IllegalArgumentException.class, () -> DataService.normalizeCode(""));
    }
}
