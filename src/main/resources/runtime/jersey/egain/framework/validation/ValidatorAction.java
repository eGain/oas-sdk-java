package egain.framework.validation;

public interface ValidatorAction<T>
{
	ValidationError call(T input);
}
