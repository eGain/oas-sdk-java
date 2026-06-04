package __PACKAGE__.observability;

import __INJECT_NS__.Inject;
import __INJECT_NS__.Singleton;
import __WS_NS__.GET;
import __WS_NS__.Path;
import __WS_NS__.Produces;

@Path("/metrics")
@Singleton
public class MetricsEndpoint {

    @Inject
    private MetricsFilter metricsFilter;

    @GET
    @Produces("text/plain")
    public String scrape() {
        return metricsFilter.getRegistry().scrape();
    }
}
