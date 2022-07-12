package io.harness.cf.client.api;

import static org.testng.Assert.*;

import com.google.gson.JsonObject;
import io.harness.cf.client.connector.LocalConnector;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Slf4j
public class InnerClientIntTest {

  private LocalConnector connector;

  private CfClient client;

  @BeforeClass
  public void setup() {
    String path =
        Objects.requireNonNull(getClass().getClassLoader().getResource("fixtures")).getPath();
    connector = new LocalConnector(path);
    client = Mockito.spy(new CfClient(connector, BaseConfig.builder().build()));
  }

  @Test
  public void WhenBoolFlagIsOnItShouldReturnTrue() {

    boolean got = client.boolVariation("bool-flag-1", null, false);

    assertTrue(got);
  }

  @Test
  public void WhenBoolFlagIsOffItShouldReturnFalse() {

    boolean got = client.boolVariation("bool-flag-2", null, false);

    assertFalse(got);
  }

  @Test
  public void WhenBoolFlagIsOnAndOnVariationIsFalseItShouldReturnFalse() {

    boolean got = client.boolVariation("bool-flag-3", null, false);

    assertFalse(got);
  }

  @Test
  public void WhenBoolFlagIsOffAndOnVariationIsFalseItShouldReturnTrue() {

    boolean got = client.boolVariation("bool-flag-4", null, false);

    assertTrue(got);
  }

  @Test
  public void WhenStringFlag1IsOnItShouldReturnOne() {
    // fixture flags/string-flag-1.json

    String got = client.stringVariation("string-flag-1", null, "three");

    assertEquals(got, "one");
  }

  @Test
  public void WhenStringFlag2IsOffItShouldReturnTwo() {
    // fixture flags/string-flag-2.json

    String got = client.stringVariation("string-flag-2", null, "three");

    assertEquals(got, "two");
  }

  @Test
  public void WhenStringFlag3IsOnItShouldReturnThree() {
    // fixture flags/string-flag-3.json

    String got = client.stringVariation("string-flag-3", null, "one");

    assertEquals(got, "three");
  }

  @Test
  public void WhenStringFlag4IsOffItShouldReturnOne() {
    // fixture flags/string-flag-4.json

    String got = client.stringVariation("string-flag-4", null, "two");

    assertEquals(got, "one");
  }

  @Test
  public void WhenStringFlag5IsOnItShouldReturn5() {
    // fixture flags/string-flag-5.json

    String got = client.stringVariation("string-flag-5", null, "five");

    assertEquals(got, "5");
  }

  @Test
  public void WhenStringFlag6IsOffItShouldReturnFive() {
    // fixture flags/string-flag-6.json

    String got = client.stringVariation("string-flag-6", null, "5");

    assertEquals(got, "five");
  }

  @Test
  public void WhenStringFlag7IsOnItShouldReturnFive5() {
    // fixture flags/string-flag-7.json

    String got = client.stringVariation("string-flag-7", null, "5");

    assertEquals(got, "five5");
  }

  @Test
  public void WhenStringFlag8IsOffItShouldReturn6Six() {
    // fixture flags/string-flag-8.json

    String got = client.stringVariation("string-flag-8", null, "5");

    assertEquals(got, "6Six");
  }

  @Test
  public void WhenStringFlag9IsOnItShouldReturnRosettiQuote() {
    // fixture flags/string-flag-9.json

    String got = client.stringVariation("string-flag-9", null, "Wilde quote");

    assertEquals(
        got,
        "“Oh where are you going with your love-locks flowing On the west wind blowing along this valley track?” “The downhill path is easy, come with me an it please ye, We shall escape the uphill by never turning back.” So they two went together in glowing August weather, The honey-breathing heather lay to their left and right; And dear she was to dote on, her swift feet seemed to float on The air like soft twin pigeons too sportive to alight. “Oh what is that in heaven where gray cloud-flakes are seven, Where blackest clouds hang riven just at the rainy skirt?” “Oh that’s a meteor sent us, a message dumb, portentous, An undeciphered solemn signal of help or hurt.” “Oh what is that glides quickly where velvet flowers grow thickly, Their scent comes rich and sickly?”—“A scaled and hooded worm.” “Oh what’s that in the hollow, so pale I quake to follow?” “Oh that’s a thin dead body which waits the eternal term.” “Turn again, O my sweetest,—turn again, false and fleetest: This beaten way thou beatest I fear is hell’s own track.” “Nay, too steep for hill-mounting; nay, too late for cost-counting: This downhill path is easy, but there’s no turning back.”");
  }

