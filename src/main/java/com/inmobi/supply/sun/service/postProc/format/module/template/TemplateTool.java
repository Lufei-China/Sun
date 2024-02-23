package com.inmobi.supply.sun.service.postProc.format.module.template;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.tools.generic.EscapeTool;
import org.springframework.stereotype.Component;


/**
 * Generic tooling for velocity templates.
 */
@Slf4j
@NoArgsConstructor
public class TemplateTool extends EscapeTool {


    /**
     * Returns true if sdkVersion is greater than the compareVersion, in a device agnostic sense.
     *
     */
    public boolean sdkGreaterOrEquals(final String sdkVersion, final String compareVersion) {
        return isSdkVersionAboveOrEqualsGivenVersion(sdkVersion, compareVersion);
    }

    public static boolean isSdkVersionAboveOrEqualsGivenVersion(final String imSdkVersionString,
                                                                final String givenVersionString) {
        if (StringUtils.isNotEmpty(imSdkVersionString) && StringUtils.isNotEmpty(givenVersionString)) {
            try {
                final String imSdkString = imSdkVersionString.replace(".", "");
                final int imSdkVersion = Integer.parseInt(imSdkString);
                final int givenVersion = Integer.parseInt(givenVersionString);
                return imSdkVersion >= givenVersion;
            } catch (final NumberFormatException e) {
                log.debug("Invalid imSdk value or invalid given version string.", e);
                return false;
            }
        }
        return false;
    }

    public Object evalJsonPath(Object jsonObject, String jsonPathExpression) {
        JsonPath jsonPath = JsonPath.compile(jsonPathExpression);
        try {
            return jsonPath.read(jsonObject);
        } catch (InvalidPathException e) {
            return null;
        }
    }



    public String jsInline(final String str) {
        return super.javascript(str).replaceAll("<\\/", "<' +'/");
    }

}