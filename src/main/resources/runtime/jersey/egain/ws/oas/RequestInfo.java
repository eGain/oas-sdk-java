package egain.ws.oas;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import __WS_NS__.core.MultivaluedMap;

public record RequestInfo(String url, String httpMethod, Multimap<String, String> queryParameters,
                          Multimap<String, String> pathParameters,
                          Multimap<String, String> headerParameters) {

	/**
	 * Backward-compatible constructor without headers (defaults to empty header map).
	 */
	public RequestInfo(String url, String httpMethod, Multimap<String, String> queryParameters,
                       Multimap<String, String> pathParameters)
	{
		this(url, httpMethod, queryParameters, pathParameters, emptyMultimap());
	}

	public RequestInfo(String url, String httpMethod, MultivaluedMap<String, String> queryParameters,
                       MultivaluedMap<String, String> pathParameters)
	{
		this(url, httpMethod, convertMultivaluedMapToMultimap(queryParameters),
			convertMultivaluedMapToMultimap(pathParameters), emptyMultimap());
	}

	public RequestInfo(String url, String httpMethod, MultivaluedMap<String, String> queryParameters,
                       MultivaluedMap<String, String> pathParameters,
                       MultivaluedMap<String, String> headerParameters)
	{
		this(url, httpMethod, convertMultivaluedMapToMultimap(queryParameters),
			convertMultivaluedMapToMultimap(pathParameters),
			convertMultivaluedMapToMultimap(headerParameters));
	}

	private static Multimap<String, String> emptyMultimap()
	{
		return MultimapBuilder.linkedHashKeys().arrayListValues().build();
	}

	private static Multimap<String, String> convertMultivaluedMapToMultimap(MultivaluedMap<String, String> multivaluedMap)
	{
		Multimap<String, String> map = emptyMultimap();
		if (multivaluedMap != null)
		{
			multivaluedMap.forEach(map::putAll);
		}
		return map;
	}

	@Override
	public String toString()
	{
		return "uriInfoHolder[" +
			"url=" + url + ", " +
			"httpMethod=" + httpMethod + ", " +
			"queryParameters=" + queryParameters + ", " +
			"pathParameters=" + pathParameters + ", " +
			"headerParameters=" + headerParameters + ']';
	}

}
