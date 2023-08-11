/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import gr.iccs.imu.ems.control.properties.InfoServiceProperties;
import gr.iccs.imu.ems.util.EmsConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import javax.validation.constraints.Null;
import java.io.*;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@ConditionalOnProperty(value = "enabled", prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "info.files", havingValue = "true", matchIfMissing = true)
public class FilesController {

    private final InfoServiceProperties properties;
    private final List<Path> roots;

    public FilesController(@NonNull InfoServiceProperties properties) {
        this.properties = properties;
        List<Path> tmp = properties.getFiles().getRoots();
        this.roots = (tmp!=null) ? tmp : Collections.emptyList();
        log.debug("FilesController: File roots: {}", roots);
    }

    @GetMapping("/files")
    public List<FILE> listRoots(HttpServletRequest request) {
        log.debug("listRoots(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        return toFileList(roots, null);
    }

    @GetMapping("/files/tree/roots")
    public List<List<FILE>> listTreeRoots(HttpServletRequest request) throws IOException {
        log.debug("listTreeRoots(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        LinkedList<List<FILE>> trees = new LinkedList<>();
        for (Path root : roots) {
            trees.add( toFileList(Files.walk(root).collect(Collectors.toList()), root) );
        }
        return trees;
    }

    @GetMapping("/files/tree/{rootId}")
    public List<FILE> listTreeFiles(HttpServletRequest request, @PathVariable int rootId) throws IOException {
        log.debug("listTreeFiles(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        log.debug("listTreeFiles(): --- Root-Id: {}", rootId);
        Path root = roots.get(rootId);
        return toFileList(Files.walk(root).collect(Collectors.toList()), root);
    }

    @GetMapping({"/files/dir/{rootId}", "/files/dir/{rootId}/**"})
    public List<FILE> listDirFiles(HttpServletRequest request, @PathVariable int rootId, WebRequest webRequest) throws IOException {
        log.debug("listDirFiles(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        String mvcPrefix = "/files/dir/" + rootId;
        String pathStr = getPathFromRequest(request, webRequest, mvcPrefix);

        Path path = Paths.get(roots.get(rootId).toString(), pathStr);
        log.debug("listDirFiles(): --- Effective Path: {}", path);
        if (path.toFile().exists()) {
            if (path.toFile().isDirectory()) {
                return toFileList(Files.list(path).collect(Collectors.toList()), path);
            } else {
                return null;
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: "+rootId+": "+pathStr);
        }
    }

    private String getPathFromRequest(HttpServletRequest request, WebRequest webRequest, String mvcPrefix) {
        log.debug("getPathFromRequest(): --- mvc-prefix: {}", mvcPrefix);
        String mvcPath = (String) webRequest.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        log.debug("getPathFromRequest(): --- mvc-path: {}", mvcPath);
        String pathStr = mvcPath!=null ? mvcPath.substring(mvcPrefix.length()) : "";
        log.debug("getPathFromRequest(): --- Prefix: {}, Path: {}", mvcPrefix, pathStr);
        return pathStr;
    }

    private InputStreamResource toStringInputStream(String s) {
        //return new InputStreamResource(new org.apache.tools.ant.filters.StringInputStream(s));
        return new InputStreamResource(new ByteArrayInputStream(s.getBytes()));
    }

    @GetMapping("/files/get/{rootId}/**")
    public ResponseEntity<InputStreamResource> getFile(HttpServletRequest request, @PathVariable int rootId, WebRequest webRequest) throws IOException {
        log.debug("getFile(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        String mvcPrefix = "/files/get/" + rootId + "/";
        String pathStr = getPathFromRequest(request, webRequest, mvcPrefix);

        File file = Paths.get(roots.get(rootId).toString(), pathStr).toFile();
        log.debug("getFile(): --- Effective Path: {}", file);
        if (!file.exists()) {
            //return ResponseEntity.badRequest().body( toStringInputStream("File not exists") );
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: "+rootId+": "+pathStr);
        }
        if (isFileBlocked(file.toPath())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Blocked extension. Cannot download file: "+rootId+": "+pathStr);
        }
        if (!file.canRead()) {
            return ResponseEntity.badRequest().body( toStringInputStream("File cannot be read") );
        }
        if (file.isFile()) {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+file.getName());
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            String mimeType = URLConnection.guessContentTypeFromName(file.getName());
            if (StringUtils.isBlank(mimeType))
                mimeType = Files.probeContentType(file.toPath());
            log.debug("getFile(): --- File content type: {}", mimeType);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            try {
                if (StringUtils.isNotBlank(mimeType))
                    mediaType = MediaType.parseMediaType(mimeType);
            } catch (Exception e) {
                log.warn("getFile(): --- Invalid File content type: {}, file: {}\n", mimeType, file.getName(), e);
            }
            log.debug("getFile(): --- Will use content type: {}", mediaType);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(mediaType)
                    .body(new InputStreamResource(new FileInputStream(file)));
        }
        return ResponseEntity.badRequest().body( toStringInputStream("Not a regular file") );
    }

    @GetMapping("/files/getpath/**")
    public ResponseEntity<InputStreamResource> getFileFromPath(HttpServletRequest request, WebRequest webRequest) throws IOException {
        log.debug("getFileFromPath(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        String mvcPrefix = "/files/getpath/";
        String pathStr = getPathFromRequest(request, webRequest, mvcPrefix);
        log.debug("getFileFromPath(): --- pathStr: {}", pathStr);
        if (!pathStr.startsWith(File.separator)) pathStr = File.separator+pathStr;

        String filePath = null;
        for (Path r : roots) {
            log.trace("getFileFromPath(): --- Checking pathStr against root: pathStr={}, root={}", pathStr, r);
            if (pathStr.startsWith(r.toFile().getAbsolutePath())) {
                log.debug("getFileFromPath(): --- pathStr is under root: pathStr={}, root={}", pathStr, r);
                filePath = pathStr;
                if (!filePath.startsWith(File.separator)) filePath = File.separator+filePath;
                break;
            }
        }
        log.debug("getFileFromPath(): --- filePath: {}", filePath);
        if (filePath==null) {
            log.warn("getFileFromPath(): --- FORBIDDEN: Specified path is not under any allowed root: {}", filePath);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Specified path is not under any allowed root: "+filePath);
        }

        File file = Paths.get(pathStr).toFile();
        log.debug("getFileFromPath(): --- Effective Path: {}", file);
        if (!file.exists()) {
            //return ResponseEntity.badRequest().body( toStringInputStream("File not exists") );
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: "+pathStr);
        }
        if (isFileBlocked(file.toPath())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Blocked extension. Cannot download file: "+pathStr);
        }
        if (!file.canRead()) {
            return ResponseEntity.badRequest().body( toStringInputStream("File cannot be read") );
        }
        if (file.isFile()) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            String mimeType = URLConnection.guessContentTypeFromName(file.getName());
            if (StringUtils.isBlank(mimeType))
                mimeType = Files.probeContentType(file.toPath());
            log.debug("getFileFromPath(): --- File content type: {}", mimeType);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            try {
                if (StringUtils.isNotBlank(mimeType))
                    mediaType = MediaType.parseMediaType(mimeType);
            } catch (Exception e) {
                log.warn("getFileFromPath(): --- Invalid File content type: {}, file: {}\n", mimeType, file.getName(), e);
            }
            log.debug("getFileFromPath(): --- Will use content type: {}", mediaType);
            if (mediaType==MediaType.APPLICATION_OCTET_STREAM)
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+file.getName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(mediaType)
                    .body(new InputStreamResource(Files.newInputStream(file.toPath())));
        }
        return ResponseEntity.badRequest().body( toStringInputStream("Not a regular file") );
    }

    private boolean isFileBlocked(Path path) {
        String fileName = path.toFile().getName();
        return properties.getFiles().getExtensionsBlocked().stream()
                .anyMatch(ext->StringUtils.endsWithIgnoreCase(fileName, ext));
    }

    private List<FILE> toFileList(@NonNull List<Path> paths, @Null Path root) {
        String prefix = (root!=null) ? root.toString() : "";
        boolean listBlocked = properties.getFiles().isListBlocked();
        boolean listHidden = properties.getFiles().isListHidden();
        List<FILE> list = new LinkedList<>();
        for (Path p : paths) {
            boolean blocked = isFileBlocked(p);
            if (!listBlocked && blocked) continue;
            if (!listHidden && p.toFile().isHidden()) continue;
            String pathStr = StringUtils.removeStart(p.toString(), prefix);
            File f = p.toFile();
            if (StringUtils.isNotBlank(pathStr))
                list.add(FILE.builder()
                        .path(pathStr)
                        .size(f.length())
                        .lastModified(f.lastModified())
                        .hidden(f.isHidden())
                        .dir(f.isDirectory())
                        .root(root==null)
                        .read(f.canRead()).write(f.canWrite()).exec(f.canExecute())
                        .noLink(blocked)
                        .build());
        }
        return list;
    }

    @Data
    @Builder
    public static class FILE {
        private final String path;
        private final long size;
        private final long lastModified;
        private final boolean hidden;
        private final boolean dir;
        private final boolean root;
        private final boolean read;
        private final boolean write;
        private final boolean exec;
        private final boolean noLink;
    }
}
