package org.deepsymmetry.cratedigger.pdb;

import io.kaitai.struct.CustomDecoder;

/**
 * <a href="https://doc.kaitai.io/user_guide.html#custom-process" target="_blank">Custom processing
 *  function</a> used to unmask the body of a song structure tag, which is masked using a 19-byte
 * XOR mask whose content depends on the number of phrases in the tag.
 *
 * Created by James Elliott on 2021-01-28.
 */
public class UnmaskSongStructureTag implements CustomDecoder {

    /**
     * Stores the phrase count found right before the masked section, which is used to derive the unmasking key.
     */
    private final byte[] mask = new byte[]{ (byte) 0xCB, (byte) 0xE1, (byte) 0xEE, (byte) 0xFA, (byte) 0xE5, (byte) 0xEE, (byte) 0xAD, (byte) 0xEE,
            (byte) 0xE9, (byte) 0xD2, (byte) 0xE9, (byte) 0xEB, (byte) 0xE1, (byte) 0xE9, (byte) 0xF3, (byte) 0xE8,
            (byte) 0xE9, (byte) 0xF4, (byte) 0xE1};

    /**
     * Constructs an instance to unmask the body of a tag with the specified number of phrases in it. This
     * is used to adjust the unmasking key so that it will work properly.
     *
     * @param phraseCount the number of phrase entries in the PSSI tag being processed.
     */
    public UnmaskSongStructureTag(int phraseCount) {
        for (int i = 0; i < mask.length; i++) {
            mask[i] = (byte) (mask[i] + phraseCount);
        }
    }

    @Override
    public byte[] decode(byte[] bytes) {
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) (bytes[i] ^ mask[i % mask.length]);
        }
        return result;
    }
}
