package egain.oassdk.testgenerators.scenario.source;

import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.testgenerators.scenario.ScenarioDocument;

import java.util.List;

/**
 * Pluggable source of acceptance / test scenarios (Jira by default).
 */
public interface ScenarioSource {

    /**
     * Fetch scenario documents for AI test generation.
     *
     * @param request source-specific request (JQL, limits, credentials env)
     * @return scenario documents (never null)
     * @throws OASSDKException if the source cannot be contacted or returns an error
     */
    List<ScenarioDocument> fetch(ScenarioSourceRequest request) throws OASSDKException;
}
