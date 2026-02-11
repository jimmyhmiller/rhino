/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Node.js module resolution algorithm. Each public method maps to a named algorithm from the
 * Node.js specification.
 *
 * <p>ESM algorithms: https://nodejs.org/api/esm.html#resolution-algorithm CJS algorithms:
 * https://nodejs.org/api/modules.html#all-together
 *
 * <p>This class is stateless (beyond the injected filesystem and reader) and thread-safe.
 */
public class NodeModuleResolver {

    private final NodeFileSystem fs;
    private final PackageJsonReader pkgReader;

    public NodeModuleResolver(NodeFileSystem fs, PackageJsonReader pkgReader) {
        this.fs = fs;
        this.pkgReader = pkgReader;
    }

    // ========================================================================
    // ESM entry points
    // ========================================================================

    /**
     * ESM_RESOLVE(specifier, parentURL)
     *
     * <p>Resolves an ESM import specifier.
     */
    public ResolvedModule esmResolve(String specifier, String parentPath) {
        // 1. If specifier starts with #, it's a package import
        if (specifier.startsWith("#")) {
            return packageImportsResolve(specifier, parentPath, NodeConditions.ESM_CONDITIONS);
        }

        String resolved;

        // 2. If specifier is a relative path or absolute
        if (specifier.startsWith("./")
                || specifier.startsWith("../")
                || specifier.startsWith("/")) {
            String parentDir = fs.dirname(parentPath);
            resolved = fs.getAbsolutePath(fs.resolve(parentDir, specifier));

            // Try exact, then extensions
            String found = resolveEsmFile(resolved);
            if (found == null) {
                throw new ModuleNotFoundException(
                        "Cannot find module '" + specifier + "' from '" + parentPath + "'");
            }
            resolved = found;
        } else {
            // 3. Bare specifier — package resolve
            ResolvedModule pkgResult = packageResolve(specifier, parentPath);
            if (pkgResult != null) {
                return pkgResult;
            }
            throw new ModuleNotFoundException(
                    "Cannot find package '" + specifier + "' from '" + parentPath + "'");
        }

        return new ResolvedModule(resolved, esmFileFormat(resolved));
    }

    /**
     * Try to resolve an ESM file path: exact match, then .mjs, .js, .json extensions, then
     * directory index.
     */
    private String resolveEsmFile(String path) {
        if (fs.isFile(path)) {
            return path;
        }
        // Try extensions
        String[] exts = {".mjs", ".js", ".json"};
        for (String ext : exts) {
            String withExt = path + ext;
            if (fs.isFile(withExt)) {
                return withExt;
            }
        }
        // Try directory with index
        if (fs.isDirectory(path)) {
            for (String ext : exts) {
                String index = fs.resolve(path, "index" + ext);
                if (fs.isFile(index)) {
                    return index;
                }
            }
        }
        return null;
    }

