package egain.ws.oas;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import jakarta.ws.rs.core.MultivaluedMap;

public record RequestInfo(String url, String httpMethod, Multimap<String, String> queryParameters,
                          Multimap<String, String> pathParameters) {

	public RequestInfo(String url, String httpMethod, MultivaluedMap<String, String> queryParameters,
                       MultivaluedMap<String, String> pathParameters)
	{
		this(url, httpMethod, convertMultivaluedMapToMultimap(queryParameters),
			convertMultivaluedMapToMultimap(pathParameters));
	}

	private static Multimap<String, String> convertMultivaluedMapToMultimap(MultivaluedMap<String, String> multivaluedMap)
	{
		Multimap<String, String> map = MultimapBuilder.linkedHashKeys().arrayListValues().build();
		multivaluedMap.forEach(map::putAll);
		return map;
	}

	@Override
	public String toString()
	{
		return "uriInfoHolder[" +
			"url=" + url + ", " +
			"httpMethod=" + httpMethod + ", " +
			"queryParameters=" + queryParameters + ", " +
			"pathParameters=" + pathParameters + ']';
	}

}
