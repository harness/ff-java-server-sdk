package io.harness.cf.client.api.evaluator;

import io.harness.cf.client.api.CaffeineCache;
import io.harness.cf.client.api.Repository;
import io.harness.cf.client.api.RepositoryCallback;
import io.harness.cf.client.api.StorageRepository;
import io.harness.cf.client.common.Cache;
import io.harness.cf.model.Segment;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
class EvaluatorTester implements EvaluatorTesting {

    private final Cache cache;
    private final Repository repository;

    {

        cache = new CaffeineCache(10000);
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
    }

    @Override
    public void process(final TestModel data) {

        log.info("Processing test data: START");

        repository.setFlag(data.flag.getFeature(), data.flag);

        List<Segment> segments = data.segments;
        if (segments!=null) {

            for (final Segment segment : segments) {

                repository.setSegment(segment.getIdentifier(), segment);
            }
        }

        log.info("Processing test data: END");
    }
}
