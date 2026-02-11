package org.mozilla.javascript.regexp;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * Unicode properties handler for Java 11 Character class. Handles binary properties from ECMA-262
 * and general category values.
 */
public class UnicodeProperties {
    // Binary Property Names (from ECMA-262 table-binary-unicode-properties)
    public static final byte ALPHABETIC = 1;
    public static final byte ASCII = ALPHABETIC + 1;
    public static final byte CASE_IGNORABLE = ASCII + 1;
    public static final byte ASCII_HEX_DIGIT = CASE_IGNORABLE + 1;
    public static final byte HEX_DIGIT = ASCII_HEX_DIGIT + 1;
    public static final byte ID_CONTINUE = HEX_DIGIT + 1;
    public static final byte ID_START = ID_CONTINUE + 1;
    public static final byte LOWERCASE = ID_START + 1;
    public static final byte UPPERCASE = LOWERCASE + 1;
    public static final byte WHITE_SPACE = UPPERCASE + 1;
    public static final byte EMOJI = WHITE_SPACE + 1;
    public static final byte EMOJI_COMPONENT = EMOJI + 1;
    public static final byte EMOJI_MODIFIER = EMOJI_COMPONENT + 1;
    public static final byte EMOJI_MODIFIER_BASE = EMOJI_MODIFIER + 1;
    public static final byte EMOJI_PRESENTATION = EMOJI_MODIFIER_BASE + 1;
    public static final byte EXTENDED_PICTOGRAPHIC = EMOJI_PRESENTATION + 1;
    public static final byte ANY = EXTENDED_PICTOGRAPHIC + 1;
    public static final byte ASSIGNED = ANY + 1;
    public static final byte DEFAULT_IGNORABLE_CODE_POINT = ASSIGNED + 1;
    public static final byte CASED = DEFAULT_IGNORABLE_CODE_POINT + 1;
    public static final byte MATH = CASED + 1;
    public static final byte NONCHARACTER_CODE_POINT = MATH + 1;
    public static final byte DASH = NONCHARACTER_CODE_POINT + 1;
    public static final byte DIACRITIC = DASH + 1;
    public static final byte EXTENDER = DIACRITIC + 1;
    public static final byte GRAPHEME_BASE = EXTENDER + 1;
    public static final byte GRAPHEME_EXTEND = GRAPHEME_BASE + 1;
    public static final byte IDEOGRAPHIC = GRAPHEME_EXTEND + 1;
    public static final byte JOIN_CONTROL = IDEOGRAPHIC + 1;
    public static final byte PATTERN_SYNTAX = JOIN_CONTROL + 1;
    public static final byte PATTERN_WHITE_SPACE = PATTERN_SYNTAX + 1;
    public static final byte QUOTATION_MARK = PATTERN_WHITE_SPACE + 1;
    public static final byte RADICAL = QUOTATION_MARK + 1;
    public static final byte REGIONAL_INDICATOR = RADICAL + 1;
    public static final byte SENTENCE_TERMINAL = REGIONAL_INDICATOR + 1;
    public static final byte SOFT_DOTTED = SENTENCE_TERMINAL + 1;
    public static final byte TERMINAL_PUNCTUATION = SOFT_DOTTED + 1;
    public static final byte UNIFIED_IDEOGRAPH = TERMINAL_PUNCTUATION + 1;
    public static final byte VARIATION_SELECTOR = UNIFIED_IDEOGRAPH + 1;
    public static final byte XID_CONTINUE = VARIATION_SELECTOR + 1;
    public static final byte XID_START = XID_CONTINUE + 1;
    public static final byte BIDI_CONTROL = XID_START + 1;
    public static final byte BIDI_MIRRORED = BIDI_CONTROL + 1;
    public static final byte DEPRECATED = BIDI_MIRRORED + 1;
    public static final byte LOGICAL_ORDER_EXCEPTION = DEPRECATED + 1;
    public static final byte CHANGES_WHEN_CASEFOLDED = LOGICAL_ORDER_EXCEPTION + 1;
    public static final byte CHANGES_WHEN_CASEMAPPED = CHANGES_WHEN_CASEFOLDED + 1;
    public static final byte CHANGES_WHEN_LOWERCASED = CHANGES_WHEN_CASEMAPPED + 1;
    public static final byte CHANGES_WHEN_NFKC_CASEFOLDED = CHANGES_WHEN_LOWERCASED + 1;
    public static final byte CHANGES_WHEN_TITLECASED = CHANGES_WHEN_NFKC_CASEFOLDED + 1;
    public static final byte CHANGES_WHEN_UPPERCASED = CHANGES_WHEN_TITLECASED + 1;
    public static final byte IDS_BINARY_OPERATOR = CHANGES_WHEN_UPPERCASED + 1;
    public static final byte IDS_TRINARY_OPERATOR = IDS_BINARY_OPERATOR + 1;
    // String properties (v flag) - approximated as code point properties
    public static final byte RGI_EMOJI = IDS_TRINARY_OPERATOR + 1;

    // Non-binary properties
    public static final byte GENERAL_CATEGORY = RGI_EMOJI + 1;
    public static final byte SCRIPT = GENERAL_CATEGORY + 1;

