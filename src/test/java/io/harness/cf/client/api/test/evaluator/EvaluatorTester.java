package io.harness.cf.client.api.test.evaluator;

import io.harness.cf.client.api.CaffeineCache;
import io.harness.cf.client.api.Repository;
import io.harness.cf.client.api.RepositoryCallback;
import io.harness.cf.client.api.StorageRepository;
import io.harness.cf.client.common.Cache;
import io.harness.cf.model.Segment;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;

import java.util.LinkedList;
import java.util.List;

@Slf4j
public class EvaluatorTester implements EvaluatorTesting {

    private final Repository repository;
    private final List<TestResult> results;

    {

        Cache cache = new CaffeineCache(10000);

        repository = new StorageRepository(

                cache,
                new RepositoryCallback() {

                    @Override
                    public void onFlagStored(@NonNull String identifier) {

                        log.info("onFlagStored: " + identifier);
                    }

                    @Override
                    public void onFlagDeleted(@NonNull String identifier) {

                        log.info("onFlagDeleted: " + identifier);
                    }

                    @Override
                    public void onSegmentStored(@NonNull String identifier) {

                        log.info("onSegmentStored: " + identifier);
                    }

                    @Override
                    public void onSegmentDeleted(@NonNull String identifier) {

                        log.info("onSegmentDeleted: " + identifier);
                    }
                }
        );

        results = new LinkedList<>();
    }

    @Override
    public void process(final TestModel data) {

        log.info("Processing test data: START");

        repository.setFlag(data.flag.getFeature(), data.flag);

        final List<Segment> segments = data.segments;
        if (segments!=null) {

            for (final Segment segment : segments) {

                repository.setSegment(segment.getIdentifier(), segment);
            }
        }

        for (final String key : data.expected.keySet()) {

            final boolean expected = data.expected.get(key);

            final TestResult result = new TestResult(

                    data.testFile,
                    key,
                    expected,
                    data
            );

            Assert.assertTrue(results.add(result));
        }

        for (final TestResult result : results) {

            log.info(

                    String.format(

                            "Use case '%s' with target '%s' and expected value '%b'",
                            result.file,
                            result.targetIdentifier,
                            result.value
                    )
            );


        }

        log.info("Processing test data: END");
    }
}
