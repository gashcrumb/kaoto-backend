package io.kaoto.backend.api.resource.v1;

import io.kaoto.backend.api.metadata.catalog.StepCatalog;
import io.kaoto.backend.api.resource.support.NativeHelper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class IntegrationsResourceIT extends IntegrationsResourceTestAbstract {
    @Override
    protected void waitForWarmUpCatalog() {
        NativeHelper.waitForWarmUpCatalog(StepCatalog.class.getCanonicalName());
    }
}
