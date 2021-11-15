package io.harness.cf.client.api.test.evaluator;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.*;
import io.harness.cf.client.common.Cache;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import io.harness.cf.model.Variation;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;

import java.util.LinkedList;
import java.util.List;

@Slf4j
public class EvaluatorTester implements EvaluatorTesting {

    private final String noTarget;
    private final Evaluation evaluator;
    private final Repository repository;
    private final List<TestResult> results;

    private final FlagEvaluateCallback flagEvaluateCallback = new FlagEvaluateCallback() {

        @Override
        public void processEvaluation(

                @NonNull FeatureConfig featureConfig,
                Target target,
                @NonNull Variation variation
        ) {

            log.info(

                    String.format(

                            "processEvaluation: '%s', '%s', '%s'",
                            featureConfig.getFeature(),
                            target != null ? target.getName() : noTarget,
                            variation.getValue()
                    )
            );
        }
    };

    {

        noTarget = "_no_target";

        final Cache cache = new CaffeineCache(10000);

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
        evaluator = new Evaluator(repository);
    }

    @Override
    public void process(final TestModel data) {

        log.info(

                String.format(

                        "Processing the test data '%s' started",
                        data.testFile
                )
        );

        repository.setFlag(data.flag.getFeature(), data.flag);

        final List<Segment> segments = data.segments;
        if (segments != null) {

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

            Target target = null;
            if (!noTarget.equals(result.targetIdentifier)) {

                if (result.useCase.targets != null) {

                    for (final Target item : result.useCase.targets) {

                        if (item != null &&
                                item.getIdentifier().equals(result.targetIdentifier)) {

                            target = item;
                            break;
                        }
                    }
                }
            }

            Object received = null;
            switch (result.useCase.flag.getKind()) {

                case BOOLEAN:

                    received = evaluator.boolVariation(

                            result.useCase.flag.getFeature(),
                            target,
                            false,
                            flagEvaluateCallback
                    );
                    break;

                case STRING:

                    received = evaluator.stringVariation(

                            result.useCase.flag.getFeature(),
                            target,
                            "",
                            flagEvaluateCallback
                    );
                    break;

                case INT:

                    received = evaluator.numberVariation(

                            result.useCase.flag.getFeature(),
                            target,
                            0,
                            flagEvaluateCallback
                    );
                    break;

                case JSON:

                    received = evaluator.jsonVariation(

                            result.useCase.flag.getFeature(),
                            target,
                            new JsonObject(),
                            flagEvaluateCallback
                    );
                    break;
            }

            Assert.assertEquals(result.value, received);
        }

        log.info(

                String.format(

                        "Processing the test data '%s' completed",
                        data.testFile
                )
        );
    }
}
