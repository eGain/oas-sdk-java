package egain.framework.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import egain.framework.validation.data.L10NResource;

public class ValidationError
{
	private final String l10nKey;

	public List<L10NResource> getErrorArguments() {
		return errorArguments;
	}

	private final List<L10NResource> errorArguments = new ArrayList<>();

	public String getL10nKey() {
		return l10nKey;
	}

	protected ValidationError(String l10nKey)
	{
		this.l10nKey = l10nKey;
	}

	protected void argument(String argument)
	{
		errorArguments.add(new L10NResource(argument));
	}

	protected void localizedArgument(String l10nFilePath, String localizedArgument)
	{
		errorArguments.add(new L10NResource(l10nFilePath, localizedArgument, true));
	}
}
