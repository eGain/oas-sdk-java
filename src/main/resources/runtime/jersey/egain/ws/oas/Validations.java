package egain.ws.oas;

import com.google.common.base.Objects;

import java.util.Arrays;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class Validations
{
	public static final Pattern RESERVED_CHARACTERS = Pattern.compile("[:/?#\\[\\]@!$&'()*+,;=]");
	// For numerical attributes
	public static final BiFunction<String, String, Boolean> isGreaterThanOrEqualTo = (value, min) -> {
		try
		{
			Double numericValue = Double.parseDouble(value);
			Double minValue = Double.parseDouble(min);
			return numericValue >= minValue;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String, String, Boolean> isGreaterThan = (value, min) -> {
		try
		{
			Double numericValue = Double.parseDouble(value);
			Double minValue = Double.parseDouble(min);
			return numericValue > minValue;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String, String, Boolean> isLessThanOrEqualTo = (value, max) -> {
		try
		{
			Double numericValue = Double.parseDouble(value);
			Double maxValue = Double.parseDouble(max);
			return numericValue <= maxValue;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String, String, Boolean> isLessThan = (value, max) -> {
		try
		{
			Double numericValue = Double.parseDouble(value);
			Double maxValue = Double.parseDouble(max);
			return numericValue < maxValue;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String, String, Boolean> isMultipleOf = (value, divisor) -> {
		try
		{
			Double numericValue = Double.parseDouble(value);
			Double divisorValue = Double.parseDouble(divisor);
			return divisorValue != 0 && numericValue % divisorValue == 0;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	// For string attributes
	public static final BiFunction<String, String, Boolean> hasMinLength = (string, minLength) -> {
		try
		{
			int min = Integer.parseInt(minLength);
			return string != null && string.length() < min;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String, String, Boolean> hasMaxLength = (string, maxLength) -> {
		try
		{
			int max = Integer.parseInt(maxLength);
			return string != null && string.length() > max;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String, String, Boolean> matchesPattern = (string, regex) -> {
		if (string == null || regex == null)
			return false;
		Pattern pattern = Pattern.compile(regex);
		return pattern.matcher(string).matches();
	};
	// For array attributes (size-based checks)
	public static final BiFunction<String[], String, Boolean> hasMinItems = (array, minItems) -> {
		try
		{
			int min = Integer.parseInt(minItems);
			return array != null && array.length >= min;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String[], String, Boolean> hasMaxItems = (array, maxItems) -> {
		try
		{
			int max = Integer.parseInt(maxItems);
			return array.length <= max;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	};
	public static final BiFunction<String[], String, Boolean> hasUniqueItems = (array, uniqueRequired) -> {
		if (array == null || !Boolean.parseBoolean(uniqueRequired))
			return true;
		return array.length == Arrays.stream(array).distinct().count();
	};
	// For enums
	public static final BiFunction<String, String, Boolean> isValueInEnum = (value, input) -> {
		if (value == null || input == null)
			return false;
		Set<String> enumSet = Arrays.stream(input.split(",\\s*")).collect(
			java.util.stream.Collectors.toSet());
		return enumSet.contains(value);
	};

	// For boolean attributes
	public static final BiFunction<String, String, Boolean> isBoolean = (value, ignored) -> {
		if (value == null)
			return false;
		return !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value));
	};
	public static final BiFunction<RequestInfo, String, String> getQueryParameterValue = (holder,
		paramName) -> holder.queryParameters().get(paramName).stream().findFirst().orElse(null);
	public static final BiFunction<RequestInfo, String, String> getPathParameterValue = (holder,
		paramName) -> holder.pathParameters().get(paramName).stream().findFirst().orElse(null);
	
	public record ParameterValidatorMapKey(String url, String httpMethod) {
		@Override
		public boolean equals(Object o)
		{
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			ParameterValidatorMapKey that = (ParameterValidatorMapKey) o;
			return Objects.equal(url, that.url) && Objects.equal(httpMethod, that.httpMethod);
		}

		@Override
		public int hashCode()
		{
			return Objects.hashCode(url, httpMethod);
		}
	}

	public record Parameter(String name, String nameSpace, boolean isRequired, boolean isAllowEmptyValue,
					boolean isAllowReserved) {
	}
}