    // Property Values for General Category (from PropertyValueAliases.txt)
    // OTHER
    public static final byte OTHER = 1;
    public static final byte CONTROL = OTHER + 1;
    public static final byte FORMAT = CONTROL + 1;
    public static final byte UNASSIGNED = FORMAT + 1;
    public static final byte PRIVATE_USE = UNASSIGNED + 1;
    public static final byte SURROGATE = PRIVATE_USE + 1;
    public static final byte LETTER = SURROGATE + 1;
    public static final byte LOWERCASE_LETTER = LETTER + 1;
    public static final byte MODIFIER_LETTER = LOWERCASE_LETTER + 1;
    public static final byte OTHER_LETTER = MODIFIER_LETTER + 1;
    public static final byte TITLECASE_LETTER = OTHER_LETTER + 1;
    public static final byte UPPERCASE_LETTER = TITLECASE_LETTER + 1;
    public static final byte MARK = UPPERCASE_LETTER + 1;
    public static final byte SPACING_MARK = MARK + 1;
    public static final byte ENCLOSING_MARK = SPACING_MARK + 1;
    public static final byte NONSPACING_MARK = ENCLOSING_MARK + 1;
    public static final byte NUMBER = NONSPACING_MARK + 1;
    public static final byte DECIMAL_NUMBER = NUMBER + 1;
    public static final byte LETTER_NUMBER = DECIMAL_NUMBER + 1;
    public static final byte OTHER_NUMBER = LETTER_NUMBER + 1;
    public static final byte PUNCTUATION = OTHER_NUMBER + 1;
    public static final byte CONNECTOR_PUNCTUATION = PUNCTUATION + 1;
    public static final byte DASH_PUNCTUATION = CONNECTOR_PUNCTUATION + 1;
    public static final byte CLOSE_PUNCTUATION = DASH_PUNCTUATION + 1;
    public static final byte FINAL_PUNCTUATION = CLOSE_PUNCTUATION + 1;
    public static final byte INITIAL_PUNCTUATION = FINAL_PUNCTUATION + 1;
    public static final byte OTHER_PUNCTUATION = INITIAL_PUNCTUATION + 1;
    public static final byte OPEN_PUNCTUATION = OTHER_PUNCTUATION + 1;
    public static final byte SYMBOL = OPEN_PUNCTUATION + 1;
    public static final byte CURRENCY_SYMBOL = SYMBOL + 1;
    public static final byte MODIFIER_SYMBOL = CURRENCY_SYMBOL + 1;
    public static final byte MATH_SYMBOL = MODIFIER_SYMBOL + 1;
    public static final byte OTHER_SYMBOL = MATH_SYMBOL + 1;
    public static final byte SEPARATOR = OTHER_SYMBOL + 1;
    public static final byte LINE_SEPARATOR = SEPARATOR + 1;
    public static final byte PARAGRAPH_SEPARATOR = LINE_SEPARATOR + 1;
    public static final byte SPACE_SEPARATOR = PARAGRAPH_SEPARATOR + 1;

    // Binary property values
    public static final byte TRUE = SPACE_SEPARATOR + 1;
    public static final byte FALSE = TRUE + 1;

    // Property Name Map (canonical names and aliases)
    public static final Map<String, Byte> PROPERTY_NAMES =
            Map.ofEntries(
                    Map.entry("Alphabetic", ALPHABETIC),
                    Map.entry("Alpha", ALPHABETIC),
                    Map.entry("ASCII", ASCII),
                    Map.entry("Case_Ignorable", CASE_IGNORABLE),
                    Map.entry("CI", CASE_IGNORABLE),
                    Map.entry("General_Category", GENERAL_CATEGORY),
                    Map.entry("gc", GENERAL_CATEGORY),
                    Map.entry("Script", SCRIPT),
                    Map.entry("sc", SCRIPT),
                    Map.entry("ASCII_Hex_Digit", ASCII_HEX_DIGIT),
                    Map.entry("AHex", ASCII_HEX_DIGIT),
                    Map.entry("Hex_Digit", HEX_DIGIT),
                    Map.entry("Hex", HEX_DIGIT),
                    Map.entry("ID_Continue", ID_CONTINUE),
                    Map.entry("IDC", ID_CONTINUE),
                    Map.entry("ID_Start", ID_START),
                    Map.entry("IDS", ID_START),
                    Map.entry("Lowercase", LOWERCASE),
                    Map.entry("Lower", LOWERCASE),
                    Map.entry("Uppercase", UPPERCASE),
                    Map.entry("Upper", UPPERCASE),
                    Map.entry("White_Space", WHITE_SPACE),
                    Map.entry("space", WHITE_SPACE),
                    Map.entry("Emoji", EMOJI),
                    Map.entry("Emoji_Component", EMOJI_COMPONENT),
                    Map.entry("EComp", EMOJI_COMPONENT),
                    Map.entry("Emoji_Modifier", EMOJI_MODIFIER),
                    Map.entry("EMod", EMOJI_MODIFIER),
                    Map.entry("Emoji_Modifier_Base", EMOJI_MODIFIER_BASE),
                    Map.entry("EBase", EMOJI_MODIFIER_BASE),
                    Map.entry("Emoji_Presentation", EMOJI_PRESENTATION),
                    Map.entry("EPres", EMOJI_PRESENTATION),
                    Map.entry("Extended_Pictographic", EXTENDED_PICTOGRAPHIC),
                    Map.entry("ExtPict", EXTENDED_PICTOGRAPHIC),
                    Map.entry("Any", ANY),
                    Map.entry("Assigned", ASSIGNED),
                    Map.entry("Default_Ignorable_Code_Point", DEFAULT_IGNORABLE_CODE_POINT),
                    Map.entry("DI", DEFAULT_IGNORABLE_CODE_POINT),
                    Map.entry("Cased", CASED),
                    Map.entry("Math", MATH),
                    Map.entry("Noncharacter_Code_Point", NONCHARACTER_CODE_POINT),
                    Map.entry("NChar", NONCHARACTER_CODE_POINT),
                    Map.entry("Dash", DASH),
                    Map.entry("Diacritic", DIACRITIC),
                    Map.entry("Dia", DIACRITIC),
                    Map.entry("Extender", EXTENDER),
                    Map.entry("Ext", EXTENDER),
                    Map.entry("Grapheme_Base", GRAPHEME_BASE),
                    Map.entry("Gr_Base", GRAPHEME_BASE),
                    Map.entry("Grapheme_Extend", GRAPHEME_EXTEND),
                    Map.entry("Gr_Ext", GRAPHEME_EXTEND),
                    Map.entry("Ideographic", IDEOGRAPHIC),
                    Map.entry("Ideo", IDEOGRAPHIC),
                    Map.entry("Join_Control", JOIN_CONTROL),
                    Map.entry("Join_C", JOIN_CONTROL),
                    Map.entry("Pattern_Syntax", PATTERN_SYNTAX),
                    Map.entry("Pat_Syn", PATTERN_SYNTAX),
                    Map.entry("Pattern_White_Space", PATTERN_WHITE_SPACE),
                    Map.entry("Pat_WS", PATTERN_WHITE_SPACE),
                    Map.entry("Quotation_Mark", QUOTATION_MARK),
                    Map.entry("QMark", QUOTATION_MARK),
                    Map.entry("Radical", RADICAL),
                    Map.entry("Regional_Indicator", REGIONAL_INDICATOR),
                    Map.entry("RI", REGIONAL_INDICATOR),
                    Map.entry("Sentence_Terminal", SENTENCE_TERMINAL),
                    Map.entry("STerm", SENTENCE_TERMINAL),
                    Map.entry("Soft_Dotted", SOFT_DOTTED),
                    Map.entry("SD", SOFT_DOTTED),
                    Map.entry("Terminal_Punctuation", TERMINAL_PUNCTUATION),
                    Map.entry("Term", TERMINAL_PUNCTUATION),
                    Map.entry("Unified_Ideograph", UNIFIED_IDEOGRAPH),
                    Map.entry("UIdeo", UNIFIED_IDEOGRAPH),
                    Map.entry("Variation_Selector", VARIATION_SELECTOR),
                    Map.entry("VS", VARIATION_SELECTOR),
                    Map.entry("XID_Continue", XID_CONTINUE),
                    Map.entry("XIDC", XID_CONTINUE),
                    Map.entry("XID_Start", XID_START),
                    Map.entry("XIDS", XID_START),
                    Map.entry("Bidi_Control", BIDI_CONTROL),
                    Map.entry("Bidi_C", BIDI_CONTROL),
                    Map.entry("Bidi_Mirrored", BIDI_MIRRORED),
                    Map.entry("Bidi_M", BIDI_MIRRORED),
                    Map.entry("Deprecated", DEPRECATED),
                    Map.entry("Dep", DEPRECATED),
                    Map.entry("Logical_Order_Exception", LOGICAL_ORDER_EXCEPTION),
                    Map.entry("LOE", LOGICAL_ORDER_EXCEPTION),
                    Map.entry("Changes_When_Casefolded", CHANGES_WHEN_CASEFOLDED),
                    Map.entry("CWCF", CHANGES_WHEN_CASEFOLDED),
                    Map.entry("Changes_When_Casemapped", CHANGES_WHEN_CASEMAPPED),
                    Map.entry("CWCM", CHANGES_WHEN_CASEMAPPED),
                    Map.entry("Changes_When_Lowercased", CHANGES_WHEN_LOWERCASED),
                    Map.entry("CWL", CHANGES_WHEN_LOWERCASED),
                    Map.entry("Changes_When_NFKC_Casefolded", CHANGES_WHEN_NFKC_CASEFOLDED),
                    Map.entry("CWKCF", CHANGES_WHEN_NFKC_CASEFOLDED),
                    Map.entry("Changes_When_Titlecased", CHANGES_WHEN_TITLECASED),
                    Map.entry("CWT", CHANGES_WHEN_TITLECASED),
                    Map.entry("Changes_When_Uppercased", CHANGES_WHEN_UPPERCASED),
                    Map.entry("CWU", CHANGES_WHEN_UPPERCASED),
                    Map.entry("IDS_Binary_Operator", IDS_BINARY_OPERATOR),
                    Map.entry("IDSB", IDS_BINARY_OPERATOR),
                    Map.entry("IDS_Trinary_Operator", IDS_TRINARY_OPERATOR),
                    Map.entry("IDST", IDS_TRINARY_OPERATOR),
                    Map.entry("RGI_Emoji", RGI_EMOJI));