    /**
     * PACKAGE_RESOLVE(packageSpecifier, parentURL)
     *
     * <p>Resolves a bare package specifier.
     */
    public ResolvedModule packageResolve(String packageSpecifier, String parentPath) {
        // 1. Parse package name and subpath
        String packageName;
        String packageSubpath;

        int sepIdx;
        if (packageSpecifier.startsWith("@")) {
            // Scoped package: @scope/name or @scope/name/subpath
            int firstSlash = packageSpecifier.indexOf('/');
            if (firstSlash == -1) {
                throw new ModuleNotFoundException(
                        "Invalid scoped package specifier: " + packageSpecifier);
            }
            int secondSlash = packageSpecifier.indexOf('/', firstSlash + 1);
            if (secondSlash == -1) {
                packageName = packageSpecifier;
                packageSubpath = ".";
            } else {
                packageName = packageSpecifier.substring(0, secondSlash);
                packageSubpath = "." + packageSpecifier.substring(secondSlash);
            }
        } else {
            sepIdx = packageSpecifier.indexOf('/');
            if (sepIdx == -1) {
                packageName = packageSpecifier;
                packageSubpath = ".";
            } else {
                packageName = packageSpecifier.substring(0, sepIdx);
                packageSubpath = "." + packageSpecifier.substring(sepIdx);
            }
        }

        // 2. Try self-reference first
        ResolvedModule selfResult = packageSelfResolve(packageName, packageSubpath, parentPath);
        if (selfResult != null) {
            return selfResult;
        }

        // 3. Walk node_modules directories
        String parentDir = fs.dirname(parentPath);
        List<String> dirs = nodeModulesPaths(parentDir);
        for (String dir : dirs) {
            String packageDir = fs.resolve(dir, packageName);
            if (!fs.isDirectory(packageDir)) {
                continue;
            }

            PackageJson pkg = pkgReader.read(packageDir);

            // Try exports field first
            if (pkg != null && pkg.hasExports()) {
                ResolvedModule exportsResult =
                        packageExportsResolve(
                                packageDir,
                                packageSubpath,
                                pkg.getExports(),
                                NodeConditions.ESM_CONDITIONS);
                if (exportsResult != null) {
                    return exportsResult;
                }
                // exports field is defined but doesn't match — that's an error per spec
                if (!packageSubpath.equals(".")) {
                    throw new ModuleNotFoundException(
                            "Package subpath '"
                                    + packageSubpath
                                    + "' is not defined by \"exports\" in "
                                    + packageDir
                                    + "/package.json");
                }
            }

            // No exports or exports didn't match for "." — fall back to main/index
            if (packageSubpath.equals(".")) {
                if (pkg != null && pkg.getMain() != null) {
                    String mainPath = fs.getAbsolutePath(fs.resolve(packageDir, pkg.getMain()));
                    String found = resolveEsmFile(mainPath);
                    if (found != null) {
                        return new ResolvedModule(found, esmFileFormat(found));
                    }
                }
                // Try index files
                String[] exts = {".mjs", ".js", ".json"};
                for (String ext : exts) {
                    String indexPath = fs.resolve(packageDir, "index" + ext);
                    if (fs.isFile(indexPath)) {
                        return new ResolvedModule(indexPath, esmFileFormat(indexPath));
                    }
                }
            } else {
                // subpath without exports — resolve as file
                // packageSubpath starts with "./" so strip both characters
                String subFile =
                        fs.getAbsolutePath(fs.resolve(packageDir, packageSubpath.substring(2)));
                String found = resolveEsmFile(subFile);
                if (found != null) {
                    return new ResolvedModule(found, esmFileFormat(found));
                }
            }
        }

        return null;
    }

    /**
     * PACKAGE_SELF_RESOLVE(packageName, packageSubpath, parentURL)
     *
     * <p>Resolves a package self-reference (when a package imports itself).
     */
    public ResolvedModule packageSelfResolve(
            String packageName, String packageSubpath, String parentPath) {
        String parentDir = fs.dirname(parentPath);
        PackageJson pkg = pkgReader.findNearest(parentDir);
        if (pkg == null || !packageName.equals(pkg.getName())) {
            return null;
        }
        if (!pkg.hasExports()) {
            return null;
        }
        return packageExportsResolve(
                pkg.getDirectory(),
                packageSubpath,
                pkg.getExports(),
                NodeConditions.ESM_CONDITIONS);
    }

    /**
     * ESM_FILE_FORMAT(url)
     *
     * <p>Determines the module format from a file path.
     */
    public ResolvedModule.ModuleFormat esmFileFormat(String path) {
        if (path.endsWith(".mjs")) {
            return ResolvedModule.ModuleFormat.MODULE;
        }
        if (path.endsWith(".cjs")) {
            return ResolvedModule.ModuleFormat.COMMONJS;
        }
        if (path.endsWith(".json")) {
            return ResolvedModule.ModuleFormat.JSON;
        }
        // .js — depends on nearest package.json type field
        String dir = fs.dirname(path);
        PackageJson pkg = pkgReader.findNearest(dir);
        if (pkg != null && "module".equals(pkg.getType())) {
            return ResolvedModule.ModuleFormat.MODULE;
        }
        return ResolvedModule.ModuleFormat.COMMONJS;
    }

    // ========================================================================
    // CJS entry points
    // ========================================================================

    /**
     * require(X) from module at path Y
     *
     * <p>Resolves a CommonJS require() call.
     */
    public ResolvedModule cjsResolve(String specifier, String parentPath) {
        // 1. If specifier starts with #, it's a package import
        if (specifier.startsWith("#")) {
            return packageImportsResolve(specifier, parentPath, NodeConditions.CJS_CONDITIONS);
        }

        // 2. If relative or absolute path
        if (specifier.startsWith("./")
                || specifier.startsWith("../")
                || specifier.startsWith("/")) {
            String parentDir = fs.dirname(parentPath);
            String resolved = fs.getAbsolutePath(fs.resolve(parentDir, specifier));

            // LOAD_AS_FILE then LOAD_AS_DIRECTORY
            String found = loadAsFile(resolved);
            if (found != null) {
                return new ResolvedModule(found, cjsFileFormat(found));
            }
            found = loadAsDirectory(resolved);
            if (found != null) {
                return new ResolvedModule(found, cjsFileFormat(found));
            }
            throw new ModuleNotFoundException(
                    "Cannot find module '" + specifier + "' from '" + parentPath + "'");
        }

        // 3. Bare specifier — LOAD_NODE_MODULES
        String parentDir = fs.dirname(parentPath);
        ResolvedModule result = loadNodeModules(specifier, parentDir);
        if (result != null) {
            return result;
        }

        throw new ModuleNotFoundException(
                "Cannot find module '" + specifier + "' from '" + parentPath + "'");
    }

