/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Default {@link NodeFileSystem} implementation backed by {@code java.nio.file}. */
public class DefaultNodeFileSystem implements NodeFileSystem {

    @Override
    public boolean isFile(String path) {
        return Files.isRegularFile(Paths.get(path));
    }

    @Override
    public boolean isDirectory(String path) {
        return Files.isDirectory(Paths.get(path));
    }

    @Override
    public String readFile(String path) throws IOException {
        return Files.readString(Paths.get(path));
    }

    @Override
    public String realPath(String path) throws IOException {
        return Paths.get(path).toRealPath().toString();
    }

    @Override
    public String[] listDirectory(String path) throws IOException {
        List<String> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(path))) {
            for (Path entry : stream) {
                entries.add(entry.getFileName().toString());
            }
        }
        return entries.toArray(new String[0]);
    }

    @Override
    public String separator() {
        return java.io.File.separator;
    }

    @Override
    public String resolve(String base, String child) {
        return Paths.get(base).resolve(child).normalize().toString();
    }

    @Override
    public String dirname(String path) {
        Path parent = Paths.get(path).getParent();
        return parent != null ? parent.toString() : path;
    }

    @Override
    public String getAbsolutePath(String path) {
        return Paths.get(path).toAbsolutePath().normalize().toString();
    }
}