    // Property Value Map for General Category (canonical names and aliases)
    public static final Map<String, Byte> PROPERTY_VALUES =
            Map.<String, Byte>ofEntries(
                    Map.entry("Other", OTHER),
                    Map.entry("C", OTHER),
                    Map.entry("Control", CONTROL),
                    Map.entry("Cc", CONTROL),
                    Map.entry("cntrl", CONTROL),
                    Map.entry("Format", FORMAT),
                    Map.entry("Cf", FORMAT),
                    Map.entry("Unassigned", UNASSIGNED),
                    Map.entry("Cn", UNASSIGNED),
                    Map.entry("Private_Use", PRIVATE_USE),
                    Map.entry("Co", PRIVATE_USE),
                    Map.entry("Surrogate", SURROGATE),
                    Map.entry("Cs", SURROGATE),
                    Map.entry("Letter", LETTER),
                    Map.entry("L", LETTER),
                    Map.entry("Lowercase_Letter", LOWERCASE_LETTER),
                    Map.entry("Ll", LOWERCASE_LETTER),
                    Map.entry("Modifier_Letter", MODIFIER_LETTER),
                    Map.entry("Lm", MODIFIER_LETTER),
                    Map.entry("Other_Letter", OTHER_LETTER),
                    Map.entry("Lo", OTHER_LETTER),
                    Map.entry("Titlecase_Letter", TITLECASE_LETTER),
                    Map.entry("Lt", TITLECASE_LETTER),
                    Map.entry("Uppercase_Letter", UPPERCASE_LETTER),
                    Map.entry("Lu", UPPERCASE_LETTER),
                    Map.entry("Mark", MARK),
                    Map.entry("M", MARK),
                    Map.entry("Combining_Mark", MARK),
                    Map.entry("Spacing_Mark", SPACING_MARK),
                    Map.entry("Mc", SPACING_MARK),
                    Map.entry("Enclosing_Mark", ENCLOSING_MARK),
                    Map.entry("Me", ENCLOSING_MARK),
                    Map.entry("Nonspacing_Mark", NONSPACING_MARK),
                    Map.entry("Mn", NONSPACING_MARK),
                    Map.entry("Number", NUMBER),
                    Map.entry("N", NUMBER),
                    Map.entry("Decimal_Number", DECIMAL_NUMBER),
                    Map.entry("Nd", DECIMAL_NUMBER),
                    Map.entry("digit", NUMBER),
                    Map.entry("Letter_Number", LETTER_NUMBER),
                    Map.entry("Nl", LETTER_NUMBER),
                    Map.entry("Other_Number", OTHER_NUMBER),
                    Map.entry("No", OTHER_NUMBER),
                    Map.entry("Punctuation", PUNCTUATION),
                    Map.entry("P", PUNCTUATION),
                    Map.entry("punct", PUNCTUATION),
                    Map.entry("Connector_Punctuation", CONNECTOR_PUNCTUATION),
                    Map.entry("Pc", CONNECTOR_PUNCTUATION),
                    Map.entry("Dash_Punctuation", DASH_PUNCTUATION),
                    Map.entry("Pd", DASH_PUNCTUATION),
                    Map.entry("Close_Punctuation", CLOSE_PUNCTUATION),
                    Map.entry("Pe", CLOSE_PUNCTUATION),
                    Map.entry("Final_Punctuation", FINAL_PUNCTUATION),
                    Map.entry("Pf", FINAL_PUNCTUATION),
                    Map.entry("Initial_Punctuation", INITIAL_PUNCTUATION),
                    Map.entry("Pi", INITIAL_PUNCTUATION),
                    Map.entry("Other_Punctuation", OTHER_PUNCTUATION),
                    Map.entry("Po", OTHER_PUNCTUATION),
                    Map.entry("Open_Punctuation", OPEN_PUNCTUATION),
                    Map.entry("Ps", OPEN_PUNCTUATION),
                    Map.entry("Symbol", SYMBOL),
                    Map.entry("S", SYMBOL),
                    Map.entry("Currency_Symbol", CURRENCY_SYMBOL),
                    Map.entry("Sc", CURRENCY_SYMBOL),
                    Map.entry("Modifier_Symbol", MODIFIER_SYMBOL),
                    Map.entry("Sk", MODIFIER_SYMBOL),
                    Map.entry("Math_Symbol", MATH_SYMBOL),
                    Map.entry("Sm", MATH_SYMBOL),
                    Map.entry("Other_Symbol", OTHER_SYMBOL),
                    Map.entry("So", OTHER_SYMBOL),
                    Map.entry("Separator", SEPARATOR),
                    Map.entry("Z", SEPARATOR),
                    Map.entry("Line_Separator", LINE_SEPARATOR),
                    Map.entry("Zl", LINE_SEPARATOR),
                    Map.entry("Paragraph_Separator", PARAGRAPH_SEPARATOR),
                    Map.entry("Zp", PARAGRAPH_SEPARATOR),
                    Map.entry("Space_Separator", SPACE_SEPARATOR),
                    Map.entry("Zs", SPACE_SEPARATOR));

