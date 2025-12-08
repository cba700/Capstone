package com.capstone.global.resource.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
public class ResourceService {

    @Autowired
    private ResourceLoader resourceLoader;

    private static final String CLASSPATH_DIR = "static/images/jobs";
    private static final String URL_PATH = "/images/jobs";

    public List<String> getJobImagePaths() {
        List<String> imagePaths = new ArrayList<>();
        try {
            Resource resource = resourceLoader.getResource("classpath:" + CLASSPATH_DIR);
            File dir = resource.getFile();

            if (dir.exists() && dir.isDirectory()) {
                try (Stream<Path> paths = Files.list(dir.toPath())) {
                    paths.filter(Files::isRegularFile)
                         .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            imagePaths.add(URL_PATH + "/" + fileName);
                         });
                }
            } else {
                log.warn("Job images directory not found or is not a directory: {}", dir.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Error reading job images directory: {}", e.getMessage());
        }
        return imagePaths;
    }
}
