package com.aidea.aidea.global.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Yjs v1 update binary에서 Y.Text/Y.XmlText의 ContentString(type 4) 항목을 추출해
 * 문서 텍스트를 복원하는 최소 파서.
 *
 * 지원: ContentString(4), ContentDeleted(1), ContentBinary(3),
 *       ContentEmbed(5), ContentFormat(6), ContentType(7), ContentAny(8), GC(0)
 * 미지원: ContentJSON(2, deprecated), ContentDoc(9), Move(10) → 만나면 파싱 중단
 */
public class YjsTextExtractor {

    public static String extractText(byte[] snapshot, List<byte[]> updates) {
        StringBuilder sb = new StringBuilder();

        if (snapshot != null && snapshot.length > 0) {
            extractFromBinary(snapshot, sb);
        }
        for (byte[] update : updates) {
            extractFromBinary(update, sb);
        }

        return sb.toString().trim();
    }

    private static void extractFromBinary(byte[] data, StringBuilder sb) {
        int[] pos = {0};
        try {
            int numClients = (int) readVarUint(data, pos);
            for (int i = 0; i < numClients; i++) {
                int numStructs = (int) readVarUint(data, pos);
                readVarUint(data, pos); // clientId (사용 안 함)
                long clock = readVarUint(data, pos); // firstClock

                for (int j = 0; j < numStructs; j++) {
                    int info = data[pos[0]++] & 0xFF;
                    int contentType = info & 0x1F;
                    boolean hasOrigin = (info & 0x80) != 0;
                    boolean hasRightOrigin = (info & 0x40) != 0;
                    boolean hasParentSub = (info & 0x20) != 0;

                    // GC: 삭제된 범위 (콘텐츠 없음)
                    if (contentType == 0) {
                        long len = readVarUint(data, pos);
                        clock += len;
                        continue;
                    }

                    // 미지원 타입
                    if (contentType == 2 || contentType == 9 || contentType == 10) {
                        return;
                    }

                    // leftOrigin 읽기
                    if (hasOrigin) {
                        readVarUint(data, pos); // originClientId
                        readVarUint(data, pos); // originClock
                    }

                    // rightOrigin 읽기
                    if (hasRightOrigin) {
                        readVarUint(data, pos); // rightOriginClientId
                        readVarUint(data, pos); // rightOriginClock
                    }

                    // origin 없으면 parent 명시적 읽기
                    if (!hasOrigin && !hasRightOrigin) {
                        int isYKey = data[pos[0]++] & 0xFF;
                        if (isYKey == 1) {
                            readVarString(data, pos); // doc-level key (예: "document-store")
                        } else {
                            readVarUint(data, pos); // parentClientId
                            readVarUint(data, pos); // parentClock
                        }
                    }

                    // parentSub (Map/XmlElement의 속성 키)
                    if (hasParentSub) {
                        readVarString(data, pos);
                    }

                    // 콘텐츠 읽기
                    switch (contentType) {
                        case 1 -> { // ContentDeleted
                            long len = readVarUint(data, pos);
                            clock += len - 1;
                        }
                        case 3 -> { // ContentBinary
                            long len = readVarUint(data, pos);
                            pos[0] += (int) len;
                        }
                        case 4 -> { // ContentString ← 실제 텍스트
                            sb.append(readVarString(data, pos));
                        }
                        case 5 -> readVarString(data, pos); // ContentEmbed (JSON)
                        case 6 -> { // ContentFormat (서식 마커)
                            readVarString(data, pos); // key
                            readAny(data, pos);       // value
                        }
                        case 7 -> { // ContentType (Y.XmlElement 등 노드 생성)
                            int typeRef = data[pos[0]++] & 0xFF;
                            if (typeRef == 3 || typeRef == 5) { // XmlElement, XmlHook
                                readVarString(data, pos); // nodeName
                            }
                        }
                        case 8 -> { // ContentAny (속성 값)
                            long count = readVarUint(data, pos);
                            for (long k = 0; k < count; k++) {
                                readAny(data, pos);
                            }
                        }
                    }

                    clock++;
                }
            }
        } catch (Exception ignored) {
            // 파싱 중 범위 초과 등 → 이미 추출된 텍스트 사용
        }
    }

    // lib0 varUint: 7-bit little-endian groups, MSB=continue
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

    // lib0 varString: varUint length + UTF-8 bytes
    private static String readVarString(byte[] data, int[] pos) {
        int len = (int) readVarUint(data, pos);
        String s = new String(data, pos[0], len, StandardCharsets.UTF_8);
        pos[0] += len;
        return s;
    }

    // lib0 readAny: 타입 바이트 + 값
    private static void readAny(byte[] data, int[] pos) {
        int type = data[pos[0]++] & 0xFF;
        switch (type) {
            case 119 -> readVarString(data, pos);          // string ('w')
            case 125 -> readVarInt(data, pos);             // int
            case 124 -> pos[0] += 4;                       // float32
            case 123 -> pos[0] += 8;                       // float64
            case 122 -> pos[0] += 8;                       // bigint
            case 118 -> {                                   // object ('v')
                long count = readVarUint(data, pos);
                for (long i = 0; i < count; i++) {
                    readVarString(data, pos);
                    readAny(data, pos);
                }
            }
            case 117 -> {                                   // array ('u')
                long count = readVarUint(data, pos);
                for (long i = 0; i < count; i++) readAny(data, pos);
            }
            case 116 -> {                                   // Uint8Array
                long len = readVarUint(data, pos);
                pos[0] += (int) len;
            }
            // 127=undefined, 126=null, 121=true, 120=false → 값 바이트 없음
        }
    }

    // lib0 varInt (signed, MSB=continue, bit6=negative on first byte)
    private static void readVarInt(byte[] data, int[] pos) {
        int r = data[pos[0]++] & 0xFF;
        if ((r & 0x80) != 0) {
            while ((data[pos[0]++] & 0x80) != 0) { /* skip */ }
        }
    }
}