    /**
     * Looks up a property name and optionally a value and returns an encoded int. For binary
     * properties, combines the property name with TRUE. For General_Category, combines
     * General_Category with the specified value.
     *
     * @param propertyOrValue Property name or property name=value pair
     * @return Encoded int combining property name and value
     */
    @SuppressWarnings("EnumOrdinal") // We don't persist the ordinals; hence this is safe.
    public static int lookup(String propertyOrValue) {
        if (propertyOrValue == null || propertyOrValue.isEmpty()) {
            return -1;
        }

        Matcher m =
                java.util.regex.Pattern.compile(
                                "^(?<propName>[a-zA-Z_]+)(?:=(?<propValue>[a-zA-Z_0-9]+))?$")
                        .matcher(propertyOrValue);
        m.find();
        if (!m.matches() || m.group("propName") == null) {
            return -1;
        }

        if (m.group("propValue") == null) {
            // It's a single property name (binary property)
            String property = m.group("propName");

            Byte propByte = PROPERTY_NAMES.get(property);

            if (propByte == null) {
                // Check if it's a general category value without the gc= prefix
                Byte valueByte = PROPERTY_VALUES.get(property);
                if (valueByte != null) {
                    // It's a GC value, encode it with GC property
                    return encodeProperty(GENERAL_CATEGORY, valueByte);
                }
                return -1;
            }

            if (propByte == GENERAL_CATEGORY || propByte == SCRIPT) {
                return -1;
            }

            // It's a binary property, encode with TRUE
            return encodeProperty(propByte, TRUE);
        } else {
            // It's a property=value format
            String property = m.group("propName");
            String value = m.group("propValue");

            Byte propByte = PROPERTY_NAMES.get(property);
            if (propByte == null) {
                return -1;
            }

            switch (propByte) {
                case GENERAL_CATEGORY:
                    Byte valueByte = PROPERTY_VALUES.get(value);
                    if (valueByte == null) {
                        return -1;
                    }
                    return encodeProperty(GENERAL_CATEGORY, valueByte);
                case SCRIPT:
                    try {
                        return encodeProperty(
                                SCRIPT, (byte) Character.UnicodeScript.forName(value).ordinal());
                    } catch (IllegalArgumentException e) {
                        return -1;
                    }
                default:
                    // Binary properties don't have values
                    return -1;
            }
        }
    }

    /**
     * Encodes a property name and value into a single int. The property name is in the high 16
     * bits, the value in the low 16 bits.
     *
     * @param property Property name constant
     * @param value Property value constant
     * @return Encoded int
     */
    private static int encodeProperty(byte property, byte value) {
        return ((property & 0xFF) << 8) | (value & 0xFF);
    }

    private static final Character.UnicodeScript[] UnicodeScriptValues =
            Character.UnicodeScript.values();

