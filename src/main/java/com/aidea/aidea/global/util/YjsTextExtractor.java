package com.aidea.aidea.global.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yjs v1 update binary에서 Y.Text/Y.XmlText의 ContentString(type 4) 항목을 추출해
 * 문서 텍스트를 복원하는 최소 파서.
 *
 * 공식 소스 기준 (yjs/yjs src/structs/Item.js, src/structs/GC.js,
 *                 src/utils/updates.js lazyStructReaderGenerator,
 *                 dmonad/lib0 decoding.js)
 *
 * 지원: GC(0), ContentDeleted(1), ContentBinary(3), ContentString(4),
 *       ContentEmbed(5), ContentFormat(6), ContentType(7), ContentAny(8), Skip(10)
 * 미지원: ContentJSON(2, deprecated), ContentDoc(9) → 만나면 해당 클라이언트 파싱 중단
 */
public class YjsTextExtractor {

    public static String extractText(byte[] snapshot, List<byte[]> updates) {
        // [clientId, startClock, length, textIdx]
        List<long[]> allItems = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        // snapshot + 모든 update의 delete set을 합산해 교차 삭제(cross-binary deletion) 처리
        Map<Long, List<long[]>> globalDeleteSet = new HashMap<>();

        if (snapshot != null && snapshot.length > 0) {
            parseBinary(snapshot, allItems, texts, globalDeleteSet);
        }
        for (byte[] update : updates) {
            parseBinary(update, allItems, texts, globalDeleteSet);
        }

        StringBuilder sb = new StringBuilder();
        for (long[] item : allItems) {
            if (!isDeleted(item[0], item[1], item[2], globalDeleteSet)) {
                sb.append(texts.get((int) item[3]));
            }
        }
        return sb.toString().trim();
    }

