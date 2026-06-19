package com.poersmart.charge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

final class BeaconProtocol {
    private static int encryptedCounter = 0;

    private BeaconProtocol() {}

    static BeaconPayload buildBeaconPayload(String mac, String key, int command, int type) throws Exception {
        if (key.length() > 0) {
            return new BeaconPayload(encryptedUuid(mac, key), 8);
        }
        return new BeaconPayload(legacyUuid(mac, command, type), 10);
    }

    static String legacyUuid(String mac, int command, int type) {
        byte[] macBytes = hexToBytes(mac);
        byte[] data = new byte[21];
        data[0] = 'L';
        data[1] = '@';
        data[2] = '2';
        data[3] = '1';
        data[4] = 0;
        System.arraycopy(macBytes, 0, data, 5, 6);
        if (type == 1) {
            byte[] fixed = hexToBytes("FCE892000000");
            System.arraycopy(fixed, 0, data, 11, 6);
        } else {
            int now = (int) (System.currentTimeMillis() / 1000L);
            putIntLE(data, 11, now);
            data[15] = 0;
            data[16] = 0;
        }
        data[17] = 19;
        if (command == 1) data[18] = (byte) 129;
        else if (command == 2) data[18] = 1;
        else data[18] = 0;

        long crc = crc32User(data, 0, 19);
        byte[] crcBig = ByteBuffer.allocate(4).putInt((int) crc).array();
        if (type != 1) {
            data[15] = crcBig[0];
            data[16] = crcBig[1];
        }
        data[19] = crcBig[2];
        data[20] = crcBig[3];
        return uuidFromBytes(data, 5);
    }

    static synchronized String encryptedUuid(String mac, String key) throws Exception {
        byte[] macBytes = hexToBytes(mac);
        byte[] keyBytes = hexToBytes(key);
        byte[] aad = new byte[4];
        aad[0] = macBytes[4];
        aad[1] = macBytes[5];
        aad[2] = (byte) ((encryptedCounter >> 8) & 0xff);
        aad[3] = (byte) (encryptedCounter & 0xff);

        byte[] plain = new byte[8];
        int now = (int) (System.currentTimeMillis() / 1000L);
        putIntBE(plain, 0, now);
        plain[4] = 1;
        plain[5] = 0;
        plain[6] = 0;
        plain[7] = 0;

        byte[] nonce = new byte[13];
        System.arraycopy(macBytes, 0, nonce, 0, 6);
        nonce[6] = (byte) ((encryptedCounter >> 8) & 0xff);
        nonce[7] = (byte) (encryptedCounter & 0xff);
        nonce[8] = 86;
        nonce[9] = 23;
        nonce[10] = (byte) 154;
        nonce[11] = 60;
        nonce[12] = 104;

        byte[] cipher = ccmCrypt(keyBytes, nonce, plain);
        byte[] tag = ccmMac(keyBytes, nonce, aad, plain, 4);
        byte[] out = new byte[16];
        System.arraycopy(aad, 0, out, 0, 4);
        System.arraycopy(cipher, 0, out, 4, 8);
        System.arraycopy(tag, 0, out, 12, 4);
        encryptedCounter = (encryptedCounter + 1) & 0xffff;
        return formatUuid(out);
    }