    /**
     * Tests if a code point has a specific Unicode property.
     *
     * @param property Encoded property (from lookup method)
     * @param codePoint Character code point to test
     * @return true if the code point has the property
     */
    public static boolean hasProperty(int property, int codePoint) {
        byte propByte = (byte) ((property >> 8) & 0xFF);
        int valueByte = (property & 0xFF);

        switch (propByte) {
            case ALPHABETIC:
                return Character.isAlphabetic(codePoint) == (valueByte == TRUE);

            case ASCII:
                return (codePoint <= 0x7F) == (valueByte == TRUE);

            case CASE_IGNORABLE:
                // Java doesn't have a direct method for this
                // This is an approximation
                return (Character.getType(codePoint) == Character.MODIFIER_SYMBOL
                                || Character.getType(codePoint) == Character.MODIFIER_LETTER
                                || Character.getType(codePoint) == Character.NON_SPACING_MARK)
                        == (valueByte == TRUE);

            case GENERAL_CATEGORY:
                int javaCategory = Character.getType(codePoint);
                return checkGeneralCategory(valueByte, javaCategory);
            case ASCII_HEX_DIGIT:
                return isHexDigit(codePoint) == (valueByte == TRUE);
            case HEX_DIGIT:
                return (Character.digit(codePoint, 16) != -1) == (valueByte == TRUE);
            case ID_CONTINUE:
                return Character.isUnicodeIdentifierPart(codePoint) == (valueByte == TRUE);

            case ID_START:
                return Character.isUnicodeIdentifierStart(codePoint) == (valueByte == TRUE);

            case LOWERCASE:
                return Character.isLowerCase(codePoint) == (valueByte == TRUE);

            case UPPERCASE:
                return Character.isUpperCase(codePoint) == (valueByte == TRUE);

            case WHITE_SPACE:
                {
                    // Note: This only a good approximation of the Unicode white space property
                    return (valueByte == TRUE)
                            == (Character.isSpaceChar(codePoint)
                                    || Character.isWhitespace(codePoint));
                }
            case SCRIPT:
                return Character.UnicodeScript.of(codePoint) == UnicodeScriptValues[valueByte];

            case EMOJI:
                return EmojiData.isEmoji(codePoint) == (valueByte == TRUE);
            case EMOJI_COMPONENT:
                return EmojiData.isEmojiComponent(codePoint) == (valueByte == TRUE);
            case EMOJI_MODIFIER:
                return EmojiData.isEmojiModifier(codePoint) == (valueByte == TRUE);
            case EMOJI_MODIFIER_BASE:
                return EmojiData.isEmojiModifierBase(codePoint) == (valueByte == TRUE);
            case EMOJI_PRESENTATION:
                return EmojiData.isEmojiPresentation(codePoint) == (valueByte == TRUE);
            case EXTENDED_PICTOGRAPHIC:
                return EmojiData.isExtendedPictographic(codePoint) == (valueByte == TRUE);

            case ANY:
                return (codePoint >= 0 && codePoint <= 0x10FFFF) == (valueByte == TRUE);
            case ASSIGNED:
                return (Character.getType(codePoint) != Character.UNASSIGNED)
                        == (valueByte == TRUE);
            case DEFAULT_IGNORABLE_CODE_POINT:
                return isDefaultIgnorableCodePoint(codePoint) == (valueByte == TRUE);
            case CASED:
                return (Character.isUpperCase(codePoint)
                                || Character.isLowerCase(codePoint)
                                || Character.isTitleCase(codePoint))
                        == (valueByte == TRUE);
            case MATH:
                return (Character.getType(codePoint) == Character.MATH_SYMBOL)
                        == (valueByte == TRUE);
            case NONCHARACTER_CODE_POINT:
                return isNoncharacterCodePoint(codePoint) == (valueByte == TRUE);
            case DASH:
                return (Character.getType(codePoint) == Character.DASH_PUNCTUATION)
                        == (valueByte == TRUE);
            case DIACRITIC:
                return (Character.getType(codePoint) == Character.NON_SPACING_MARK
                                || Character.getType(codePoint) == Character.MODIFIER_LETTER)
                        == (valueByte == TRUE);
            case EXTENDER:
                // Approximation: characters that extend preceding characters
                return (codePoint == 0x00B7
                                || codePoint == 0x02D0
                                || codePoint == 0x02D1
                                || codePoint == 0x0640
                                || codePoint == 0x07FA
                                || codePoint == 0x0E46
                                || codePoint == 0x0EC6
                                || codePoint == 0x1843
                                || codePoint == 0x3005
                                || (codePoint >= 0x3031 && codePoint <= 0x3035)
                                || (codePoint >= 0x309D && codePoint <= 0x309E)
                                || (codePoint >= 0x30FC && codePoint <= 0x30FE)
                                || codePoint == 0xFF70)
                        == (valueByte == TRUE);
            case GRAPHEME_BASE:
                {
                    int type = Character.getType(codePoint);
                    return (type != Character.CONTROL
                                    && type != Character.FORMAT
                                    && type != Character.SURROGATE
                                    && type != Character.UNASSIGNED
                                    && type != Character.LINE_SEPARATOR
                                    && type != Character.PARAGRAPH_SEPARATOR
                                    && type != Character.NON_SPACING_MARK
                                    && type != Character.ENCLOSING_MARK
                                    && type != Character.COMBINING_SPACING_MARK
                                    && codePoint != 0)
                            == (valueByte == TRUE);
                }
            case GRAPHEME_EXTEND:
                {
                    int type = Character.getType(codePoint);
                    return (type == Character.NON_SPACING_MARK
                                    || type == Character.ENCLOSING_MARK
                                    || type == Character.COMBINING_SPACING_MARK)
                            == (valueByte == TRUE);
                }
            case IDEOGRAPHIC:
                return Character.isIdeographic(codePoint) == (valueByte == TRUE);
            case JOIN_CONTROL:
                return (codePoint == 0x200C || codePoint == 0x200D) == (valueByte == TRUE);
            case PATTERN_SYNTAX:
                return isPatternSyntax(codePoint) == (valueByte == TRUE);
            case PATTERN_WHITE_SPACE:
                return isPatternWhiteSpace(codePoint) == (valueByte == TRUE);
            case QUOTATION_MARK:
                return isQuotationMark(codePoint) == (valueByte == TRUE);
            case RADICAL:
                return (codePoint >= 0x2E80 && codePoint <= 0x2FD5) == (valueByte == TRUE);
            case REGIONAL_INDICATOR:
                return (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) == (valueByte == TRUE);
            case SENTENCE_TERMINAL:
                return isSentenceTerminal(codePoint) == (valueByte == TRUE);
            case SOFT_DOTTED:
                return (codePoint == 'i' || codePoint == 'j') == (valueByte == TRUE);
            case TERMINAL_PUNCTUATION:
                {
                    int type = Character.getType(codePoint);
                    return (type == Character.END_PUNCTUATION
                                    || type == Character.FINAL_QUOTE_PUNCTUATION
                                    || codePoint == '.'
                                    || codePoint == '!'
                                    || codePoint == '?'
                                    || codePoint == ','
                                    || codePoint == ':'
                                    || codePoint == ';')
                            == (valueByte == TRUE);
                }
            case UNIFIED_IDEOGRAPH:
                return Character.isIdeographic(codePoint) == (valueByte == TRUE);
            case VARIATION_SELECTOR:
                return ((codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF)
                                || codePoint == 0x180B
                                || codePoint == 0x180C
                                || codePoint == 0x180D
                                || codePoint == 0x180F)
                        == (valueByte == TRUE);
            case XID_CONTINUE:
                return Character.isUnicodeIdentifierPart(codePoint) == (valueByte == TRUE);
            case XID_START:
                return Character.isUnicodeIdentifierStart(codePoint) == (valueByte == TRUE);
            case BIDI_CONTROL:
                return ((codePoint >= 0x200E && codePoint <= 0x200F)
                                || (codePoint >= 0x202A && codePoint <= 0x202E)
                                || (codePoint >= 0x2066 && codePoint <= 0x2069)
                                || codePoint == 0x061C)
                        == (valueByte == TRUE);
            case BIDI_MIRRORED:
                return Character.isMirrored(codePoint) == (valueByte == TRUE);
            case DEPRECATED:
                return (codePoint == 0x0149
                                || codePoint == 0x0673
                                || codePoint == 0x0F77
                                || codePoint == 0x0F79
                                || (codePoint >= 0x17A3 && codePoint <= 0x17A4)
                                || (codePoint >= 0x206A && codePoint <= 0x206F)
                                || codePoint == 0x2329
                                || codePoint == 0x232A
                                || (codePoint >= 0xE0001 && codePoint <= 0xE007F))
                        == (valueByte == TRUE);
            case LOGICAL_ORDER_EXCEPTION:
                return (codePoint == 0x0E40
                                || codePoint == 0x0E41
                                || codePoint == 0x0E42
                                || codePoint == 0x0E43
                                || codePoint == 0x0E44
                                || codePoint == 0x0EC0
                                || codePoint == 0x0EC1
                                || codePoint == 0x0EC2
                                || codePoint == 0x0EC3
                                || codePoint == 0x0EC4
                                || (codePoint >= 0xAAB5 && codePoint <= 0xAAB6)
                                || (codePoint >= 0xAAB9 && codePoint <= 0xAAB9)
                                || (codePoint >= 0xAABB && codePoint <= 0xAABC))
                        == (valueByte == TRUE);
            case CHANGES_WHEN_LOWERCASED:
                return (Character.toLowerCase(codePoint) != codePoint) == (valueByte == TRUE);
            case CHANGES_WHEN_UPPERCASED:
                return (Character.toUpperCase(codePoint) != codePoint) == (valueByte == TRUE);
            case CHANGES_WHEN_TITLECASED:
                return (Character.toTitleCase(codePoint) != codePoint) == (valueByte == TRUE);
            case CHANGES_WHEN_CASEFOLDED:
                // Approximation: case folding is close to lowercasing for most chars
                return (Character.toLowerCase(codePoint) != codePoint) == (valueByte == TRUE);
            case CHANGES_WHEN_CASEMAPPED:
                return (Character.toLowerCase(codePoint) != codePoint
                                || Character.toUpperCase(codePoint) != codePoint
                                || Character.toTitleCase(codePoint) != codePoint)
                        == (valueByte == TRUE);
            case CHANGES_WHEN_NFKC_CASEFOLDED:
                // Approximation
                return (Character.toLowerCase(codePoint) != codePoint) == (valueByte == TRUE);
            case IDS_BINARY_OPERATOR:
                return (codePoint == 0x2FF0
                                || codePoint == 0x2FF1
                                || (codePoint >= 0x2FF4 && codePoint <= 0x2FFB)
                                || codePoint == 0x31EF)
                        == (valueByte == TRUE);
            case IDS_TRINARY_OPERATOR:
                return (codePoint == 0x2FF2 || codePoint == 0x2FF3) == (valueByte == TRUE);

            case RGI_EMOJI:
                // Approximation: RGI_Emoji is a property of strings that includes
                // multi-codepoint emoji sequences. We approximate with Emoji_Presentation
                // for single-codepoint matching.
                return EmojiData.isEmojiPresentation(codePoint) == (valueByte == TRUE);

            default:
                return false;
        }
    }