    /**
     * LOAD_AS_FILE(X)
     *
     * <p>Try to load X as a file: exact, .js, .json, .node (skipped).
     */
    public String loadAsFile(String path) {
        // 1. If X is a file, return X
        if (fs.isFile(path)) {
            return path;
        }
        // 2. Try X.js, X.json
        String[] exts = {".js", ".json"};
        for (String ext : exts) {
            String withExt = path + ext;
            if (fs.isFile(withExt)) {
                return withExt;
            }
        }
        return null;
    }

    /**
     * LOAD_AS_DIRECTORY(X)
     *
     * <p>Try to load X as a directory: package.json main, then index.js/json.
     */
    public String loadAsDirectory(String path) {
        if (!fs.isDirectory(path)) {
            return null;
        }
        // 1. Try package.json main
        PackageJson pkg = pkgReader.read(path);
        if (pkg != null && pkg.getMain() != null) {
            String mainPath = fs.getAbsolutePath(fs.resolve(path, pkg.getMain()));
            String found = loadAsFile(mainPath);
            if (found != null) {
                return found;
            }
            // Try main as directory (index)
            found = loadIndex(mainPath);
            if (found != null) {
                return found;
            }
        }
        // 2. Try index files
        return loadIndex(path);
    }

    private String loadIndex(String path) {
        if (!fs.isDirectory(path)) {
            return null;
        }
        String[] indexFiles = {"index.js", "index.json"};
        for (String indexFile : indexFiles) {
            String indexPath = fs.resolve(path, indexFile);
            if (fs.isFile(indexPath)) {
                return indexPath;
            }
        }
        return null;
    }

    /**
     * LOAD_NODE_MODULES(X, START)
     *
     * <p>Search node_modules directories for the given specifier.
     */
    public ResolvedModule loadNodeModules(String specifier, String startDir) {
        // Parse package name and subpath
        String packageName;
        String subpath;

        if (specifier.startsWith("@")) {
            int firstSlash = specifier.indexOf('/');
            if (firstSlash == -1) {
                return null;
            }
            int secondSlash = specifier.indexOf('/', firstSlash + 1);
            if (secondSlash == -1) {
                packageName = specifier;
                subpath = null;
            } else {
                packageName = specifier.substring(0, secondSlash);
                subpath = specifier.substring(secondSlash);
            }
        } else {
            int slashIdx = specifier.indexOf('/');
            if (slashIdx == -1) {
                packageName = specifier;
                subpath = null;
            } else {
                packageName = specifier.substring(0, slashIdx);
                subpath = specifier.substring(slashIdx);
            }
        }

        List<String> dirs = nodeModulesPaths(startDir);
        for (String dir : dirs) {
            String packageDir = fs.resolve(dir, packageName);
            if (!fs.isDirectory(packageDir)) {
                continue;
            }

            // LOAD_PACKAGE_EXPORTS
            PackageJson pkg = pkgReader.read(packageDir);
            if (pkg != null && pkg.hasExports()) {
                String pkgSubpath = subpath != null ? "." + subpath : ".";
                ResolvedModule exportsResult =
                        packageExportsResolve(
                                packageDir,
                                pkgSubpath,
                                pkg.getExports(),
                                NodeConditions.CJS_CONDITIONS);
                if (exportsResult != null) {
                    return exportsResult;
                }
            }

            // Fall back to file/directory resolution
            String target;
            if (subpath != null) {
                target = fs.resolve(packageDir, subpath.substring(1));
            } else {
                target = packageDir;
            }

            if (subpath != null) {
                String found = loadAsFile(target);
                if (found != null) {
                    return new ResolvedModule(found, cjsFileFormat(found));
                }
                found = loadAsDirectory(target);
                if (found != null) {
                    return new ResolvedModule(found, cjsFileFormat(found));
                }
            } else {
                String found = loadAsDirectory(target);
                if (found != null) {
                    return new ResolvedModule(found, cjsFileFormat(found));
                }
            }
        }

        return null;
    }