  @Test
  public void WhenStringFlag10IsOffItShouldReturnRosettiQuote() {
    // fixture flags/string-flag-10.json

    String got = client.stringVariation("string-flag-10", null, "Rosetti quote");

    assertEquals(
        got,
        "Anti-mimesis is a philosophical position that holds the direct opposite of Aristotelian mimesis. Its most notable proponent is Oscar Wilde, who opined in his 1889 essay The Decay of Lying that, \"Life imitates Art far more than Art imitates Life\". In the essay, written as a Platonic dialogue, Wilde holds that anti-mimesis \"results not merely from Life's imitative instinct, but from the fact that the self-conscious aim of Life is to find expression, and that Art offers it certain beautiful forms through which it may realise that energy.\"[1][2]\nWhat is found in life and nature is not what is really there, but is that which artists have taught people to find there, through art. As in an example posited by Wilde, although there has been fog in London for centuries, one notices the beauty and wonder of the fog because \"poets and painters have taught the loveliness of such effects...They did not exist till Art had invented them.\"[1]\nMcGrath places the antimimetic philosophy in a tradition of Irish writing, including Wilde and writers such as Synge and Joyce in a group that \"elevate blarney (in the form of linguistic idealism) to aesthetic and philosophical distinction\", noting that Terry Eagleton observes an even longer tradition that stretches \"as far back in Irish thought as the ninth-century theology of John Scottus Eriugena\" and \"the fantastic hyperbole of the ancient sagas\". Wilde's antimimetic idealism, specifically, McGrath describes to be part of the late nineteenth century debate between Romanticism and Realism.[1] Wilde's antimimetic philosophy has also had influence on later Irish writers, including Brian Friel.\nHalliwell asserts that the idea that life imitates art derives from classical notions that can be traced as far back as the writings of Aristophanes of Byzantium, and does not negate mimesis but rather \"displace[s] its purpose onto the artlike fashioning of life itself\". Halliwell draws a parallel between Wilde's philosophy and Aristophanes' famous question about the comedies written by Menander: \"O Menander and Life! Which of you took the other as your model?\", noting, however, that Aristophanes was a precursor to Wilde, and not necessarily espousing the positions that Wilde was later to propound.[3]\nIn George Bernard Shaw's preface to Three Plays he wrote, \"I have noticed that when a certain type of feature appears in painting and is admired as beautiful, it presently becomes common in nature; so that the Beatrices and Francescas in the picture galleries of one generation come to life as the parlor-maids and waitresses of the next.\" He stated that he created the aristocratic characters in Cashel Byron's Profession as unrealistically priggish even without his later understanding that \"the real world does not exist...men and women are made by their own fancies in the image of the imaginary creatures in my youthful fictions, only much stupider.\" Shaw, however, disagreed with Wilde on some points. He considered most attempts by life to imitate art to be reprehensible, in part because the art that people generally chose to imitate was idealistic and romanticized.[4]\nAlso well-known fiction writers explore broadly and magnificently the theme. Miguel de Cervantes Saavedra, author of the infamous Quixote, is one of the first modern writers who touches this topic while alluding to reality-fiction boundaries. Likewise, the Argentinian author Jorge Luis Borges explores the idea of reality imitating art mainly in his short stories “Tema del traidor y del héroe”, “Un problema”, “Un sueño” and “El evangelio según San Marcos”.\n");
  }

