package dev.apidocs.core.generation;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/** Fixed labels of the generated documents. English is the base bundle; Portuguese covers pt-*. */
public final class Messages {

    private final ResourceBundle bundle;
    private final Locale locale;

    private Messages(ResourceBundle bundle, Locale locale) {
        this.bundle = bundle;
        this.locale = locale;
    }

    public static Messages forLanguage(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag == null || languageTag.isBlank() ? "en" : languageTag);
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        return new Messages(bundle, locale);
    }

    /** Locale of the document language, for numbers written outside the bundle patterns. */
    public Locale locale() {
        return locale;
    }

    /** Arguments are formatted with {@link MessageFormat}; pass numbers as strings to avoid locale grouping. */
    public String get(String key, Object... args) {
        String pattern = bundle.getString(key);
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }
}
