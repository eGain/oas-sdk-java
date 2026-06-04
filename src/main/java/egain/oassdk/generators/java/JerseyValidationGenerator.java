package egain.oassdk.generators.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generates all parameter validation classes (IsRequiredValidator, PatternValidator, etc.).
 * Extracted from JerseyGenerator to keep that class focused on orchestration.
 */
class JerseyValidationGenerator {

    private final JerseyGenerationContext ctx;

    JerseyValidationGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Entry point: generate every validation class into the appropriate output directory.
     */
    void generate() throws IOException {
        generateValidationClasses(ctx.outputDir, ctx.packageName);
    }

    private void generateValidationClasses(String outputDir, String packageName) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String validationPackage = packageName != null ? packageName : "egain.ws.oas.validation";
        String packagePath = validationPackage.replace(".", "/");
        String sourceRoot = outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/");
        String validationDir = sourceRoot + packagePath;

        // Ensure target directory exists
        Files.createDirectories(Paths.get(validationDir));

        // Generate all validator classes
        generateIsRequiredValidator(validationDir, validationPackage);
        generatePatternValidator(validationDir, validationPackage);
        generateMaxLengthValidator(validationDir, validationPackage);
        generateMinLengthValidator(validationDir, validationPackage);
        generateNumericMaxValidator(validationDir, validationPackage);
        generateNumericMinValidator(validationDir, validationPackage);
        generateNumericMultipleOfValidator(validationDir, validationPackage);
        generateEnumValidator(validationDir, validationPackage);
        generateBooleanValidator(validationDir, validationPackage);
        generateFormatValidator(validationDir, validationPackage);
        generateAllowedParameterValidator(validationDir, validationPackage);
        generateArrayMaxItemsValidators(validationDir, validationPackage);
        generateArrayMinItemsValidator(validationDir, validationPackage);
        generateArrayUniqueItemsValidators(validationDir, validationPackage);
        generateArraySimpleStyleValidator(validationDir, validationPackage);
        generateIsAllowEmptyValueValidator(validationDir, validationPackage);
        generateIsAllowReservedValidator(validationDir, validationPackage);
    }

    /**
     * Generate IsRequiredValidator class
     */
    private void generateIsRequiredValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;

                public class IsRequiredValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;

                    private final String requiredParameter;

                    private final String nameSpace;
                    private final boolean isArray;

                    public IsRequiredValidator(String parameterName, String requiredParameter, String l10nKey, List<String> arguments,
                        List<String> localizedArguments,
                        String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.requiredParameter = requiredParameter;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        if (nameSpace.equalsIgnoreCase("path") && !val.pathParameters().containsKey(requiredParameter))
                        {
                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                        }
                        if (nameSpace.equalsIgnoreCase("query") && !val.queryParameters().containsKey(requiredParameter))
                        {
                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/IsRequiredValidator.java", content);
    }

    /**
     * Generate PatternValidator class
     */
    private void generatePatternValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class PatternValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String val;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public PatternValidator(String parameterName, String val, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.val = val;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            if (isArray)
                            {
                                String[] items = input.split(",");
                                for (String item : items)
                                {
                                    if (!Validations.matchesPattern.apply(item, this.val))
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                            else
                            {
                                if (!Validations.matchesPattern.apply(input, this.val))
                                {
                                    return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                }
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/PatternValidator.java", content);
    }

    /**
     * Generate MaxLengthValidator class
     */
    private void generateMaxLengthValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class MaxLengthValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String maxLength;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public MaxLengthValidator(String parameterName, String maxLength, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.maxLength = maxLength;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                int maxLen = Integer.parseInt(maxLength);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        if (item.length() > maxLen)
                                        {
                                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    if (input.length() > maxLen)
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid maxLength value, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/MaxLengthValidator.java", content);
    }

    /**
     * Generate MinLengthValidator class
     */
    private void generateMinLengthValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class MinLengthValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String minLength;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public MinLengthValidator(String parameterName, String minLength, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.minLength = minLength;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                int minLen = Integer.parseInt(minLength);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        if (item.length() < minLen)
                                        {
                                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    if (input.length() < minLen)
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid minLength value, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/MinLengthValidator.java", content);
    }

    /**
     * Generate NumericMaxValidator class
     */
    private void generateNumericMaxValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class NumericMaxValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
					private final String val;
					private final String l10nKey;
					private final List<String> arguments;
					private final List<String> localizedArgs;
					private final String nameSpace;
					private final boolean isExclusive;
					private final boolean isArray;

					public NumericMaxValidator(String parameterName, String val, String l10nKey, List<String> arguments,
						List<String> localizedArgs, String nameSpace, boolean isExclusive, boolean isArray)
					{
						this.parameterName = parameterName;
						this.val = val;
						this.l10nKey = l10nKey;
						this.arguments = new ArrayList<>(arguments);
						this.localizedArgs = new ArrayList<>(localizedArgs);
						this.nameSpace = nameSpace;
						this.isExclusive = isExclusive;
						this.isArray = isArray;
					}

					@Override
					public ValidationError call(RequestInfo val)
					{
						String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
							: Validations.getPathParameterValue.apply(val, parameterName);
						if (input == null)
						{
							return null;
						}

						if (isArray)
						{
							String[] items = input.split(",");
							for (String item : items)
							{
								if (isExclusive && Validations.isGreaterThanOrEqualTo.apply(item, this.val))
								{
									return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
								}
								else if (!isExclusive && Validations.isGreaterThan.apply(item, this.val))
								{
									return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
								}
							}
						}
						else
						{
							if (isExclusive && Validations.isGreaterThanOrEqualTo.apply(input, this.val))
							{
								return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
							}
							else if (!isExclusive && Validations.isGreaterThan.apply(input, this.val))
							{
								return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
							}
						}
						return null;
					}
                }
                """, packageName);

        writeFile(outputDir + "/NumericMaxValidator.java", content);
    }

    /**
     * Generate NumericMinValidator class
     */
    private void generateNumericMinValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class NumericMinValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
					private final String val;
					private final String l10nKey;
					private final List<String> arguments;
					private final List<String> localizedArgs;
					private final String nameSpace;
					private final boolean isExclusive;
					private final boolean isArray;

					public NumericMinValidator(String parameterName, String val, String l10nKey, List<String> arguments,
						List<String> localizedArgs, String nameSpace, boolean isExclusive, boolean isArray)
					{
						this.parameterName = parameterName;
						this.val = val;
						this.l10nKey = l10nKey;
						this.arguments = new ArrayList<>(arguments);
						this.localizedArgs = new ArrayList<>(localizedArgs);
						this.nameSpace = nameSpace;
						this.isExclusive = isExclusive;
						this.isArray = isArray;
					}

					@Override
					public ValidationError call(RequestInfo val)
					{
						String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
							: Validations.getPathParameterValue.apply(val, parameterName);
						if (input == null)
						{
							return null;
						}

						if (isArray)
						{
							String[] items = input.split(",");
							for (String item : items)
							{
								if (isExclusive && Validations.isLessThanOrEqualTo.apply(item, this.val))
								{
									return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
								}
								else if (!isExclusive && Validations.isLessThan.apply(item, this.val))
								{
									return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
								}
							}
						}
						else
						{
							if (isExclusive && Validations.isLessThanOrEqualTo.apply(input, this.val))
							{
								return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
							}
							else if (!isExclusive && Validations.isLessThan.apply(input, this.val))
							{
								return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
							}
						}
						return null;
					}
                }
                """, packageName);

        writeFile(outputDir + "/NumericMinValidator.java", content);
    }

    /**
     * Generate NumericMultipleOfValidator class
     */
    private void generateNumericMultipleOfValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class NumericMultipleOfValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String multipleOf;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public NumericMultipleOfValidator(String parameterName, String multipleOf, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.multipleOf = multipleOf;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            try {
                                double multiple = Double.parseDouble(multipleOf);
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        double numericValue = Double.parseDouble(item.trim());
                                        if (Math.abs(numericValue %% multiple) > 0.0001)
                                        {
                                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    double numericValue = Double.parseDouble(input);
                                    if (Math.abs(numericValue %% multiple) > 0.0001)
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Invalid number format, skip validation
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        // Replace %% with % for modulo operation
        content = content.replace("%%", "%");
        writeFile(outputDir + "/NumericMultipleOfValidator.java", content);
    }

    /**
     * Generate EnumValidator class
     */
    private void generateEnumValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class EnumValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String enumValues;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public EnumValidator(String parameterName, String enumValues, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.enumValues = enumValues;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            List<String> allowedValues = Arrays.asList(enumValues.split(","));
                            if (isArray)
                            {
                                String[] items = input.split(",");
                                for (String item : items)
                                {
                                    if (!allowedValues.contains(item.trim()))
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                            else
                            {
                                if (!allowedValues.contains(input.trim()))
                                {
                                    return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                }
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/EnumValidator.java", content);
    }

    /**
     * Generate BooleanValidator class
     */
    private void generateBooleanValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Locale;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class BooleanValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public BooleanValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            if (isArray)
                            {
                                String[] items = input.split(",");
                                for (String item : items)
                                {
                                    String trimmed = item.trim().toLowerCase(Locale.ROOT);
                                    if (!"true".equals(trimmed) && !"false".equals(trimmed))
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                            else
                            {
                                String trimmed = input.trim().toLowerCase(Locale.ROOT);
                                if (!"true".equals(trimmed) && !"false".equals(trimmed))
                                {
                                    return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                }
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/BooleanValidator.java", content);
    }

    /**
     * Generate FormatValidator class
     */
    private void generateFormatValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.regex.Pattern;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class FormatValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String format;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$");
                    private static final Pattern URI_PATTERN = Pattern.compile("^https?://.*");
                    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

                    private static final long INT32_MIN = -2147483648L;
                    private static final long INT32_MAX = 2147483647L;

                    public FormatValidator(String parameterName, String format, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.format = format;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            Pattern pattern = getPatternForFormat(format);
                            if (pattern != null)
                            {
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        if (!pattern.matcher(item.trim()).matches())
                                        {
                                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    if (!pattern.matcher(input.trim()).matches())
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                            else if (isNumericFormat(format))
                            {
                                if (isArray)
                                {
                                    String[] items = input.split(",");
                                    for (String item : items)
                                    {
                                        if (!validateNumericFormat(format, item.trim()))
                                        {
                                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                        }
                                    }
                                }
                                else
                                {
                                    if (!validateNumericFormat(format, input.trim()))
                                    {
                                        return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                    }
                                }
                            }
                        }
                        return null;
                    }

                    private boolean isNumericFormat(String format)
                    {
                        if (format == null) return false;
                        String f = format.toLowerCase();
                        return "int32".equals(f) || "int64".equals(f) || "float".equals(f) || "double".equals(f);
                    }

                    private boolean validateNumericFormat(String format, String input)
                    {
                        if (input == null || input.isEmpty()) return true;
                        try
                        {
                            switch (format.toLowerCase())
                            {
                                case "int32" ->
                                {
                                    long v = Long.parseLong(input);
                                    return v >= INT32_MIN && v <= INT32_MAX;
                                }
                                case "int64" ->
                                {
                                    Long.parseLong(input);
                                    return true;
                                }
                                case "float" ->
                                {
                                    Float.parseFloat(input);
                                    return true;
                                }
                                case "double" ->
                                {
                                    Double.parseDouble(input);
                                    return true;
                                }
                                default -> { return true; }
                            }
                        }
                        catch (NumberFormatException e)
                        {
                            return false;
                        }
                    }

                    private Pattern getPatternForFormat(String format)
                    {
                        if ("email".equalsIgnoreCase(format))
                        {
                            return EMAIL_PATTERN;
                        }
                        else if ("uri".equalsIgnoreCase(format) || "url".equalsIgnoreCase(format))
                        {
                            return URI_PATTERN;
                        }
                        else if ("uuid".equalsIgnoreCase(format))
                        {
                            return UUID_PATTERN;
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/FormatValidator.java", content);
    }

    /**
     * Generate AllowedParameterValidator class
     */
    private void generateAllowedParameterValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;

                public class AllowedParameterValidator implements ValidatorAction<RequestInfo>
                {
                    private final List<String> allowedParameters;

                    public AllowedParameterValidator(List<String> allowedParameters)
                    {
                        this.allowedParameters = allowedParameters;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        for (String param : val.queryParameters().keySet())
                        {
                            if (!allowedParameters.contains(param))
                            {
                                return ValidationErrorHelper.createValidationError("", "L10N_INVALID_QUERY_PARAMETER",
                                    List.of(param), List.of());
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/AllowedParameterValidator.java", content);
    }

    /**
     * Generate ArrayMaxItemsValidators class
     */
    private void generateArrayMaxItemsValidators(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class ArrayMaxItemsValidators implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
					private final String val;
					private final String l10nKey;
					private final List<String> arguments;
					private final List<String> localizedArgs;
					private final String nameSpace;
					private final boolean isArray;

					public ArrayMaxItemsValidators(String parameterName, String val, String l10nKey, List<String> arguments,
						List<String> localizedArgs, String nameSpace, boolean isArray)
					{
						this.parameterName = parameterName;
						this.val = val;
						this.l10nKey = l10nKey;
						this.arguments = new ArrayList<>(arguments);
						this.localizedArgs = new ArrayList<>(localizedArgs);
						this.nameSpace = nameSpace;
						this.isArray = isArray;
					}

					@Override
					public ValidationError call(RequestInfo val)
					{
						String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
										: Validations.getPathParameterValue.apply(val, parameterName);
						if (input != null && !Validations.hasMaxItems.apply(input.split(","),
										this.val))
						{
							return ValidationErrorHelper.createValidationError("", l10nKey,
											arguments,
											localizedArgs);
						}
						return null;
					}
                }
                """, packageName);

        writeFile(outputDir + "/ArrayMaxItemsValidators.java", content);
    }

    /**
     * Generate ArrayMinItemsValidator class
     */
    private void generateArrayMinItemsValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class ArrayMinItemsValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
					private final String val;
					private final String l10nKey;
					private final List<String> arguments;
					private final List<String> localizedArgs;
					private final String nameSpace;
					private final boolean isArray;

					public ArrayMinItemsValidator(String parameterName, String val, String l10nKey, List<String> arguments,
						List<String> localizedArgs, String nameSpace, boolean isArray)
					{
						this.parameterName = parameterName;
						this.val = val;
						this.l10nKey = l10nKey;
						this.arguments = new ArrayList<>(arguments);
						this.localizedArgs = new ArrayList<>(localizedArgs);
						this.nameSpace = nameSpace;
						this.isArray = isArray;
					}

					@Override
					public ValidationError call(RequestInfo val)
					{
						String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
										: Validations.getPathParameterValue.apply(val, parameterName);
						if (input != null && !Validations.hasMinItems.apply(input.split(","),
										this.val))
						{
							return ValidationErrorHelper.createValidationError("", l10nKey,
											arguments,
											localizedArgs);
						}
						return null;
					}
                }
                """, packageName);

        writeFile(outputDir + "/ArrayMinItemsValidator.java", content);
    }

    /**
     * Generate ArrayUniqueItemsValidators class
     */
    private void generateArrayUniqueItemsValidators(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.HashSet;
                import java.util.List;
                import java.util.Set;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class ArrayUniqueItemsValidators implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public ArrayUniqueItemsValidators(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null)
                        {
                            String[] items = input.split(",");
                            Set<String> seen = new HashSet<>();
                            for (String item : items)
                            {
                                String trimmed = item.trim();
                                if (seen.contains(trimmed))
                                {
                                    return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                                }
                                seen.add(trimmed);
                            }
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/ArrayUniqueItemsValidators.java", content);
    }

    /**
     * Generate ArraySimpleStyleValidator class
     */
    private void generateArraySimpleStyleValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;

                public class ArraySimpleStyleValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public ArraySimpleStyleValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        // Simple style validation - currently a placeholder
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/ArraySimpleStyleValidator.java", content);
    }

    /**
     * Generate IsAllowEmptyValueValidator class
     */
    private void generateIsAllowEmptyValueValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;
                import egain.ws.oas.Validations;

                public class IsAllowEmptyValueValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public IsAllowEmptyValueValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        String input = this.nameSpace.equalsIgnoreCase("query") ? Validations.getQueryParameterValue.apply(val, parameterName)
                            : Validations.getPathParameterValue.apply(val, parameterName);
                        if (input != null && input.trim().isEmpty())
                        {
                            return ValidationErrorHelper.createValidationError("", l10nKey, arguments, localizedArgs);
                        }
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/IsAllowEmptyValueValidator.java", content);
    }

    /**
     * Generate IsAllowReservedValidator class
     */
    private void generateIsAllowReservedValidator(String outputDir, String packageName) throws IOException {
        String content = String.format("""
                package %s;

                import java.util.ArrayList;
                import java.util.List;

                import egain.framework.validation.ValidationError;
                import egain.framework.validation.ValidationErrorHelper;
                import egain.framework.validation.ValidatorAction;

                import egain.ws.oas.RequestInfo;

                public class IsAllowReservedValidator implements ValidatorAction<RequestInfo>
                {
                    private final String parameterName;
                    private final String l10nKey;
                    private final List<String> arguments;
                    private final List<String> localizedArgs;
                    private final String nameSpace;
                    private final boolean isArray;

                    public IsAllowReservedValidator(String parameterName, String l10nKey, List<String> arguments,
                        List<String> localizedArguments, String nameSpace, boolean isArray)
                    {
                        this.parameterName = parameterName;
                        this.l10nKey = l10nKey;
                        this.arguments = new ArrayList<>(arguments);
                        this.localizedArgs = new ArrayList<>(localizedArguments);
                        this.nameSpace = nameSpace;
                        this.isArray = isArray;
                    }

                    @Override
                    public ValidationError call(RequestInfo val)
                    {
                        // Reserved character validation - currently a placeholder
                        return null;
                    }
                }
                """, packageName);

        writeFile(outputDir + "/IsAllowReservedValidator.java", content);
    }

    /**
     * Generate the runtime support classes (RequestInfo, Validations) into the fixed
     * {@code egain.ws.oas} package that every generated validator imports from.
     *
     * <p>These are emitted regardless of the {@code modelsOnly} flag — the orchestrator calls this
     * unconditionally so the support classes are always present, while the validators themselves
     * are only generated in full (non-models-only) mode.
     */
    public void generateSupportClasses() throws IOException {
        if (ctx.outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String sourceRoot = ctx.outputDir + (ctx.modelsOnly ? "/" : "/src/main/java/");
        String supportDir = sourceRoot + "egain/ws/oas";

        // RequestInfo's only spec-independent variation is the javax/jakarta namespace prefix.
        String requestInfo = JerseyGenerationContext
                .readRuntimeResource("runtime/jersey/egain/ws/oas/RequestInfo.java")
                .replace("__WS_NS__", ctx.getWsNs());
        writeFile(supportDir + "/RequestInfo.java", requestInfo);

        String validations = JerseyGenerationContext
                .readRuntimeResource("runtime/jersey/egain/ws/oas/Validations.java");
        writeFile(supportDir + "/Validations.java", validations);
    }

    private void writeFile(String filePath, String content) throws IOException {
        JerseyGenerationContext.writeFile(filePath, content);
    }
}