  @Test
  public void WhenStringFlag11IsOnItShouldReturnJava() {
    // fixture flags/string-flag-11.json

    String got = client.stringVariation("string-flag-11", null, "Some string");

    assertEquals(
        got,
        "// This is a simple Java program.\n// FileName : \"HelloWorld.java\".\n \nclass HelloWorld\n{\n    // Your program begins with a call to main().\n    // Prints \"Hello, World\" to the terminal window.\n    public static void main(String args[])\n    {\n        System.out.println(\"Hello, World\");\n    }\n}\n");
  }

  @Test
  public void WhenStringFlag12IsOffItShouldReturnSpecChars1() {
    // fixture flags/string-flag-12.json

    String got = client.stringVariation("string-flag-12", null, "^%&\"'\\/#");

    assertEquals(got, "$£-_><~");
  }

  @Test
  public void WhenStringFlag13IsOffItShouldReturnSpecChars2() {
    // fixture flags/string-flag-13.json

    String got = client.stringVariation("string-flag-13", null, "$£-_><~");

    assertEquals(got, "^%&\"'\\/#");
  }

  @Test
  public void WhenStringFlag14IsOnItShouldReturnSpecChars3() {
    // fixture flags/string-flag-14.json

    String got = client.stringVariation("string-flag-14", null, "^%&\"'\\/#");

    assertEquals(got, "><~^%");
  }

  @Test
  public void WhenStringFlag15IsOnItShouldReturnSpecChars3() {
    // fixture flags/string-flag-15.json

    String got = client.stringVariation("string-flag-15", null, "^%&\"'\\/#");

    assertEquals(got, "$£-_><~");
  }

  @Test
  public void WhenNumberFlag1IsOnItShouldReturnQuarter() {
    // fixture flags/number-flag-1.json

    double got = client.numberVariation("number-flag-1", null, 0);

    assertEquals(got, 0.25);
  }

  @Test
  public void WhenNumberFlag2IsOffItShouldReturnOne() {
    // fixture flags/number-flag-2.json

    double got = client.numberVariation("number-flag-2", null, 0);

    assertEquals(got, 1.0);
  }

  @Test
  public void WhenNumberFlag3IsOnItShouldReturnHalf() {
    // fixture flags/number-flag-3.json

    double got = client.numberVariation("number-flag-3", null, 0);

    assertEquals(got, 0.5);
  }

  @Test
  public void WhenNumberFlag4IsOnItShouldReturnQuarter() {
    // fixture flags/number-flag-4.json

    double got = client.numberVariation("number-flag-4", null, 0);

    assertEquals(got, 0.25);
  }

  @Test
  public void WhenNumberFlag5IsOnItShouldReturnBig() {
    // fixture flags/number-flag-5.json

    double got = client.numberVariation("number-flag-5", null, 0);

    assertEquals(got, 1.5625478965213255454589785451236541256321);
  }

  @Test
  public void WhenJsonFlag1IsOnItShouldReturnMixedJSON() {
    // fixture flags/json-flag-1.json

    JsonObject got = client.jsonVariation("json-flag-1", null, new JsonObject());

    JsonObject expected = new JsonObject();
    expected.addProperty("number", "5");
    expected.addProperty("word", "five");
    expected.addProperty("char", "&^%$");

    assertEquals(got, expected);
  }

  @Test
  public void WhenJsonFlag2IsOnItShouldReturnMixed_JSON() {
    // fixture flags/json-flag-2.json

    JsonObject got = client.jsonVariation("json-flag-2", null, new JsonObject());

    JsonObject expected = new JsonObject();
    expected.addProperty("number", "5");
    expected.addProperty("word", "five");
    expected.addProperty("char", "&^%$");

    assertEquals(got, expected);
  }

  @Test
  public void WhenJsonFlag3IsOffItShouldReturnMixed1JSON() {
    // fixture flags/json-flag-3.json

    JsonObject got = client.jsonVariation("json-flag-3", null, new JsonObject());

    JsonObject expected = new JsonObject();
    expected.addProperty("number", "5");
    expected.addProperty("word", "five");
    expected.addProperty("char", "&^%$");

    assertEquals(got, expected);
  }
}