    /**
     * NODE_MODULES_PATHS(START)
     *
     * <p>Compute the list of node_modules directories to search from the given start directory.
     */
    public List<String> nodeModulesPaths(String startDir) {
        List<String> dirs = new ArrayList<>();
        String dir = fs.getAbsolutePath(startDir);
        while (true) {
            String basename = baseName(dir);
            if (!"node_modules".equals(basename)) {
                dirs.add(fs.resolve(dir, "node_modules"));
            }
            String parent = fs.dirname(dir);
            if (parent.equals(dir)) {
                break;
            }
            dir = parent;
        }
        return dirs;
    }

    // ========================================================================
    // Shared: package exports/imports resolution
    // ========================================================================

    /**
     * PACKAGE_EXPORTS_RESOLVE(packageURL, subpath, exports, conditions)
     *
     * <p>Resolves a subpath through a package's exports field.
     */
    @SuppressWarnings("unchecked")
    public ResolvedModule packageExportsResolve(
            String packagePath, String subpath, Object exports, Set<String> conditions) {

        // If exports is a string or array, it's the "." mapping
        if (exports instanceof String || exports instanceof List) {
            if (".".equals(subpath)) {
                return packageTargetResolve(packagePath, exports, null, false, conditions);
            }
            return null;
        }

        if (!(exports instanceof Map)) {
            return null;
        }

        Map<String, Object> exportsMap = (Map<String, Object>) exports;

        // Check if all keys start with "." (subpath exports) or none do (conditional exports)
        boolean allDot = true;
        boolean noneDot = true;
        for (String key : exportsMap.keySet()) {
            if (key.startsWith(".")) {
                noneDot = false;
            } else {
                allDot = false;
            }
        }

        // If no keys start with ".", this is a conditional exports for "."
        if (noneDot) {
            if (".".equals(subpath)) {
                return packageTargetResolve(packagePath, exports, null, false, conditions);
            }
            return null;
        }

        // Mixed keys are invalid, but we handle it gracefully
        if (!allDot) {
            return null;
        }

        // Subpath exports
        // 1. Exact match
        if (exportsMap.containsKey(subpath)) {
            Object target = exportsMap.get(subpath);
            return packageTargetResolve(packagePath, target, null, false, conditions);
        }

        // 2. Pattern match (keys with *)
        String bestMatch = null;
        String bestMatchSubpath = null;
        for (String key : exportsMap.keySet()) {
            int starIdx = key.indexOf('*');
            if (starIdx == -1) {
                continue;
            }
            String prefix = key.substring(0, starIdx);
            String suffix = key.substring(starIdx + 1);

            if (subpath.startsWith(prefix)
                    && (suffix.isEmpty() || subpath.endsWith(suffix))
                    && subpath.length() >= prefix.length() + suffix.length()) {
                if (bestMatch == null || patternKeyCompare(key, bestMatch) < 0) {
                    bestMatch = key;
                    bestMatchSubpath =
                            subpath.substring(prefix.length(), subpath.length() - suffix.length());
                }
            }
        }

        if (bestMatch != null) {
            Object target = exportsMap.get(bestMatch);
            return packageTargetResolve(packagePath, target, bestMatchSubpath, false, conditions);
        }

        return null;
    }

    /**
     * PACKAGE_IMPORTS_RESOLVE(specifier, parentURL, conditions)
     *
     * <p>Resolves a # import specifier through the nearest package.json imports field.
     */
    @SuppressWarnings("unchecked")
    public ResolvedModule packageImportsResolve(
            String specifier, String parentPath, Set<String> conditions) {
        // Validate: must start with #, must not be just "#" or start with "#/"
        if ("#".equals(specifier) || specifier.startsWith("#/")) {
            throw new ModuleNotFoundException("Invalid import specifier: " + specifier);
        }

        String parentDir = fs.dirname(parentPath);
        PackageJson pkg = pkgReader.findNearest(parentDir);
        if (pkg == null || !pkg.hasImports()) {
            throw new ModuleNotFoundException(
                    "No imports defined in package.json for '" + specifier + "'");
        }

        Object imports = pkg.getImports();
        if (!(imports instanceof Map)) {
            throw new ModuleNotFoundException(
                    "Invalid imports field in package.json for '" + specifier + "'");
        }

        Map<String, Object> importsMap = (Map<String, Object>) imports;

        // Exact match
        if (importsMap.containsKey(specifier)) {
            Object target = importsMap.get(specifier);
            ResolvedModule result =
                    packageTargetResolve(pkg.getDirectory(), target, null, true, conditions);
            if (result != null) {
                return result;
            }
        }

        // Pattern match
        String bestMatch = null;
        String bestMatchSubpath = null;
        for (String key : importsMap.keySet()) {
            int starIdx = key.indexOf('*');
            if (starIdx == -1) {
                continue;
            }
            String prefix = key.substring(0, starIdx);
            String suffix = key.substring(starIdx + 1);

            if (specifier.startsWith(prefix)
                    && (suffix.isEmpty() || specifier.endsWith(suffix))
                    && specifier.length() >= prefix.length() + suffix.length()) {
                if (bestMatch == null || patternKeyCompare(key, bestMatch) < 0) {
                    bestMatch = key;
                    bestMatchSubpath =
                            specifier.substring(
                                    prefix.length(), specifier.length() - suffix.length());
                }
            }
        }

        if (bestMatch != null) {
            Object target = importsMap.get(bestMatch);
            ResolvedModule result =
                    packageTargetResolve(
                            pkg.getDirectory(), target, bestMatchSubpath, true, conditions);
            if (result != null) {
                return result;
            }
        }

        throw new ModuleNotFoundException(
                "Package import specifier '"
                        + specifier
                        + "' is not defined in package.json imports");
    }

