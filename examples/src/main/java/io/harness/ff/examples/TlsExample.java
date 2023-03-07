package io.harness.ff.examples;

import io.harness.cf.client.api.BaseConfig;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.FeatureFlagInitializeException;
import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.connector.HarnessConnector;
import io.harness.cf.client.dto.Target;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;

import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static java.lang.System.out;

public class TlsExample {
    private static final String apiKey = getEnvOrDefault("FF_API_KEY", "");
    private static final String flagName = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemodarkmode");
    private static final String trustedCaPemFile = getEnvOrDefault("FF_TRUSTED_CA_FILE_NAME", "/change/me/CA.pem");

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Provider bcProvider = new BouncyCastleProvider();


    public static void main(String[] args) throws InterruptedException, FeatureFlagInitializeException, GeneralSecurityException, IOException {
        out.println("Java SDK TLS example");

        List<X509Certificate> trustedServers = loadCerts(trustedCaPemFile);

        // Note that this code uses ffserver hostname as an example, likely you'll have your own hostname or IP.
        // You should ensure the endpoint is returning a cert with valid SANs configured for the host/IP.
        HarnessConfig config = HarnessConfig.builder()
                .configUrl("https://ffserver:8001/api/1.0")
                .eventUrl("https://ffserver:8000/api/1.0")
                .tlsTrustedCAs(trustedServers)
                .build();

        HarnessConnector connector = new HarnessConnector(apiKey, config);

        try (CfClient cfClient = new CfClient(connector)) {

            cfClient.waitForInitialization();

            final Target target = Target.builder()
                    .identifier("javasdk")
                    .name("JavaSDK")
                    .build();

            // Loop forever reporting the state of the flag
            scheduler.scheduleAtFixedRate(
                    () -> {
                        boolean result = cfClient.boolVariation(flagName, target, false);
                        out.println("Flag '" + flagName + "' Boolean variation is " + result);
                    },
                    0,
                    10,
                    TimeUnit.SECONDS);


            TimeUnit.MINUTES.sleep(15);

            out.println("Cleaning up...");
            scheduler.shutdownNow();
        }
    }

    // Get the value from the environment or return the default
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    // Here we're using BC's PKIX lib to convert the PEM to an X.509, you can use any crypto library you prefer
    private static List<X509Certificate> loadCerts(String filename) throws IOException, CertificateException {
        List<X509Certificate> list = new ArrayList<>();
        try (PEMParser parser = new PEMParser(new FileReader(filename))) {
            Object obj;
            while ((obj = parser.readObject()) !=  null) {
                if (obj instanceof X509CertificateHolder) {
                    list.add(new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate((X509CertificateHolder) obj));
                }
            }
        }
        return list;
    }
}