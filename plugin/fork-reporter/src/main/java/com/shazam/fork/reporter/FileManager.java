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

import org.apache.commons.io.filefilter.*;

import java.io.File;
import java.io.FileFilter;
import java.util.List;

import static com.shazam.fork.CommonDefaults.FORK;
import static com.shazam.fork.CommonDefaults.JSON;
import static java.util.Arrays.asList;

public class FileManager {
    private final File inputDirectory;

    public FileManager(File inputDirectory) {
        this.inputDirectory = inputDirectory;
    }

    public List<File> getIndividualSummaries() {
        FileFilter fileFilter = new AndFileFilter(
                new PrefixFileFilter(FORK),
                new SuffixFileFilter(JSON));

        return asList(inputDirectory.listFiles(fileFilter));
    }
}
