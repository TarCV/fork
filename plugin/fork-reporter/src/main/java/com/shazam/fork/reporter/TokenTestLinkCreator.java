/*
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.reporter;

import com.shazam.fork.reporter.model.TestLabel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.apache.commons.lang3.StringUtils.stripEnd;

public class TokenTestLinkCreator implements TestLinkCreator {
    private static final String TEST_PATH = "/html/pools/%s/%s__%s.html";

    @Override
    public String createLinkToTest(@Nullable String buildLink, @Nonnull String poolName, @Nonnull TestLabel testLabel) {
        if (buildLink == null) {
            return null;
        }

        String baseTestUrl = stripEnd(buildLink, "/") + TEST_PATH;
        return String.format(baseTestUrl, poolName, testLabel.getClassName(), testLabel.getMethod());
    }
}