    /**
     * 단일 Yjs v1 바이너리 블롭을 파싱한다.
     * 포맷: [ Struct Section ][ Delete Set Section ]
     *
     * Struct Section:
     *   numClients: varuint
     *   per client:
     *     numStructs: varuint
     *     clientId:   varuint
     *     firstClock: varuint
     *     per struct:
     *       info: uint8  (bits [4:0]=contentType, [5]=hasParentSub, [6]=hasRightOrigin, [7]=hasLeftOrigin)
     *       ...struct body...
     *       clock += struct.getLength()
     *
     * Delete Set Section (V1):
     *   numClients: varuint
     *   per client:
     *     clientId:      varuint
     *     numRanges:     varuint
     *     per range:
     *       clock: varuint  (absolute)
     *       len:   varuint
     */
    private static void parseBinary(byte[] data,
                                    List<long[]> items,
                                    List<String> texts,
                                    Map<Long, List<long[]>> deleteSet) {
        int[] pos = {0};
        try {
            // ── Struct Section ──────────────────────────────────────
            int numClients = (int) readVarUint(data, pos);
            for (int i = 0; i < numClients; i++) {
                int numStructs = (int) readVarUint(data, pos);
                long clientId  = readVarUint(data, pos);
                long clock     = readVarUint(data, pos);

                for (int j = 0; j < numStructs; j++) {
                    int     info           = data[pos[0]++] & 0xFF;
                    int     contentType    = info & 0x1F;
                    boolean hasOrigin      = (info & 0x80) != 0; // BIT8 = leftOrigin
                    boolean hasRightOrigin = (info & 0x40) != 0; // BIT7
                    boolean hasParentSub   = (info & 0x20) != 0; // BIT6

                    // ── GC (info & BITS5 == 0, i.e. contentType == 0) ──
                    // GC는 origin/parent 없이 길이만 인코딩
                    if (contentType == 0) {
                        long len = readVarUint(data, pos);
                        clock += len;
                        continue;
                    }

                    // ── Skip (info == 10) ──
                    // lazyStructReaderGenerator: `if (info === 10)` → readVarUint + clock += len
                    // 이전 코드의 버그: return으로 처리하여 이후 structs를 모두 누락했음
                    if (contentType == 10) {
                        long len = readVarUint(data, pos);
                        clock += len;
                        continue;
                    }

                    // 미지원 타입 (ContentJSON=2 deprecated, ContentDoc=9)
                    if (contentType == 2 || contentType == 9) {
                        return;
                    }

                    // ── Item struct 공통 헤더 ──────────────────────────
                    // 1) leftOrigin (origin)
                    if (hasOrigin) { readVarUint(data, pos); readVarUint(data, pos); }

                    // 2) rightOrigin
                    if (hasRightOrigin) { readVarUint(data, pos); readVarUint(data, pos); }

                    // 3) parent 정보는 cantCopyParentInfo == true일 때만 인코딩됨
                    //    cantCopyParentInfo = (origin == null && rightOrigin == null)
                    //    Item.write()에서:
                    //      if (origin === null && rightOrigin === null) {
                    //        encoder.writeParentInfo(isYKey ? 1 : 0)
                    //        if (isYKey) encoder.writeString(ykey)
                    //        else encoder.writeLeftID(parentItem.id)
                    //
                    //        if (parentSub !== null) encoder.writeString(parentSub)  ← 여기서만 기록
                    //      }
                    //
                    // 이전 코드의 버그: hasParentSub 체크가 if(!hasOrigin && !hasRightOrigin) 밖에 있어서
                    //   origin 있는 Y.Map 아이템의 경우 parentSub string을 없는데도 읽으려 해 오정렬 발생
                    if (!hasOrigin && !hasRightOrigin) {
                        int isYKey = data[pos[0]++] & 0xFF; // writeParentInfo: varuint(0 or 1), 단일 바이트
                        if (isYKey == 1) readVarString(data, pos);  // parentYKey
                        else { readVarUint(data, pos); readVarUint(data, pos); } // parentItem.id

                        // parentSub는 cantCopyParentInfo가 true일 때만 기록됨
                        if (hasParentSub) readVarString(data, pos);
                    }

                    // ── Content 읽기 ──────────────────────────────────
                    // struct.getLength()에 따라 clock을 진행시켜야 함:
                    //   ContentString  → str.length   (UTF-16 code units, JS str.length와 동일)
                    //   ContentDeleted → len
                    //   ContentAny     → arr.length (원소 개수)   ← 이전 코드의 버그: 1로 처리했음
                    //   나머지         → 1
                    long structLen = 1;
                    switch (contentType) {
                        case 1 -> { // ContentDeleted: getLength() = len
                            structLen = readVarUint(data, pos);
                        }
                        case 3 -> { // ContentBinary: getLength() = 1
                            long len = readVarUint(data, pos);
                            pos[0] += (int) len;
                        }
                        case 4 -> { // ContentString: getLength() = str.length
                            String text = readVarString(data, pos);
                            int idx = texts.size();
                            texts.add(text);
                            items.add(new long[]{clientId, clock, text.length(), idx});
                            structLen = text.length();
                        }
                        case 5 -> readVarString(data, pos); // ContentEmbed: getLength() = 1, JSON string
                        case 6 -> { // ContentFormat: getLength() = 1
                            readVarString(data, pos); // key
                            readAny(data, pos);       // value
                        }
                        case 7 -> { // ContentType: getLength() = 1
                            // _write(): writeTypeRef(varuint), XmlElement/XmlHook만 nodeName(varstring) 추가
                            int typeRef = data[pos[0]++] & 0xFF;
                            if (typeRef == 3 || typeRef == 5) readVarString(data, pos); // XmlElement, XmlHook
                        }
                        case 8 -> { // ContentAny: getLength() = arr.length
                            long count = readVarUint(data, pos);
                            for (long k = 0; k < count; k++) readAny(data, pos);
                            structLen = count; // 이전 코드의 버그: 항상 1이었음
                        }
                    }

                    clock += structLen;
                }
            }

            // ── Delete Set Section (V1) ──────────────────────────────
            // struct 섹션 직후 위치. pos[0] == data.length이면 delete set 없음(순수 삽입 업데이트)
            if (pos[0] < data.length) {
                int dsNumClients = (int) readVarUint(data, pos);
                for (int i = 0; i < dsNumClients; i++) {
                    long clientId  = readVarUint(data, pos);
                    int numRanges  = (int) readVarUint(data, pos);
                    List<long[]> ranges = deleteSet.computeIfAbsent(clientId, k -> new ArrayList<>());
                    for (int j = 0; j < numRanges; j++) {
                        long dsClock = readVarUint(data, pos); // V1: absolute clock
                        long dsLen   = readVarUint(data, pos); // V1: range length
                        ranges.add(new long[]{dsClock, dsLen});
                    }
                }
            }
        } catch (Exception ignored) {
            // 파싱 중 범위 초과 등 → 이미 수집된 items 사용
        }
    }

