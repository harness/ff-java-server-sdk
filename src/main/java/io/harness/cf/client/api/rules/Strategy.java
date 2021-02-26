package io.harness.cf.client.api.rules;

import com.sangupta.murmur.Murmur3;

public class Strategy {

  public static final int ONE_HUNDRED = 100;

  private final String identifier;
  private final String bucketBy;

  public Strategy(String identifier, String bucketBy) {
    this.identifier = identifier;
    this.bucketBy = bucketBy;
  }

  public int loadNormalizedNumber() {
    return loadNormalizedNumberWithNormalizer(ONE_HUNDRED);
  }

  public int loadNormalizedNumberWithNormalizer(int normalizer) {
    byte[] value = String.join(":", bucketBy, identifier).getBytes();
    long hasher = Murmur3.hash_x86_32(value, value.length, Murmur3.UINT_MASK);
    return (int) (hasher % normalizer) + 1;
  }
}