    /**
     * PACKAGE_TARGET_RESOLVE(packageURL, target, patternMatch, isImports, conditions)
     *
     * <p>Resolves a single exports/imports target value.
     */
    @SuppressWarnings("unchecked")
    public ResolvedModule packageTargetResolve(
            String packagePath,
            Object target,
            String patternMatch,
            boolean isImports,
            Set<String> conditions) {

        // 1. If target is a string
        if (target instanceof String) {
            String targetStr = (String) target;

            // Validate: must start with "./" for non-imports
            if (!isImports && !targetStr.startsWith("./")) {
                return null;
            }

            // Replace * with patternMatch if present
            if (patternMatch != null) {
                targetStr = targetStr.replace("*", patternMatch);
            }

            String resolved = fs.getAbsolutePath(fs.resolve(packagePath, targetStr));
            if (fs.isFile(resolved)) {
                return new ResolvedModule(resolved, esmFileFormat(resolved));
            }
            return null;
        }

        // 2. If target is an object (conditional)
        if (target instanceof Map) {
            Map<String, Object> targetMap = (Map<String, Object>) target;
            for (Map.Entry<String, Object> entry : targetMap.entrySet()) {
                String key = entry.getKey();
                if ("default".equals(key) || conditions.contains(key)) {
                    ResolvedModule result =
                            packageTargetResolve(
                                    packagePath,
                                    entry.getValue(),
                                    patternMatch,
                                    isImports,
                                    conditions);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        // 3. If target is an array (fallback)
        if (target instanceof List) {
            List<Object> targetList = (List<Object>) target;
            for (Object item : targetList) {
                ResolvedModule result =
                        packageTargetResolve(
                                packagePath, item, patternMatch, isImports, conditions);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        // 4. If target is null, this entry is explicitly not exported
        return null;
    }

    /**
     * PATTERN_KEY_COMPARE(keyA, keyB)
     *
     * <p>Compare two pattern keys for specificity. More specific patterns sort earlier. Returns
     * negative if keyA is more specific, positive if keyB is more specific.
     */
    public int patternKeyCompare(String keyA, String keyB) {
        int starA = keyA.indexOf('*');
        int starB = keyB.indexOf('*');

        // Longer prefix is more specific
        int prefixA = starA == -1 ? keyA.length() : starA;
        int prefixB = starB == -1 ? keyB.length() : starB;
        if (prefixA != prefixB) {
            return prefixB - prefixA;
        }

        // If same prefix length, longer suffix is more specific
        if (starA != -1 && starB != -1) {
            int suffixA = keyA.length() - starA - 1;
            int suffixB = keyB.length() - starB - 1;
            return suffixB - suffixA;
        }

        // Keys without * sort before keys with *
        if (starA == -1) return -1;
        if (starB == -1) return 1;

        return 0;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ResolvedModule.ModuleFormat cjsFileFormat(String path) {
        if (path.endsWith(".json")) {
            return ResolvedModule.ModuleFormat.JSON;
        }
        if (path.endsWith(".mjs")) {
            return ResolvedModule.ModuleFormat.MODULE;
        }
        return ResolvedModule.ModuleFormat.COMMONJS;
    }

    private String baseName(String path) {
        String sep = fs.separator();
        int idx = path.lastIndexOf(sep.charAt(0));
        if (idx == -1) {
            return path;
        }
        return path.substring(idx + 1);
    }

    /** Exception thrown when a module cannot be found. */
    public static class ModuleNotFoundException extends RuntimeException {
        public ModuleNotFoundException(String message) {
            super(message);
        }
    }
}