    /**
     * ContentString이 delete set의 어느 range에 완전히 포함되면 삭제된 항목으로 판정.
     * Yjs는 부분 삭제 시 ContentString을 분할(split)하므로, 각 항목은 fully deleted or fully alive.
     */
    private static boolean isDeleted(long clientId, long startClock, long length,
                                     Map<Long, List<long[]>> deleteSet) {
        List<long[]> ranges = deleteSet.get(clientId);
        if (ranges == null) return false;
        for (long[] range : ranges) {
            long rangeStart = range[0];
            long rangeLen   = range[1];
            if (rangeStart <= startClock && rangeStart + rangeLen >= startClock + length) {
                return true;
            }
        }
        return false;
    }

    // ── lib0 primitives ─────────────────────────────────────────────

    // lib0 readVarUint: 7-bit little-endian groups, MSB=continue
    // decoding.js: `num = num | ((r & BITS7) << len); len += 7; if (r < BIT8) return num`
    private static long readVarUint(byte[] data, int[] pos) {
        long num = 0, mult = 1;
        while (true) {
            int b = data[pos[0]++] & 0xFF;
            num += (b & 0x7F) * mult;
            mult *= 128;
            if ((b & 0x80) == 0) break;
        }
        return num;
    }

    // lib0 readVarString: readVarUint(byteLength) + UTF-8 bytes
    // decoding.js: reads raw bytes, then `decodeURIComponent(escape(str))` ≡ UTF-8 decode
    private static String readVarString(byte[] data, int[] pos) {
        int len = (int) readVarUint(data, pos);
        String s = new String(data, pos[0], len, StandardCharsets.UTF_8);
        pos[0] += len;
        return s;
    }

    // lib0 readAny: type byte(127-index) + value
    // decoding.js readAnyLookupTable[127 - readUint8(decoder)](decoder)
    private static void readAny(byte[] data, int[] pos) {
        int type = data[pos[0]++] & 0xFF;
        switch (type) {
            case 119 -> readVarString(data, pos);   // string ('w')
            case 125 -> readVarInt(data, pos);      // integer
            case 124 -> pos[0] += 4;                // float32
            case 123 -> pos[0] += 8;                // float64
            case 122 -> pos[0] += 8;                // bigint64
            case 118 -> {                           // object
                long count = readVarUint(data, pos);
                for (long i = 0; i < count; i++) { readVarString(data, pos); readAny(data, pos); }
            }
            case 117 -> {                           // array
                long count = readVarUint(data, pos);
                for (long i = 0; i < count; i++) readAny(data, pos);
            }
            case 116 -> {                           // Uint8Array
                long len = readVarUint(data, pos);
                pos[0] += (int) len;
            }
            // 127=undefined, 126=null, 121=false, 120=true → payload 없음
        }
    }

    // lib0 readVarInt (signed): first byte [bit7=continue, bit6=sign, bits5-0=data], rest [bit7=continue, bits6-0=data]
    // decoding.js: sign = (r & BIT7) > 0 ? -1 : 1; if (r & BIT8 == 0) done; else read continuation bytes
    private static void readVarInt(byte[] data, int[] pos) {
        int r = data[pos[0]++] & 0xFF;
        if ((r & 0x80) != 0) { // BIT8: continuation flag
            while ((data[pos[0]++] & 0x80) != 0) { /* skip continuation bytes */ }
        }
    }
}