    /** Maps our property value bytes to Java's Character.getType() values. */
    private static boolean checkGeneralCategory(int propertyValueByte, int javaCategory) {
        switch (propertyValueByte) {
            case LETTER:
                return javaCategory == Character.UPPERCASE_LETTER
                        || javaCategory == Character.LOWERCASE_LETTER
                        || javaCategory == Character.TITLECASE_LETTER
                        || javaCategory == Character.MODIFIER_LETTER
                        || javaCategory == Character.OTHER_LETTER;
            case UPPERCASE_LETTER:
                return javaCategory == Character.UPPERCASE_LETTER;
            case LOWERCASE_LETTER:
                return javaCategory == Character.LOWERCASE_LETTER;
            case TITLECASE_LETTER:
                return javaCategory == Character.TITLECASE_LETTER;
            case MODIFIER_LETTER:
                return javaCategory == Character.MODIFIER_LETTER;
            case OTHER_LETTER:
                return javaCategory == Character.OTHER_LETTER;
            case MARK:
                return javaCategory == Character.NON_SPACING_MARK
                        || javaCategory == Character.ENCLOSING_MARK
                        || javaCategory == Character.COMBINING_SPACING_MARK;
            case NONSPACING_MARK:
                return javaCategory == Character.NON_SPACING_MARK;
            case ENCLOSING_MARK:
                return javaCategory == Character.ENCLOSING_MARK;
            case SPACING_MARK:
                return javaCategory == Character.COMBINING_SPACING_MARK;
            case NUMBER:
                return javaCategory == Character.DECIMAL_DIGIT_NUMBER
                        || javaCategory == Character.LETTER_NUMBER
                        || javaCategory == Character.OTHER_NUMBER;
            case DECIMAL_NUMBER:
                return javaCategory == Character.DECIMAL_DIGIT_NUMBER;
            case LETTER_NUMBER:
                return javaCategory == Character.LETTER_NUMBER;
            case OTHER_NUMBER:
                return javaCategory == Character.OTHER_NUMBER;

            case SEPARATOR:
                return javaCategory == Character.SPACE_SEPARATOR
                        || javaCategory == Character.LINE_SEPARATOR
                        || javaCategory == Character.PARAGRAPH_SEPARATOR;
            case SPACE_SEPARATOR:
                return javaCategory == Character.SPACE_SEPARATOR;
            case LINE_SEPARATOR:
                return javaCategory == Character.LINE_SEPARATOR;
            case PARAGRAPH_SEPARATOR:
                return javaCategory == Character.PARAGRAPH_SEPARATOR;

            case OTHER:
                return javaCategory == Character.OTHER_LETTER
                        || javaCategory == Character.OTHER_NUMBER
                        || javaCategory == Character.OTHER_PUNCTUATION
                        || javaCategory == Character.OTHER_SYMBOL;
            case CONTROL:
                return javaCategory == Character.CONTROL;
            case FORMAT:
                return javaCategory == Character.FORMAT;
            case SURROGATE:
                return javaCategory == Character.SURROGATE;
            case PRIVATE_USE:
                return javaCategory == Character.PRIVATE_USE;

            case PUNCTUATION:
                return javaCategory == Character.CONNECTOR_PUNCTUATION
                        || javaCategory == Character.DASH_PUNCTUATION
                        || javaCategory == Character.START_PUNCTUATION
                        || javaCategory == Character.END_PUNCTUATION
                        || javaCategory == Character.OTHER_PUNCTUATION
                        || javaCategory == Character.INITIAL_QUOTE_PUNCTUATION
                        || javaCategory == Character.FINAL_QUOTE_PUNCTUATION;
            case DASH_PUNCTUATION:
                return javaCategory == Character.DASH_PUNCTUATION;
            case OPEN_PUNCTUATION:
                return javaCategory == Character.START_PUNCTUATION;
            case CLOSE_PUNCTUATION:
                return javaCategory == Character.END_PUNCTUATION;
            case CONNECTOR_PUNCTUATION:
                return javaCategory == Character.CONNECTOR_PUNCTUATION;
            case OTHER_PUNCTUATION:
                return javaCategory == Character.OTHER_PUNCTUATION;
            case INITIAL_PUNCTUATION:
                return javaCategory == Character.INITIAL_QUOTE_PUNCTUATION;
            case FINAL_PUNCTUATION:
                return javaCategory == Character.FINAL_QUOTE_PUNCTUATION;

            case SYMBOL:
                return javaCategory == Character.MATH_SYMBOL
                        || javaCategory == Character.CURRENCY_SYMBOL
                        || javaCategory == Character.MODIFIER_SYMBOL
                        || javaCategory == Character.OTHER_SYMBOL;
            case MATH_SYMBOL:
                return javaCategory == Character.MATH_SYMBOL;
            case CURRENCY_SYMBOL:
                return javaCategory == Character.CURRENCY_SYMBOL;
            case MODIFIER_SYMBOL:
                return javaCategory == Character.MODIFIER_SYMBOL;
            case OTHER_SYMBOL:
                return javaCategory == Character.OTHER_SYMBOL;
            case UNASSIGNED:
                return javaCategory == Character.UNASSIGNED;

            default:
                return false;
        }
    }

