package egain.framework.validation;

import java.util.List;

public class ValidationErrorHelper
{
	public static ValidationError createValidationError(String l10nFilePath, String l10NKey, List<String> arguments, List<String> localizedArgs)
	{
		ValidationError validationError = new ValidationError(l10NKey);
		arguments.forEach(validationError::argument);
		localizedArgs.forEach(localizedArg -> validationError.localizedArgument(l10nFilePath, localizedArg));
		return validationError;
	}
}
