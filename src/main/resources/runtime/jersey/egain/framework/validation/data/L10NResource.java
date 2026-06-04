package egain.framework.validation.data;

/**
 * This class contains information to localize a L10N key.
 *
 */
public class L10NResource
{
	private String l10nFilePath;
	private final String l10nKey;

	// set to true if 'key' should be localized, else false
	private boolean isLocalize = false;

	public L10NResource(String l10nFilePath, String l10nKey, boolean isLocalize)
	{
		this.l10nFilePath = l10nFilePath;
		this.l10nKey = l10nKey;
		this.isLocalize = isLocalize;
	}

	/**
	 * Use this constructor whenever using this Data object to populate a non-localized string. E.g.: Comma separated String of
	 * activity IDs. Since <code>WsErrorProducer</code> requires an array of <code>L10NResourceData</code> objects to be
	 * provided, it is quite possible that some of these are localized and some others are not. Using this constructor is same
	 * as using <code>L10NResourceData(String l10nFilePath, String l10nKey, boolean
	 * isLocalize)</code> with isLocalise as false.
	 *
	 * @param l10nKey:
	 *            String to be used.
	 */
	public L10NResource(String l10nKey)
	{
		this.l10nKey = l10nKey;
	}

	/**
	 * @return the l10nFilePath
	 */
	public String getL10nFilePath()
	{
		return l10nFilePath;
	}

	/**
	 * @return the l10nKey
	 */
	public String getL10nKey()
	{
		return l10nKey;
	}

	/**
	 * @return the isLocalize
	 */
	public boolean isLocalize()
	{
		return isLocalize;
	}
}
