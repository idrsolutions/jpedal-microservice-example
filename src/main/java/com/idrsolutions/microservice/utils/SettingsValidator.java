package com.idrsolutions.microservice.utils;

import org.jpedal.utils.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SettingsValidator {

    final StringBuilder errorMessage = new StringBuilder();
    final HashMap<String, String> paramMap = new HashMap<>();

    public SettingsValidator(final Map<String, String> paramMap) {
        this.paramMap.putAll(paramMap);
    }

    public void requireString(final String setting, final String[] values) {
        validateString(setting, values, true);
    }

    public void requireFloat(final String setting, final float[] range) {
        validateFloat(setting, range, true);
    }

    public void optionalString(final String setting, final String[] values) {
        validateString(setting, values, false);
    }

    public void optionalFloat(final String setting, final float[] range) {
        validateFloat(setting, range, false);
    }

    private void validateString(final String setting, final String[] values, final boolean required) {
        if (paramMap.containsKey(setting)) {
            final String value = paramMap.remove(setting);
            if (!Arrays.asList(values).contains(value)) {
                errorMessage.append(required ? "Required " : "Optional ").append("setting \"").append(setting)
                        .append("\" has incorrect value. Valid values are ").append(Arrays.toString(values)).append('\n');
            }
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing. Valid values are ")
                        .append(Arrays.toString(values)).append(".\n");
            }
        }
    }

    private void validateFloat(final String setting, final float[] range, final boolean required) {
        if (paramMap.containsKey(setting)) {
            final String value = paramMap.remove(setting);
            if (StringUtils.isNumber(value)) {
                final float fValue = Float.parseFloat(value);
                if (fValue < range[0] && range[1] < fValue) {
                    errorMessage.append(required ? "Required " : "Optional ").append("setting \"").append(setting)
                            .append("\" has incorrect value. Valid values are between ").append(range[0]).append(" and ")
                            .append(range[1]).append(".\n");
                }
            }
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing. Valid values are between ")
                        .append(range[0]).append(" and ").append(range[1]).append(".\n");
            }
        }
    }

    public boolean validates() {
        if (!paramMap.isEmpty()) {
            errorMessage.append("The following settings were not recognised.\n");
            final Set<String> keys = paramMap.keySet();
            for (String key : keys) {
                errorMessage.append("    ").append(key).append('\n');
            }
            paramMap.clear();
        }

        return errorMessage.length() == 0;
    }

    public String getMessage() {
        return errorMessage.toString();
    }

}