    /** Checks if a code point is a hex digit. */
    private static boolean isHexDigit(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9')
                || (codePoint >= 'a' && codePoint <= 'f')
                || (codePoint >= 'A' && codePoint <= 'F');
    }

    /** Unicode 16.0 Default_Ignorable_Code_Point property. */
    private static boolean isDefaultIgnorableCodePoint(int cp) {
        return cp == 0x00AD
                || cp == 0x034F
                || cp == 0x061C
                || (cp >= 0x115F && cp <= 0x1160)
                || (cp >= 0x17B4 && cp <= 0x17B5)
                || (cp >= 0x180B && cp <= 0x180F)
                || (cp >= 0x200B && cp <= 0x200F)
                || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2060 && cp <= 0x206F)
                || cp == 0x3164
                || (cp >= 0xFE00 && cp <= 0xFE0F)
                || cp == 0xFEFF
                || (cp >= 0xFFF0 && cp <= 0xFFF8)
                || cp == 0xFFA0
                || (cp >= 0x1BCA0 && cp <= 0x1BCA3)
                || (cp >= 0x1D173 && cp <= 0x1D17A)
                || (cp >= 0xE0000 && cp <= 0xE0FFF);
    }

    /** Unicode Noncharacter_Code_Point property. */
    private static boolean isNoncharacterCodePoint(int cp) {
        return (cp >= 0xFDD0 && cp <= 0xFDEF) || ((cp & 0xFFFE) == 0xFFFE && cp <= 0x10FFFF);
    }

    /** Unicode Pattern_Syntax property. */
    private static boolean isPatternSyntax(int cp) {
        return (cp >= 0x0021 && cp <= 0x002F)
                || (cp >= 0x003A && cp <= 0x0040)
                || (cp >= 0x005B && cp <= 0x005E)
                || cp == 0x0060
                || (cp >= 0x007B && cp <= 0x007E)
                || (cp >= 0x00A1 && cp <= 0x00A7)
                || cp == 0x00A9
                || (cp >= 0x00AB && cp <= 0x00AC)
                || cp == 0x00AE
                || (cp >= 0x00B0 && cp <= 0x00B1)
                || cp == 0x00B6
                || cp == 0x00BB
                || cp == 0x00BF
                || cp == 0x00D7
                || cp == 0x00F7
                || (cp >= 0x2010 && cp <= 0x2027)
                || (cp >= 0x2030 && cp <= 0x203E)
                || (cp >= 0x2041 && cp <= 0x2053)
                || (cp >= 0x2055 && cp <= 0x205E)
                || (cp >= 0x2190 && cp <= 0x245F)
                || (cp >= 0x2500 && cp <= 0x2775)
                || (cp >= 0x2794 && cp <= 0x2BFF)
                || (cp >= 0x2E00 && cp <= 0x2E7F)
                || (cp >= 0x3001 && cp <= 0x3003)
                || (cp >= 0x3008 && cp <= 0x3020)
                || cp == 0x3030
                || (cp >= 0xFD3E && cp <= 0xFD3F)
                || (cp >= 0xFE45 && cp <= 0xFE46);
    }

    /** Unicode Pattern_White_Space property. */
    private static boolean isPatternWhiteSpace(int cp) {
        return cp == 0x0009
                || cp == 0x000A
                || cp == 0x000B
                || cp == 0x000C
                || cp == 0x000D
                || cp == 0x0020
                || cp == 0x0085
                || cp == 0x200E
                || cp == 0x200F
                || cp == 0x2028
                || cp == 0x2029;
    }

    /** Unicode Quotation_Mark property. */
    private static boolean isQuotationMark(int cp) {
        return cp == 0x0022
                || cp == 0x0027
                || cp == 0x00AB
                || cp == 0x00BB
                || cp == 0x2018
                || cp == 0x2019
                || cp == 0x201A
                || (cp >= 0x201B && cp <= 0x201C)
                || cp == 0x201D
                || (cp >= 0x201E && cp <= 0x201F)
                || cp == 0x2039
                || cp == 0x203A
                || cp == 0x2E42
                || (cp >= 0x300C && cp <= 0x300F)
                || (cp >= 0x301D && cp <= 0x301F)
                || cp == 0xFE41
                || cp == 0xFE42
                || cp == 0xFE43
                || cp == 0xFE44
                || cp == 0xFF02
                || cp == 0xFF07
                || cp == 0xFF62
                || cp == 0xFF63;
    }

    /** Unicode Sentence_Terminal property (common code points). */
    private static boolean isSentenceTerminal(int cp) {
        return cp == 0x0021
                || cp == 0x002E
                || cp == 0x003F
                || cp == 0x0589
                || cp == 0x061D
                || (cp >= 0x061E && cp <= 0x061F)
                || cp == 0x06D4
                || cp == 0x0700
                || (cp >= 0x0701 && cp <= 0x0702)
                || cp == 0x07F9
                || cp == 0x0837
                || cp == 0x0839
                || (cp >= 0x083D && cp <= 0x083E)
                || cp == 0x0964
                || cp == 0x0965
                || cp == 0x104A
                || cp == 0x104B
                || cp == 0x1362
                || cp == 0x1367
                || cp == 0x1368
                || cp == 0x166E
                || cp == 0x1735
                || cp == 0x1736
                || cp == 0x1803
                || cp == 0x1809
                || cp == 0x1944
                || cp == 0x1945
                || (cp >= 0x1AA8 && cp <= 0x1AAB)
                || (cp >= 0x1B5A && cp <= 0x1B5B)
                || (cp >= 0x1B5E && cp <= 0x1B5F)
                || cp == 0x1B7D
                || cp == 0x1B7E
                || (cp >= 0x1C3B && cp <= 0x1C3C)
                || cp == 0x1C7E
                || cp == 0x1C7F
                || cp == 0x203C
                || cp == 0x203D
                || cp == 0x2047
                || cp == 0x2048
                || cp == 0x2049
                || cp == 0x2E2E
                || cp == 0x2E3C
                || cp == 0x2E53
                || cp == 0x2E54
                || cp == 0x3002
                || cp == 0xA4FF
                || cp == 0xA60E
                || cp == 0xA60F
                || cp == 0xA6F3
                || cp == 0xA6F7
                || cp == 0xA876
                || cp == 0xA877
                || cp == 0xA8CE
                || cp == 0xA8CF
                || cp == 0xA92F
                || cp == 0xA9C8
                || cp == 0xA9C9
                || (cp >= 0xAA5D && cp <= 0xAA5F)
                || cp == 0xAAF0
                || cp == 0xAAF1
                || cp == 0xABEB
                || cp == 0xFE52
                || cp == 0xFE56
                || cp == 0xFE57
                || cp == 0xFF01
                || cp == 0xFF0E
                || cp == 0xFF1F
                || cp == 0xFF61
                || (cp >= 0x10A56 && cp <= 0x10A57)
                || cp == 0x10F55
                || (cp >= 0x10F56 && cp <= 0x10F59)
                || cp == 0x10F86
                || (cp >= 0x10F87 && cp <= 0x10F89)
                || cp == 0x11047
                || cp == 0x11048
                || (cp >= 0x110BE && cp <= 0x110C1)
                || (cp >= 0x11141 && cp <= 0x11143)
                || (cp >= 0x111C5 && cp <= 0x111C6)
                || cp == 0x111CD
                || cp == 0x111DE
                || cp == 0x111DF
                || cp == 0x11238
                || cp == 0x11239
                || cp == 0x1123B
                || cp == 0x1123C
                || cp == 0x112A9
                || (cp >= 0x1144B && cp <= 0x1144C)
                || (cp >= 0x115C2 && cp <= 0x115C3)
                || (cp >= 0x115C9 && cp <= 0x115D7)
                || (cp >= 0x11641 && cp <= 0x11642)
                || (cp >= 0x1173C && cp <= 0x1173E)
                || cp == 0x11944
                || cp == 0x11946
                || cp == 0x11A42
                || cp == 0x11A43
                || cp == 0x11A9B
                || cp == 0x11A9C
                || cp == 0x11C41
                || cp == 0x11C42
                || cp == 0x11EF7
                || cp == 0x11EF8
                || cp == 0x11F43
                || cp == 0x11F44
                || cp == 0x16A6E
                || cp == 0x16A6F
                || cp == 0x16AF5
                || cp == 0x16B37
                || cp == 0x16B38
                || cp == 0x16B44
                || cp == 0x16E98
                || cp == 0x1BC9F
                || cp == 0x1DA88;
    }
}
