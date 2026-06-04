package egain.framework.validation;

public class ValidationBuilder<T>
{
	private final Validator<T> validator = new Validator<>();

	public ValidationBuilder<T> add(ValidatorAction<T> validatorAction)
	{
		validator.add(validatorAction);
		return this;
	}

	public Validator<T> build()
	{
		return validator;
	}
}
