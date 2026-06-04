package egain.framework.validation;

public class ValidationErrorBuilder
{
	private final ValidationError validationError;
	
	public ValidationErrorBuilder(String l10nKey)
	{
		validationError = new ValidationError(l10nKey);
	}
	
	public ValidationError build()
	{
		return validationError;
	}
	
	public ValidationErrorBuilder argument(String argument)
	{
		validationError.argument(argument);
		return this;
	}
	
	public ValidationErrorBuilder localizedArgument(String l10nFilePath, String localizedArgument)
	{
		validationError.localizedArgument(l10nFilePath, localizedArgument);
		return this;
	}
}
