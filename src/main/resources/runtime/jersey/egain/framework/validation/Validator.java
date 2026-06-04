package egain.framework.validation;

import java.util.ArrayList;
import java.util.List;

public class Validator<T>
{

	private final List<ValidatorAction<T>> validatorActions = new ArrayList<>();

	protected Validator()
	{

	}

	protected void add(ValidatorAction<T> validatorAction)
	{
		validatorActions.add(validatorAction);
	}

	public List<ValidationError> validate(T input)
	{
		List<ValidationError> validationErrors = new ArrayList<>();
		for (ValidatorAction<T> validatorAction : validatorActions)
		{
			ValidationError validationError = validatorAction.call(input);
			if (validationError != null)
			{
				validationErrors.add(validationError);
				break;
			}
		}
		return validationErrors;
	}
}