    static byte[] iBeaconData(String uuid, int major, int minor, int measuredPower) {
        ByteBuffer buffer = ByteBuffer.allocate(23).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x02);
        buffer.put((byte) 0x15);
        buffer.put(uuidToBytes(uuid));
        buffer.putShort((short) major);
        buffer.putShort((short) minor);
        buffer.put((byte) measuredPower);
        return buffer.array();
    }

    private static byte[] ccmCrypt(byte[] key, byte[] nonce, byte[] plain) throws Exception {
        byte[] out = new byte[plain.length];
        int offset = 0;
        int counter = 1;
        while (offset < plain.length) {
            byte[] stream = aesBlock(key, ctrBlock(nonce, counter));
            int n = Math.min(16, plain.length - offset);
            for (int i = 0; i < n; i++) out[offset + i] = (byte) (plain[offset + i] ^ stream[i]);
            offset += n;
            counter++;
        }
        return out;
    }

    private static byte[] ccmMac(byte[] key, byte[] nonce, byte[] aad, byte[] plain, int tagSize) throws Exception {
        int flags = (aad.length > 0 ? 0x40 : 0) | (((tagSize - 2) / 2) << 3) | 0x01;
        byte[] b0 = new byte[16];
        b0[0] = (byte) flags;
        System.arraycopy(nonce, 0, b0, 1, 13);
        b0[14] = (byte) ((plain.length >> 8) & 0xff);
        b0[15] = (byte) (plain.length & 0xff);

        byte[] x = aesBlock(key, b0);
        byte[] formatted = formatCcmInput(aad, plain);
        for (int offset = 0; offset < formatted.length; offset += 16) {
            byte[] block = new byte[16];
            System.arraycopy(formatted, offset, block, 0, 16);
            xorInPlace(block, x);
            x = aesBlock(key, block);
        }
        byte[] s0 = aesBlock(key, ctrBlock(nonce, 0));
        byte[] tag = new byte[tagSize];
        for (int i = 0; i < tagSize; i++) tag[i] = (byte) (x[i] ^ s0[i]);
        return tag;
    }

    private static byte[] formatCcmInput(byte[] aad, byte[] plain) {
        int aadPart = aad.length == 0 ? 0 : round16(2 + aad.length);
        int plainPart = round16(plain.length);
        byte[] out = new byte[aadPart + plainPart];
        int pos = 0;
        if (aad.length > 0) {
            out[pos++] = (byte) ((aad.length >> 8) & 0xff);
            out[pos++] = (byte) (aad.length & 0xff);
            System.arraycopy(aad, 0, out, pos, aad.length);
            pos = aadPart;
        }
        System.arraycopy(plain, 0, out, pos, plain.length);
        return out;
    }

    private static int round16(int value) {
        return value == 0 ? 0 : ((value + 15) / 16) * 16;
    }

    private static byte[] ctrBlock(byte[] nonce, int counter) {
        byte[] out = new byte[16];
        out[0] = 0x01;
        System.arraycopy(nonce, 0, out, 1, 13);
        out[14] = (byte) ((counter >> 8) & 0xff);
        out[15] = (byte) (counter & 0xff);
        return out;
    }

    private static byte[] aesBlock(byte[] key, byte[] block) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(block);
    }

    private static void xorInPlace(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) a[i] = (byte) (a[i] ^ b[i]);
    }

    private static long crc32User(byte[] bytes, int off, int len) {
        long t = 0xffffffffL;
        for (int i = off; i < off + len; i++) {
            t ^= bytes[i] & 0xffL;
            for (int j = 0; j < 4; j++) {
                long u = CRC_TABLE[(int) ((t >> 24) & 0xff)];
                t = ((t << 8) & 0xffffffffL) ^ u;
                t &= 0xffffffffL;
            }
        }
        return t;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static void putIntLE(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xff);
        out[offset + 1] = (byte) ((value >> 8) & 0xff);
        out[offset + 2] = (byte) ((value >> 16) & 0xff);
        out[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private static void putIntBE(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >> 24) & 0xff);
        out[offset + 1] = (byte) ((value >> 16) & 0xff);
        out[offset + 2] = (byte) ((value >> 8) & 0xff);
        out[offset + 3] = (byte) (value & 0xff);
    }

    private static String uuidFromBytes(byte[] bytes, int offset) {
        byte[] out = new byte[16];
        System.arraycopy(bytes, offset, out, 0, 16);
        return formatUuid(out);
    }

    private static String formatUuid(byte[] bytes) {
        String hex = bytesToHex(bytes);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-"
                + hex.substring(20);
    }

    private static byte[] uuidToBytes(String uuid) {
        return hexToBytes(uuid.replace("-", ""));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xff));
        return sb.toString();
    }

    static class BeaconPayload {
        final String uuid;
        final int minor;

        BeaconPayload(String uuid, int minor) {
            this.uuid = uuid;
            this.minor = minor;
        }
    }

    private static final long[] CRC_TABLE = new long[]{
            0L,79764919L,159529838L,222504665L,319059676L,398814059L,445009330L,507990021L,638119352L,583659535L,797628118L,726387553L,890018660L,835552979L,1015980042L,944750013L,
            1276238704L,1221641927L,1167319070L,1095957929L,1595256236L,1540665371L,1452775106L,1381403509L,1780037320L,1859660671L,1671105958L,1733955601L,2031960084L,2111593891L,1889500026L,1952343757L,
            2552477408L,2632100695L,2443283854L,2506133561L,2334638140L,2414271883L,2191915858L,2254759653L,3190512472L,3135915759L,3081330742L,3009969537L,2905550212L,2850959411L,2762807018L,2691435357L,
            3560074640L,3505614887L,3719321342L,3648080713L,3342211916L,3287746299L,3467911202L,3396681109L,4063920168L,4143685023L,4223187782L,4286162673L,3779000052L,3858754371L,3904687514L,3967668269L,
            881225847L,809987520L,1023691545L,969234094L,662832811L,591600412L,771767749L,717299826L,311336399L,374308984L,453813921L,533576470L,25881363L,88864420L,134795389L,214552010L,
            2023205639L,2086057648L,1897238633L,1976864222L,1804852699L,1867694188L,1645340341L,1724971778L,1587496639L,1516133128L,1461550545L,1406951526L,1302016099L,1230646740L,1142491917L,1087903418L,
            2896545431L,2825181984L,2770861561L,2716262478L,3215044683L,3143675388L,3055782693L,3001194130L,2326604591L,2389456536L,2200899649L,2280525302L,2578013683L,2640855108L,2418763421L,2498394922L,
            3769900519L,3832873040L,3912640137L,3992402750L,4088425275L,4151408268L,4197601365L,4277358050L,3334271071L,3263032808L,3476998961L,3422541446L,3585640067L,3514407732L,3694837229L,3640369242L,
            1762451694L,1842216281L,1619975040L,1682949687L,2047383090L,2127137669L,1938468188L,2001449195L,1325665622L,1271206113L,1183200824L,1111960463L,1543535498L,1489069629L,1434599652L,1363369299L,
            622672798L,568075817L,748617968L,677256519L,907627842L,853037301L,1067152940L,995781531L,51762726L,131386257L,177728840L,240578815L,269590778L,349224269L,429104020L,491947555L,
            4046411278L,4126034873L,4172115296L,4234965207L,3794477266L,3874110821L,3953728444L,4016571915L,3609705398L,3555108353L,3735388376L,3664026991L,3290680682L,3236090077L,3449943556L,3378572211L,
            3174993278L,3120533705L,3032266256L,2961025959L,2923101090L,2868635157L,2813903052L,2742672763L,2604032198L,2683796849L,2461293480L,2524268063L,2284983834L,2364738477L,2175806836L,2238787779L,
            1569362073L,1498123566L,1409854455L,1355396672L,1317987909L,1246755826L,1192025387L,1137557660L,2072149281L,2135122070L,1912620623L,1992383480L,1753615357L,1816598090L,1627664531L,1707420964L,
            295390185L,358241886L,404320391L,483945776L,43990325L,106832002L,186451547L,266083308L,932423249L,861060070L,1041341759L,986742920L,613929101L,542559546L,756411363L,701822548L,
            3316196985L,3244833742L,3425377559L,3370778784L,3601682597L,3530312978L,3744426955L,3689838204L,3819031489L,3881883254L,3928223919L,4007849240L,4037393693L,4100235434L,4180117107L,4259748804L,
            2310601993L,2373574846L,2151335527L,2231098320L,2596047829L,2659030626L,2470359227L,2550115596L,2947551409L,2876312838L,2788305887L,2733848168L,3165939309L,3094707162L,3040238851L,2985771188L
    };
}
