package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileMapStoreTest {

  @Test
  void testFileMap() throws IOException {
    File file = File.createTempFile(FileMapStoreTest.class.getSimpleName(), ".tmp");
    file.deleteOnExit();

    try (XmlFileMapStore store = new XmlFileMapStore(file.getAbsolutePath())) {

      store.set("testkey1", "testval1");
      store.set("testkey2", "testval2");
      store.set("testkey3", "testval3");

      assertEquals("testval1", store.get("testkey1"));
      assertEquals("testval2", store.get("testkey2"));
      assertEquals("testval3", store.get("testkey3"));

      List<String> keys = store.keys();
      assertNotNull(keys);
      assertEquals(3, keys.size());

      store.delete("testkey2");
      keys = store.keys();
      assertNotNull(keys);
      assertEquals(2, keys.size());
      assertFalse(keys.contains("testkey2"));
    }
  }
}
